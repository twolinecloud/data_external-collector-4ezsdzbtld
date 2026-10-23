package egovframework.external.publicdata.transfer;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.model.SendResult;
import egovframework.external.staging.RawStagingStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 정제가 끝난 법령/행정규칙을 Xenon(GenOS)에 ZIP으로 보낸다 - 법령 체인(수집→정제→<b>전송</b>)의 마지막 단계.
 *
 * <p>흐름: 수집 실행(run)의 CLEANSED 행 → 문서로 해석 → 지난번에 보낸 내용과 같은 문서는 제외({@link LawTransferLedger}) →
 * 연결 확인(ping) → 문서마다 별표·첨부 다운로드 → {@link LawZipWriter}로 ZIP(상한 넘으면 분할) → 완성되는 ZIP마다
 * 전송하고 응답 체크섬 대조 → 확인된 문서만 "보냄"으로 기록. 한 ZIP의 전송이 실패해도 다음 ZIP은 계속 시도하고,
 * 못 보낸 문서는 기록에 남지 않아 다음 수집 때 자동으로 다시 시도된다.</p>
 *
 * <p>{@code public-data.law-transfer.enabled=false}(기본값)면 아무 것도 하지 않는다 - 켜는 건 API 키와 Xenon 쪽 구조
 * 합의가 끝난 뒤다. 끝나면 이 run의 모든 행을 LOAD_SKIPPED로 종결한다(법령은 적재기가 없어 원래도 그렇게 끝난다).</p>
 *
 * <p>첨부 다운로드가 일시적으로 실패한(5xx/타임아웃) 문서는 첨부를 빼고 보내지 않고 <b>통째로 이번 전송에서 제외</b>한다 -
 * 첨부 없는 문서를 "보냄"으로 기록하면 다음에 고쳐질 기회가 없다. 404/410처럼 영구 실패는 그 파일만 건너뛰고 manifest에 사유를 남긴다.</p>
 */
@Service
public class LawTransferService {

    private static final Logger logger = LogManager.getLogger(LawTransferService.class);
    private static final int MAX_ROWS = 5_000;

    private final RawStagingStore store;
    private final LawFileFetcher fetcher;
    private final LawIngestPort ingest;
    private final LawTransferLedger ledger;
    private final boolean enabled;
    private final boolean sendAll;
    private final boolean includeImages;
    private final long maxZipBytes;
    private final int downloadConcurrency;
    private final Set<String> allowedExtensions;

    public LawTransferService(
        RawStagingStore store,
        LawFileFetcher fetcher,
        LawIngestPort ingest,
        LawTransferLedger ledger,
        @Value("${public-data.law-transfer.enabled:false}") boolean enabled,
        @Value("${public-data.law-transfer.send-all:false}") boolean sendAll,
        @Value("${public-data.law-transfer.include-images:false}") boolean includeImages,
        @Value("${public-data.law-transfer.max-zip-bytes:52428800}") long maxZipBytes,
        @Value("${public-data.law-transfer.download.concurrency:4}") int downloadConcurrency,
        @Value("${public-data.law-transfer.allowed-extensions:hwp,hwpx,pdf,doc,docx,png,jpg,jpeg,gif}") String allowedExtensions
    ) {
        this.store = store;
        this.fetcher = fetcher;
        this.ingest = ingest;
        this.ledger = ledger;
        this.enabled = enabled;
        this.sendAll = sendAll;
        this.includeImages = includeImages;
        this.maxZipBytes = maxZipBytes;
        this.downloadConcurrency = Math.max(1, downloadConcurrency);
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
            .map(s -> s.strip().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 그 수집 실행의 정제된 행을 전송한다. @return 보내야 했던(변경된) 문서 기준 대상/성공/실패 */
    public SendResult transfer(String collectRunId) {
        if (!enabled) {
            return new SendResult(0, 0, 0);
        }
        List<RawStagingDto> rows = store.findByStatusAndRunId("CLEANSED", MAX_ROWS, collectRunId);
        if (rows.isEmpty()) {
            return new SendResult(0, 0, 0);
        }
        Tally tally = new Tally();
        String outcome = "전송 완료";
        try {
            outcome = run(rows, tally);
        } catch (RuntimeException e) {
            logger.error("[SEND] 법령 전송 중 예외 runId={}", collectRunId, e);
            outcome = "전송 실패: " + e.getMessage();
        } finally {
            for (RawStagingDto row : rows) {
                store.markLoadSkipped(row.getId(), outcome);
            }
        }
        logger.info("[SEND] 법령 전송 끝 runId={} 대상={} 성공={} 실패={} 변경없음={} (기록 {}건)",
            collectRunId, tally.total, tally.success, tally.fail, tally.unchanged, ledger.size());
        return new SendResult(tally.total, tally.success, tally.fail);
    }

    private String run(List<RawStagingDto> rows, Tally tally) {
        List<LawDocument> changed = new ArrayList<>();
        for (RawStagingDto row : rows) {
            List<LawDocument> docs;
            try {
                docs = LawDocumentParser.parse(row.getCleansedPayload());
            } catch (RuntimeException e) {
                logger.warn("[SEND] 정제본을 문서로 해석하지 못해 건너뜀 collectorKey={}: {}", row.getCollectorKey(), e.getMessage());
                tally.total++;
                tally.fail++;
                continue;
            }
            for (LawDocument doc : docs) {
                if (!sendAll && ledger.isUnchanged(doc.key(), fingerprint(doc))) {
                    tally.unchanged++;
                } else {
                    changed.add(doc);
                }
            }
        }
        tally.total += changed.size();
        if (changed.isEmpty()) {
            return "변경 없음";
        }

        try {
            ingest.ping();
        } catch (IOException e) {
            logger.error("[SEND] Xenon 연결 확인 실패 - 내려받기 전에 전송 포기 (대상 {}건): {}", changed.size(), e.getMessage());
            tally.fail += changed.size();
            return "Xenon 연결 실패: " + e.getMessage();
        }

        Path tmpDir = null;
        ExecutorService pool = Executors.newFixedThreadPool(downloadConcurrency);
        try {
            tmpDir = Files.createTempDirectory("law-transfer-");
            for (String top : List.of(LawArchiveLayout.LAW_TOP, LawArchiveLayout.ADMRUL_TOP)) {
                List<LawDocument> group = changed.stream()
                    .filter(d -> top.equals(LawArchiveLayout.topFolder(d))).toList();
                if (!group.isEmpty()) {
                    sendGroup(top, group, tmpDir, pool, tally);
                }
            }
            return tally.fail == 0 ? "전송 완료" : "일부 전송 실패";
        } catch (IOException e) {
            throw new IllegalStateException("임시 파일 처리 실패: " + e.getMessage(), e);
        } finally {
            pool.shutdownNow();
            deleteQuietly(tmpDir);
        }
    }

    private void sendGroup(String top, List<LawDocument> docs, Path tmpDir, ExecutorService pool, Tally tally)
        throws IOException {
        try (LawZipWriter writer = new LawZipWriter(tmpDir, top, maxZipBytes, Instant.now())) {
            for (LawDocument doc : docs) {
                PreparedDocument prepared;
                try {
                    prepared = prepare(doc, pool);
                } catch (TransientDownloadException e) {
                    logger.warn("[SEND] 첨부 다운로드 실패로 이번 전송에서 제외 {} ({}): {}", doc.name(), doc.key(), e.getMessage());
                    tally.fail++;
                    continue;
                }
                sendCompleted(writer.add(prepared), tally);
            }
            sendCompleted(writer.finish(), tally);
        }
    }

    private void sendCompleted(ZipPart part, Tally tally) {
        if (part == null) {
            return;
        }
        try {
            ingest.upload(part);
            for (LawDocument doc : part.documents()) {
                ledger.record(doc.key(), fingerprint(doc));
            }
            ledger.save();
            tally.success += part.documents().size();
        } catch (IOException e) {
            logger.error("[SEND] ZIP 전송 실패 part={} 문서 {}건 - 기록하지 않으므로 다음 수집에서 다시 시도: {}",
                part.index(), part.documents().size(), e.getMessage());
            tally.fail += part.documents().size();
        } finally {
            deleteQuietly(part.file());
        }
    }

    // ── 문서 하나의 별표·첨부 내려받기 ───────────────────────────

    private PreparedDocument prepare(LawDocument doc, ExecutorService pool) throws TransientDownloadException {
        List<Download> downloads = plan(doc);
        List<Future<Fetched>> futures = new ArrayList<>(downloads.size());
        for (Download d : downloads) {
            futures.add(pool.submit((Callable<Fetched>) () -> {
                try {
                    return new Fetched(d, fetcher.fetch(d.url()), null);
                } catch (LawFileFetcher.FetchException e) {
                    return new Fetched(d, null, e);
                }
            }));
        }

        List<PreparedDocument.PackedFile> files = new ArrayList<>();
        List<PreparedDocument.SkippedFile> skipped = new ArrayList<>();
        for (Future<Fetched> future : futures) {
            Fetched f;
            try {
                f = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransientDownloadException("다운로드 중단");
            } catch (ExecutionException e) {
                throw new TransientDownloadException(String.valueOf(e.getCause()));
            }
            if (f.error() != null) {
                if (!f.error().isPermanent()) {
                    futures.forEach(x -> x.cancel(true));
                    throw new TransientDownloadException(f.error().getMessage());
                }
                skipped.add(new PreparedDocument.SkippedFile(f.download().title(), f.download().kind(), f.error().getMessage()));
                continue;
            }
            String ext = FileTypes.extensionOf(f.file(), f.download().hintName(), f.download().kind());
            if (!allowedExtensions.contains(ext)) {
                skipped.add(new PreparedDocument.SkippedFile(f.download().title(), f.download().kind(),
                    "허용되지 않는 확장자(" + ext + ")"));
                continue;
            }
            files.add(new PreparedDocument.PackedFile(f.download().folder(), f.download().baseName(), ext,
                f.file().content(), f.download().title(), f.file().fileName(), f.download().kind()));
        }
        return new PreparedDocument(doc, files, skipped);
    }

    /** 내려받을 파일 목록. 이미지(GIF)는 PDF와 같은 내용이라 기본으로 뺀다({@code include-images}). */
    private List<Download> plan(LawDocument doc) {
        List<Download> plan = new ArrayList<>();
        for (LawDocument.Annex annex : doc.annexes()) {
            String base = LawArchiveLayout.annexBaseName(annex);
            if (annex.formUrl() != null) {
                plan.add(new Download(annex.formUrl(), "별표", base, annex.title(), "서식파일", null));
            }
            if (annex.pdfUrl() != null) {
                plan.add(new Download(annex.pdfUrl(), "별표", base, annex.title(), "PDF", "pdf"));
            }
            if (includeImages) {
                for (String url : annex.imageUrls()) {
                    plan.add(new Download(url, "별표", base, annex.title(), "이미지", "gif"));
                }
            }
        }
        int index = 1;
        for (LawDocument.Attachment att : doc.attachments()) {
            plan.add(new Download(att.url(), "첨부", LawArchiveLayout.attachmentBaseName(index++), att.name(), "첨부", att.name()));
        }
        return plan;
    }

    private record Download(String url, String folder, String baseName, String title, String kind, String hintName) {
    }

    private record Fetched(Download download, LawFileFetcher.FetchedFile file, LawFileFetcher.FetchException error) {
    }

    private static class TransientDownloadException extends Exception {
        TransientDownloadException(String message) {
            super(message);
        }
    }

    // ── 보조 ────────────────────────────────────────────

    /** 본문 JSON의 sha256 - 법령이 개정되면 바뀐다. */
    static String fingerprint(LawDocument doc) {
        return LawZipWriter.sha256(doc.body().getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var children = Files.list(path)) {
                    children.forEach(LawTransferService::deleteQuietly);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.warn("[SEND] 임시 파일 삭제 실패: {}", path);
        }
    }

    private static final class Tally {
        int total;
        int success;
        int fail;
        int unchanged;
    }
}
