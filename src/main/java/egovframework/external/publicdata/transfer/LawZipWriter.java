package egovframework.external.publicdata.transfer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 문서를 하나씩 받아 Xenon 규약 레이아웃({@link LawArchiveLayout})으로 ZIP에 쓴다. 한 ZIP이 {@code maxBytes}를
 * 넘을 것 같으면 새 ZIP으로 넘어간다 - 전체가 수백 MB라 하나로 보낼 수 없어서다. 문서는 쪼개지 않는다(법령 본문과
 * 그 별표는 항상 같은 ZIP). 문서 하나가 상한보다 크면 그 문서만 담은 ZIP이 상한을 넘긴다.
 *
 * <p>ZIP마다 manifest(문서별 법령ID·종류·시행일·본문 sha256·첨부 목록(제목, 원본 파일명, sha256)과 넣지 못한 첨부의 사유)를
 * 만들어 {@link ZipPart#manifestJson()}으로 넘긴다. <b>ZIP 안에는 넣지 않는다</b> - Xenon 규약은 manifest를 업로드 요청의
 * 별도 파일 파트로 받는다(2026-10-27 확인). 항목 이름은 UTF-8이다({@link ZipOutputStream}은 UTF-8로 쓰면 EFS 플래그를
 * 세운다 - 규약이 요구하는 한글 이름 인코딩).</p>
 *
 * <p>스레드 안전하지 않다. {@link #finish}를 부르거나 {@link #close}로 열린 ZIP을 정리한다.</p>
 */
public class LawZipWriter implements AutoCloseable {

    private final Path dir;
    private final String prefix;
    private final long maxBytes;
    private final Instant generatedAt;

    private final Set<String> usedDirs = new HashSet<>();

    private Current current;
    private int partCount;

    public LawZipWriter(Path dir, String prefix, long maxBytes, Instant generatedAt) {
        this.dir = dir;
        this.prefix = prefix;
        this.maxBytes = maxBytes;
        this.generatedAt = generatedAt;
    }

    /**
     * 문서를 현재 ZIP에 넣는다. 이 문서가 들어가면 상한을 넘겨서 먼저 ZIP을 닫았다면 그 완성된 ZIP을 돌려준다
     * (호출부가 바로 전송하고 지우면 디스크에 여러 개가 쌓이지 않는다). 닫을 일이 없었으면 null.
     */
    public ZipPart add(PreparedDocument prepared) throws IOException {
        ZipPart completed = null;
        long estimate = estimateBytes(prepared);
        if (current != null && current.documents() > 0 && current.counter.count + estimate > maxBytes) {
            completed = closeCurrent();
        }
        if (current == null) {
            current = openNext();
        }
        current.write(prepared);
        return completed;
    }

    /** 열린 ZIP을 마무리해 돌려준다. 문서가 하나도 없었으면 null. */
    public ZipPart finish() throws IOException {
        return closeCurrent();
    }

    @Override
    public void close() throws IOException {
        if (current != null) {
            current.abort();
            current = null;
        }
    }

    private static long estimateBytes(PreparedDocument prepared) {
        long total = prepared.doc().body().length() / 4L; // JSON은 잘 압축된다
        for (PreparedDocument.PackedFile f : prepared.files()) {
            total += f.content().length; // HWP/PDF는 거의 줄지 않는다
        }
        return total;
    }

    private Current openNext() throws IOException {
        int index = ++partCount;
        Path file = Files.createTempFile(dir, prefix + "-" + index + "-", ".zip");
        return new Current(index, file);
    }

    private ZipPart closeCurrent() throws IOException {
        if (current == null) {
            return null;
        }
        Current c = current;
        current = null;
        if (c.documents() == 0) {
            c.abort();
            return null;
        }
        return c.finish();
    }

    /** 지금 쓰고 있는 ZIP 하나. */
    private final class Current {
        final int index;
        final Path file;
        final CountingOutputStream counter;
        final ZipOutputStream zip;
        final Set<String> topFolders = new LinkedHashSet<>();
        final List<LawDocument> documents = new ArrayList<>();
        final JSONArray manifestDocs = new JSONArray();
        int attachmentCount;

        Current(int index, Path file) throws IOException {
            this.index = index;
            this.file = file;
            this.counter = new CountingOutputStream(new BufferedOutputStream(Files.newOutputStream(file)));
            this.zip = new ZipOutputStream(counter, StandardCharsets.UTF_8);
        }

        int documents() {
            return documents.size();
        }

        void write(PreparedDocument prepared) throws IOException {
            LawDocument doc = prepared.doc();
            String dirName = LawArchiveLayout.sanitize(doc.name());
            String docDir = LawArchiveLayout.documentDir(doc, dirName);
            if (!usedDirs.add(LawArchiveLayout.collisionKey(docDir))) {
                // 같은 종류·같은 이름의 다른 문서 - 실측 491건에선 없었지만, 덮어쓰면 한 건이 조용히 사라지므로 ID로 구분한다.
                dirName = LawArchiveLayout.sanitize(doc.name() + "_" + doc.lawId());
                docDir = LawArchiveLayout.documentDir(doc, dirName);
                usedDirs.add(LawArchiveLayout.collisionKey(docDir));
            }

            String bodyPath = LawArchiveLayout.bodyPath(docDir, dirName);
            byte[] body = doc.body().getBytes(StandardCharsets.UTF_8);
            putEntry(bodyPath, body);
            topFolders.add(LawArchiveLayout.topFolder(doc));

            JSONObject manifestDoc = new JSONObject()
                .put("path", bodyPath)
                .put("docType", doc.docType())
                .put("lawId", doc.lawId())
                .put("name", doc.name())
                .put("kind", doc.kind())
                .put("promulgationDate", doc.promulgationDate())
                .put("effectiveDate", doc.effectiveDate())
                .put("ministry", doc.ministry())
                .put("sha256", sha256(body));

            JSONArray manifestFiles = new JSONArray();
            Set<String> usedFiles = new HashSet<>();
            for (PreparedDocument.PackedFile f : prepared.files()) {
                String path = uniqueFilePath(docDir, f, usedFiles);
                putEntry(path, f.content());
                attachmentCount++;
                manifestFiles.put(new JSONObject()
                    .put("path", path)
                    .put("kind", f.kind())
                    .put("title", f.title())
                    .put("originalName", f.originalName() == null ? JSONObject.NULL : f.originalName())
                    .put("size", f.content().length)
                    .put("sha256", sha256(f.content())));
            }
            manifestDoc.put("files", manifestFiles);

            JSONArray manifestSkipped = new JSONArray();
            for (PreparedDocument.SkippedFile s : prepared.skipped()) {
                manifestSkipped.put(new JSONObject()
                    .put("title", s.title())
                    .put("kind", s.kind())
                    .put("reason", s.reason()));
            }
            manifestDoc.put("skippedFiles", manifestSkipped);

            manifestDocs.put(manifestDoc);
            documents.add(doc);
        }

        private String uniqueFilePath(String docDir, PreparedDocument.PackedFile f, Set<String> used) {
            String base = LawArchiveLayout.sanitize(f.baseName());
            String path = docDir + "/" + f.folder() + "/" + base + "." + f.extension();
            for (int n = 2; !used.add(LawArchiveLayout.collisionKey(path)); n++) {
                path = docDir + "/" + f.folder() + "/" + base + "_" + n + "." + f.extension();
            }
            return path;
        }

        private void putEntry(String path, byte[] content) throws IOException {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(content);
            zip.closeEntry();
        }

        ZipPart finish() throws IOException {
            JSONObject manifest = new JSONObject()
                .put("generatedAt", generatedAt.toString())
                .put("source", "국가법령정보센터(법제처)")
                .put("part", index)
                .put("documentCount", documents.size())
                .put("documents", manifestDocs);
            String manifestJson = manifest.toString(2);
            zip.close();
            return new ZipPart(index, file, sha256(file), Files.size(file), documents.size(),
                attachmentCount, topFolders.size(), List.copyOf(documents), manifestJson);
        }

        void abort() throws IOException {
            try {
                zip.close();
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    static String sha256(byte[] data) {
        MessageDigest digest = digest();
        return HexFormat.of().formatHex(digest.digest(data));
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 압축 후 ZIP 파일에 실제로 쓰인 바이트 수를 센다 - 분할 기준이 압축 후 크기라서. */
    private static final class CountingOutputStream extends FilterOutputStream {
        long count;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}
