package egovframework.external.publicdata.transfer;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class LawZipWriterTest {

    @TempDir
    Path dir;

    private static LawDocument doc(String type, String id, String name, String kind) {
        String body = "{\"x\":\"" + name + "\"}";
        return new LawDocument(type, id, name, kind, "20260101", "20260201", "법무부", body, List.of(), List.of());
    }

    private static PreparedDocument.PackedFile file(String folder, String base, String ext, int size) {
        return new PreparedDocument.PackedFile(folder, base, ext, new byte[size], base, base + "." + ext, "서식파일");
    }

    private static List<String> entries(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile z = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            z.stream().map(ZipEntry::getName).forEach(names::add);
        }
        return names;
    }

    @Test
    void 규약_레이아웃으로_쓰고_manifest를_최상위에_넣는다() throws Exception {
        try (LawZipWriter writer = new LawZipWriter(dir, "law", 10_000_000, Instant.parse("2026-10-23T00:00:00Z"))) {
            assertThat(writer.add(new PreparedDocument(doc("LAW", "1", "형법", "법률"),
                List.of(file("별표", "별표1", "hwp", 10), file("별표", "별표1", "pdf", 20)), List.of()))).isNull();
            assertThat(writer.add(new PreparedDocument(doc("LAW", "2", "마약류 관리에 관한 법률 시행령", "대통령령"),
                List.of(), List.of()))).isNull();
            ZipPart part = writer.finish();

            assertThat(entries(part.file())).containsExactlyInAnyOrder(
                "law/법률/형법/형법.json",
                "law/법률/형법/별표/별표1.hwp",
                "law/법률/형법/별표/별표1.pdf",
                "law/시행령/마약류 관리에 관한 법률 시행령/마약류 관리에 관한 법률 시행령.json",
                "manifest.json");
            assertThat(part.jsonCount()).isEqualTo(2);
            assertThat(part.attachmentCount()).isEqualTo(2);
            assertThat(part.topLevelFolders()).isEqualTo(1);
            assertThat(part.sizeBytes()).isEqualTo(part.file().toFile().length());
            assertThat(part.sha256()).hasSize(64).isEqualTo(LawZipWriter.sha256(part.file()));
            assertThat(part.documents()).extracting(LawDocument::lawId).containsExactly("1", "2");
        }
    }

    @Test
    void 항목_이름은_UTF8_플래그로_기록된다() throws Exception {
        try (LawZipWriter writer = new LawZipWriter(dir, "law", 10_000_000, Instant.now())) {
            writer.add(new PreparedDocument(doc("LAW", "1", "형법", "법률"), List.of(), List.of()));
            ZipPart part = writer.finish();

            // 일부 압축 도구가 CP949로 쓰면 서버에서 이름이 깨진다 - EFS(UTF-8) 비트(0x0800)가 서 있어야 한다.
            try (InputStream in = java.nio.file.Files.newInputStream(part.file())) {
                byte[] header = in.readNBytes(10);
                int flags = (header[6] & 0xFF) | ((header[7] & 0xFF) << 8);
                assertThat(flags & 0x0800).isEqualTo(0x0800);
            }
        }
    }

    @Test
    void manifest에는_문서_메타와_첨부_해시_건너뛴_사유가_들어간다() throws Exception {
        try (LawZipWriter writer = new LawZipWriter(dir, "admrul", 10_000_000, Instant.parse("2026-10-23T00:00:00Z"))) {
            writer.add(new PreparedDocument(doc("ADMIN_RULE", "35334", "가석방 업무지침", "예규"),
                List.of(new PreparedDocument.PackedFile("첨부", "첨부1", "hwp", new byte[]{1, 2, 3}, "가석방 업무지침.hwp", "원본.hwp", "첨부")),
                List.of(new PreparedDocument.SkippedFile("자료.zip", "첨부", "허용되지 않는 확장자(zip)"))));
            ZipPart part = writer.finish();

            JSONObject manifest;
            try (ZipFile z = new ZipFile(part.file().toFile(), StandardCharsets.UTF_8);
                 InputStream in = z.getInputStream(z.getEntry("manifest.json"))) {
                manifest = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            JSONObject d = manifest.getJSONArray("documents").getJSONObject(0);
            assertThat(manifest.getInt("documentCount")).isEqualTo(1);
            assertThat(d.getString("path")).isEqualTo("admrul/예규/가석방 업무지침/가석방 업무지침.json");
            assertThat(d.getString("lawId")).isEqualTo("35334");
            assertThat(d.getString("kind")).isEqualTo("예규");
            assertThat(d.getString("sha256")).hasSize(64);
            JSONObject f = d.getJSONArray("files").getJSONObject(0);
            assertThat(f.getString("path")).isEqualTo("admrul/예규/가석방 업무지침/첨부/첨부1.hwp");
            assertThat(f.getString("originalName")).isEqualTo("원본.hwp");
            assertThat(f.getInt("size")).isEqualTo(3);
            assertThat(f.getString("sha256")).isEqualTo(LawZipWriter.sha256(new byte[]{1, 2, 3}));
            assertThat(d.getJSONArray("skippedFiles").getJSONObject(0).getString("reason")).contains("zip");
        }
    }

    @Test
    void 상한을_넘기면_문서_단위로_ZIP을_나눠_완성된_것을_돌려준다() throws Exception {
        try (LawZipWriter writer = new LawZipWriter(dir, "law", 1_000, Instant.now())) {
            // 압축이 안 되는 난수 파일 - 1KB 상한에 문서 하나(800B)가 들어가면 다음 문서는 새 ZIP
            byte[] noise = new byte[800];
            new java.util.Random(7).nextBytes(noise);
            PreparedDocument a = withFile(doc("LAW", "1", "가법", "법률"), noise);
            PreparedDocument b = withFile(doc("LAW", "2", "나법", "법률"), noise);
            PreparedDocument c = withFile(doc("LAW", "3", "다법", "법률"), noise);

            assertThat(writer.add(a)).isNull();
            ZipPart first = writer.add(b);
            ZipPart second = writer.add(c);
            ZipPart third = writer.finish();

            assertThat(first.documents()).extracting(LawDocument::lawId).containsExactly("1");
            assertThat(second.documents()).extracting(LawDocument::lawId).containsExactly("2");
            assertThat(third.documents()).extracting(LawDocument::lawId).containsExactly("3");
            assertThat(List.of(first.index(), second.index(), third.index())).containsExactly(1, 2, 3);
            // 문서는 쪼개지 않는다 - 본문과 별표가 같은 ZIP에 있다
            assertThat(entries(first.file())).contains("law/법률/가법/가법.json", "law/법률/가법/별표/별표1.hwp");
        }
    }

    @Test
    void 문서_하나가_상한보다_커도_쪼개지_않고_그_문서만_담은_ZIP이_된다() throws Exception {
        try (LawZipWriter writer = new LawZipWriter(dir, "law", 100, Instant.now())) {
            byte[] noise = new byte[5_000];
            new java.util.Random(1).nextBytes(noise);

            assertThat(writer.add(withFile(doc("LAW", "1", "큰법", "법률"), noise))).isNull();
            ZipPart part = writer.finish();

            assertThat(part.documents()).hasSize(1);
            assertThat(part.sizeBytes()).isGreaterThan(100);
        }
    }

    @Test
    void 같은_종류_같은_이름의_다른_문서는_법령ID를_붙여_구분하고_같은_파일명은_번호를_붙인다() throws Exception {
        try (LawZipWriter writer = new LawZipWriter(dir, "law", 10_000_000, Instant.now())) {
            writer.add(new PreparedDocument(doc("LAW", "1", "같은법", "법률"),
                List.of(file("별표", "별표1", "gif", 1), file("별표", "별표1", "gif", 2)), List.of()));
            writer.add(new PreparedDocument(doc("LAW", "2", "같은법", "법률"), List.of(), List.of()));
            ZipPart part = writer.finish();

            assertThat(entries(part.file())).contains(
                "law/법률/같은법/같은법.json",
                "law/법률/같은법/별표/별표1.gif",
                "law/법률/같은법/별표/별표1_2.gif",
                "law/법률/같은법_2/같은법_2.json");
        }
    }

    @Test
    void 법령과_행정규칙이_섞이면_최상위_폴더_수가_2다() throws Exception {
        try (LawZipWriter writer = new LawZipWriter(dir, "mixed", 10_000_000, Instant.now())) {
            writer.add(new PreparedDocument(doc("LAW", "1", "형법", "법률"), List.of(), List.of()));
            writer.add(new PreparedDocument(doc("ADMIN_RULE", "9", "지침", "훈령"), List.of(), List.of()));

            assertThat(writer.finish().topLevelFolders()).isEqualTo(2);
        }
    }

    @Test
    void 문서가_없으면_ZIP을_만들지_않는다() throws Exception {
        try (LawZipWriter writer = new LawZipWriter(dir, "law", 1_000, Instant.now())) {
            assertThat(writer.finish()).isNull();
        }
        try (var files = java.nio.file.Files.list(dir)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    private static PreparedDocument withFile(LawDocument doc, byte[] content) {
        return new PreparedDocument(doc,
            List.of(new PreparedDocument.PackedFile("별표", "별표1", "hwp", content, "t", null, "서식파일")), List.of());
    }
}
