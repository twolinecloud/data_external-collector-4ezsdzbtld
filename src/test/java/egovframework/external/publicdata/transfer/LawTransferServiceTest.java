package egovframework.external.publicdata.transfer;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.model.SendResult;
import egovframework.external.staging.InMemoryRawStagingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class LawTransferServiceTest {

    private static final String RUN = "run-1";

    private final InMemoryRawStagingStore store = new InMemoryRawStagingStore();
    private final FakeFetcher fetcher = new FakeFetcher();
    private final FakeIngest ingest = new FakeIngest();
    private LawTransferLedger ledger;

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        ledger = new LawTransferLedger("");
    }

    private LawTransferService service(boolean enabled, boolean sendAll, boolean images, long maxZip) {
        return new LawTransferService(store, fetcher, ingest, ledger, enabled, sendAll, images, maxZip, 2,
            "hwp,hwpx,pdf,doc,docx,png,jpg,jpeg,gif");
    }

    private LawTransferService service() {
        return service(true, false, false, 50_000_000);
    }

    /** 정제까지 끝난 행 하나를 넣는다. */
    private RawStagingDto cleansed(String payload) {
        RawStagingDto dto = RawStagingDto.builder().operationKey("moleg-criminal-law").collectorKey("k" + System.nanoTime())
            .collectRunId(RUN).rawPayload("[]").build();
        store.insert(dto);
        store.markCleansed(dto.getId(), payload, "batch");
        return dto;
    }

    private static String law(String id, String name, String kind, String annexes) {
        return "[{\"법령\":{\"기본정보\":{\"법령ID\":\"" + id + "\",\"법령명_한글\":\"" + name + "\",\"법종구분\":{\"content\":\""
            + kind + "\"},\"공포일자\":\"20260101\",\"시행일자\":\"20260201\"}" + (annexes.isEmpty() ? "" : ",\"별표\":{\"별표단위\":" + annexes + "}") + "}}]";
    }

    private static final String ANNEX = "[{\"별표구분\":\"별표\",\"별표번호\":\"0001\",\"별표가지번호\":\"00\",\"별표제목\":\"제목\","
        + "\"별표서식파일링크\":\"/LSW/flDownload.do?flSeq=1\",\"별표서식PDF파일링크\":\"/LSW/flDownload.do?flSeq=2\","
        + "\"별표서식이미지파일링크\":\"/LSW/flDownload.do?flSeq=3\"}]";

    private static String rule(String id, String name, String attachments) {
        return "[{\"AdmRulService\":{\"행정규칙기본정보\":{\"행정규칙ID\":\"" + id + "\",\"행정규칙명\":\"" + name
            + "\",\"행정규칙종류\":\"예규\"}" + (attachments.isEmpty() ? "" : ",\"첨부파일\":" + attachments) + "}}]";
    }

    private String status(RawStagingDto dto) {
        return store.findByStatusAndRunId("LOAD_SKIPPED", 100, RUN).stream()
            .anyMatch(r -> r.getId().equals(dto.getId())) ? "LOAD_SKIPPED" : "other";
    }

    // ── 기본 흐름 ─────────────────────────────────────────

    @Test
    void 꺼져_있으면_아무것도_하지_않고_행도_건드리지_않는다() {
        cleansed(law("1", "형법", "법률", ""));

        SendResult result = service(false, false, false, 1_000_000).transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(0, 0, 0));
        assertThat(ingest.uploads).isEmpty();
        assertThat(store.findByStatusAndRunId("CLEANSED", 10, RUN)).hasSize(1);
    }

    @Test
    void 법령을_받아_별표를_내려받아_ZIP으로_보내고_보냄으로_기록하고_행을_종결한다() {
        RawStagingDto row = cleansed(law("1", "형법", "법률", ANNEX));
        fetcher.add("https://www.law.go.kr/LSW/flDownload.do?flSeq=1", "hwp-bytes", "별표1.hwp");
        fetcher.add("https://www.law.go.kr/LSW/flDownload.do?flSeq=2", "%PDF-pdf", null);

        SendResult result = service().transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(1, 1, 0));
        assertThat(ingest.uploads).hasSize(1);
        assertThat(ingest.uploads.get(0).entries).containsExactlyInAnyOrder(
            "law/법률/형법/형법.json", "law/법률/형법/별표/별표1.hwp", "law/법률/형법/별표/별표1.pdf", "manifest.json");
        assertThat(ledger.isUnchanged("LAW:1", LawTransferService.fingerprint(LawDocumentParser.parse(law("1", "형법", "법률", ANNEX)).get(0)))).isTrue();
        assertThat(status(row)).isEqualTo("LOAD_SKIPPED");
        assertThat(ingest.pingCount).isEqualTo(1);
    }

    @Test
    void 이미지는_기본으로_내려받지_않고_include_images를_켜면_받는다() {
        cleansed(law("1", "형법", "법률", ANNEX));
        fetcher.add("https://www.law.go.kr/LSW/flDownload.do?flSeq=1", "h", "a.hwp");
        fetcher.add("https://www.law.go.kr/LSW/flDownload.do?flSeq=2", "%PDF", null);
        fetcher.add("https://www.law.go.kr/LSW/flDownload.do?flSeq=3", "GIF89a", "a.gif");

        service().transfer(RUN);

        assertThat(fetcher.requested).doesNotContain("https://www.law.go.kr/LSW/flDownload.do?flSeq=3");

        ledger = new LawTransferLedger("");
        fetcher.requested.clear();
        cleansed(law("1", "형법", "법률", ANNEX));
        service(true, false, true, 50_000_000).transfer(RUN);

        assertThat(fetcher.requested).contains("https://www.law.go.kr/LSW/flDownload.do?flSeq=3");
        assertThat(ingest.uploads.get(1).entries).contains("law/법률/형법/별표/별표1.gif");
    }

    @Test
    void 지난번과_같은_본문은_다시_보내지_않고_개정되면_보낸다() {
        cleansed(law("1", "형법", "법률", ""));
        service().transfer(RUN);
        assertThat(ingest.uploads).hasSize(1);

        SendResult again = service().transfer(RUN); // 첫 전송에서 행이 종결돼 더 처리할 게 없다
        assertThat(again).isEqualTo(new SendResult(0, 0, 0));

        cleansed(law("1", "형법", "법률", ""));
        assertThat(service().transfer(RUN)).isEqualTo(new SendResult(0, 0, 0)); // 변경 없음
        assertThat(ingest.uploads).hasSize(1);

        cleansed(law("1", "형법", "법률", "").replace("20260201", "20270101")); // 개정
        assertThat(service().transfer(RUN)).isEqualTo(new SendResult(1, 1, 0));
        assertThat(ingest.uploads).hasSize(2);
    }

    @Test
    void send_all이면_변경_없어도_보낸다() {
        cleansed(law("1", "형법", "법률", ""));
        service().transfer(RUN);
        cleansed(law("1", "형법", "법률", ""));

        assertThat(service(true, true, false, 50_000_000).transfer(RUN)).isEqualTo(new SendResult(1, 1, 0));
    }

    @Test
    void 법령과_행정규칙은_따로_ZIP으로_보낸다() {
        cleansed(law("1", "형법", "법률", ""));
        cleansed(rule("9", "가석방 업무지침", "{\"첨부파일링크\":[\"http://law.go.kr/flDownload.do?flSeq=20\"],\"첨부파일명\":[\"지침.hwp\"]}"));
        fetcher.add("https://law.go.kr/flDownload.do?flSeq=20", "hwp", "지침.hwp");

        SendResult result = service().transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(2, 2, 0));
        assertThat(ingest.uploads).hasSize(2);
        assertThat(ingest.uploads.get(0).entries).contains("law/법률/형법/형법.json").noneMatch(e -> e.startsWith("admrul/"));
        assertThat(ingest.uploads.get(1).entries).contains(
            "admrul/예규/가석방 업무지침/가석방 업무지침.json", "admrul/예규/가석방 업무지침/첨부/첨부1.hwp");
    }

    // ── 첨부 처리 ─────────────────────────────────────────

    @Test
    void 허용되지_않는_확장자는_빼고_manifest에_사유를_남긴다() throws Exception {
        cleansed(rule("9", "지침", "{\"첨부파일링크\":[\"http://law.go.kr/flDownload.do?flSeq=1\",\"http://law.go.kr/flDownload.do?flSeq=2\"],"
            + "\"첨부파일명\":[\"a.hwp\",\"자료.zip\"]}"));
        fetcher.add("https://law.go.kr/flDownload.do?flSeq=1", "h", "a.hwp");
        fetcher.add("https://law.go.kr/flDownload.do?flSeq=2", "PK..", "자료.zip");

        SendResult result = service().transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(1, 1, 0));
        assertThat(ingest.uploads.get(0).entries).contains("admrul/예규/지침/첨부/첨부1.hwp").noneMatch(e -> e.endsWith(".zip"));
        assertThat(ingest.uploads.get(0).manifest).contains("허용되지 않는 확장자(zip)");
    }

    @Test
    void 영구_다운로드_실패는_그_파일만_빼고_문서는_보낸다() {
        cleansed(law("1", "형법", "법률", ANNEX));
        fetcher.add("https://www.law.go.kr/LSW/flDownload.do?flSeq=1", "h", "a.hwp");
        fetcher.fail("https://www.law.go.kr/LSW/flDownload.do?flSeq=2", true);

        SendResult result = service().transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(1, 1, 0));
        assertThat(ingest.uploads.get(0).entries).contains("law/법률/형법/별표/별표1.hwp").noneMatch(e -> e.endsWith("별표1.pdf"));
        assertThat(ingest.uploads.get(0).manifest).contains("skippedFiles");
    }

    @Test
    void 일시_다운로드_실패는_그_문서를_통째로_제외하고_기록하지_않아_다음에_다시_시도한다() {
        cleansed(law("1", "형법", "법률", ANNEX));
        cleansed(law("2", "민법", "법률", ""));
        fetcher.add("https://www.law.go.kr/LSW/flDownload.do?flSeq=1", "h", "a.hwp");
        fetcher.fail("https://www.law.go.kr/LSW/flDownload.do?flSeq=2", false);

        SendResult result = service().transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(2, 1, 1));
        assertThat(ingest.uploads).hasSize(1);
        assertThat(ingest.uploads.get(0).entries).contains("law/법률/민법/민법.json").noneMatch(e -> e.contains("형법"));
        assertThat(ledger.isUnchanged("LAW:1", LawTransferService.fingerprint(LawDocumentParser.parse(law("1", "형법", "법률", ANNEX)).get(0)))).isFalse();
    }

    // ── 전송 실패 ─────────────────────────────────────────

    @Test
    void ping이_실패하면_내려받기_전에_전부_실패로_끝낸다() {
        cleansed(law("1", "형법", "법률", ANNEX));
        ingest.pingFails = true;

        SendResult result = service().transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(1, 0, 1));
        assertThat(fetcher.requested).isEmpty();
        assertThat(ingest.uploads).isEmpty();
    }

    @Test
    void 업로드가_실패하면_기록하지_않고_그_ZIP의_문서를_실패로_센다_다음_ZIP은_계속_시도한다() {
        cleansed(law("1", "형법", "법률", ""));
        cleansed(rule("9", "지침", ""));
        ingest.failUploadNumbers.add(1);

        SendResult result = service().transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(2, 1, 1));
        assertThat(ledger.isUnchanged("LAW:1", LawTransferService.fingerprint(LawDocumentParser.parse(law("1", "형법", "법률", "")).get(0)))).isFalse();
        assertThat(ledger.isUnchanged("ADMIN_RULE:9", LawTransferService.fingerprint(LawDocumentParser.parse(rule("9", "지침", "")).get(0)))).isTrue();
    }

    @Test
    void 실패해도_행은_종결해서_정제된_행이_쌓이지_않는다() {
        RawStagingDto row = cleansed(law("1", "형법", "법률", ""));
        ingest.failUploadNumbers.add(1);

        service().transfer(RUN);

        assertThat(status(row)).isEqualTo("LOAD_SKIPPED");
        assertThat(store.findByStatusAndRunId("CLEANSED", 10, RUN)).isEmpty();
    }

    @Test
    void 해석할_수_없는_정제본은_실패로_세고_나머지는_보낸다() {
        cleansed("[{\"엉뚱한\": 1}]");
        cleansed(law("1", "형법", "법률", ""));

        SendResult result = service().transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(2, 1, 1));
    }

    @Test
    void 상한을_넘으면_여러_ZIP으로_나눠_보낸다() {
        for (int i = 1; i <= 3; i++) {
            cleansed(law(String.valueOf(i), "법" + i, "법률", ""));
        }

        // 상한 1바이트 - 문서마다 ZIP이 하나씩 (문서는 쪼개지 않는다)
        SendResult result = service(true, false, false, 1).transfer(RUN);

        assertThat(result).isEqualTo(new SendResult(3, 3, 0));
        assertThat(ingest.uploads).hasSize(3);
    }

    @Test
    void 임시_ZIP은_전송_뒤_지워진다() {
        cleansed(law("1", "형법", "법률", ""));

        service().transfer(RUN);

        assertThat(ingest.uploads.get(0).file).doesNotExist();
    }

    // ── 가짜 ──────────────────────────────────────────────

    static class FakeFetcher implements LawFileFetcher {
        final Map<String, FetchedFile> files = new HashMap<>();
        final Map<String, Boolean> failures = new HashMap<>();
        final List<String> requested = new CopyOnWriteArrayList<>();

        void add(String url, String content, String name) {
            files.put(url, new FetchedFile(content.getBytes(StandardCharsets.UTF_8), name, null));
        }

        void fail(String url, boolean permanent) {
            failures.put(url, permanent);
        }

        @Override
        public FetchedFile fetch(String url) throws FetchException {
            requested.add(url);
            if (failures.containsKey(url)) {
                throw new FetchException("실패 " + url, failures.get(url));
            }
            FetchedFile f = files.get(url);
            if (f == null) {
                throw new FetchException("없음 " + url, true);
            }
            return f;
        }
    }

    class FakeIngest implements LawIngestPort {
        final List<Uploaded> uploads = new ArrayList<>();
        final List<Integer> failUploadNumbers = new ArrayList<>();
        boolean pingFails;
        int pingCount;
        int uploadCalls;

        @Override
        public void ping() throws IngestException {
            pingCount++;
            if (pingFails) {
                throw new IngestException("ping 실패");
            }
        }

        @Override
        public Receipt upload(ZipPart part) throws IngestException {
            uploadCalls++;
            if (failUploadNumbers.contains(uploadCalls)) {
                throw new IngestException("업로드 실패");
            }
            try (ZipFile zip = new ZipFile(part.file().toFile(), StandardCharsets.UTF_8)) {
                List<String> names = zip.stream().map(ZipEntry::getName).toList();
                String manifest = new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(), StandardCharsets.UTF_8);
                uploads.add(new Uploaded(part.file(), names, manifest));
            } catch (IOException e) {
                throw new IngestException("읽기 실패", e);
            }
            return new Receipt(true, "x.zip", part.sizeBytes(), part.sha256(), true, 0, part.jsonCount(), part.attachmentCount(), part.topLevelFolders());
        }
    }

    record Uploaded(Path file, List<String> entries, String manifest) {
    }
}
