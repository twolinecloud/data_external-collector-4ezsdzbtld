package egovframework.external.publicdata.transfer;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XenonIngestClientTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<String> contentTypes = new CopyOnWriteArrayList<>();
    private final List<byte[]> bodies = new CopyOnWriteArrayList<>();
    private final List<String> contentLengths = new CopyOnWriteArrayList<>();
    private volatile Function<ZipPartFacts, String> responder;
    private volatile int status = 200;

    record ZipPartFacts(int call) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/ping", ex -> {
            authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.createContext("/api/v1/law/ingest", ex -> {
            authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            contentTypes.add(ex.getRequestHeaders().getFirst("Content-Type"));
            contentLengths.add(ex.getRequestHeaders().getFirst("Content-Length"));
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            ex.getRequestBody().transferTo(buf);
            bodies.add(buf.toByteArray());
            byte[] out = responder.apply(new ZipPartFacts(bodies.size())).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private XenonIngestClient client(String apiKey, int attempts) {
        return new XenonIngestClient(HttpClient.newHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort() + "/",
            apiKey, attempts, Duration.ofSeconds(10), 1L);
    }

    private ZipPart part(byte[] content, int jsonCount, int topLevel) throws IOException {
        Path file = Files.write(dir.resolve("law-1.zip"), content);
        return new ZipPart(1, file, LawZipWriter.sha256(content), content.length, jsonCount, 3, topLevel, List.of(), "{\"documents\":[{\"lawId\":\"001692\"}]}");
    }

    private static String receipt(ZipPart p, boolean valid, long size, String sha, int json, int top) {
        return "{\"received\":true,\"filename\":\"law-1.zip\",\"size_bytes\":" + size + ",\"sha256\":\"" + sha
            + "\",\"received_at\":\"2026-10-23T00:00:00Z\",\"internal_x\":1,\"zip\":{\"valid\":" + valid
            + ",\"entries\":9,\"json\":" + json + ",\"attachments\":3,\"top_level_count\":" + top
            + ",\"top_level_sample\":[\"law\"]}}";
    }

    @Test
    void 파일을_multipart_file_필드와_Bearer_인증으로_올리고_수신_응답을_대조한다() throws Exception {
        byte[] content = "PK-fake-zip-bytes".getBytes(StandardCharsets.UTF_8);
        ZipPart p = part(content, 2, 1);
        responder = f -> receipt(p, true, p.sizeBytes(), p.sha256(), 2, 1);

        LawIngestPort.Receipt r = client("secret-key", 3).upload(p);

        assertThat(r.received()).isTrue();
        assertThat(r.sha256()).isEqualTo(p.sha256());
        assertThat(authHeaders).containsExactly("Bearer secret-key");
        assertThat(contentTypes.get(0)).startsWith("multipart/form-data; boundary=");
        String body = new String(bodies.get(0), StandardCharsets.ISO_8859_1);
        assertThat(body).contains("name=\"file\"").contains("Content-Type: application/zip").contains("PK-fake-zip-bytes");
        // 본문 길이를 미리 알려 chunked 업로드를 피한다
        assertThat(contentLengths.get(0)).isEqualTo(String.valueOf(bodies.get(0).length));
    }

    @Test
    void manifest를_ZIP에_넣지_않으므로_json과_top_level은_정확히_같아야_한다() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);

        responder = f -> receipt(p, true, p.sizeBytes(), p.sha256(), 3, 1);
        assertThatThrownBy(() -> client("k", 1).upload(p)).hasMessageContaining("zip.json 불일치");

        responder = f -> receipt(p, true, p.sizeBytes(), p.sha256(), 2, 2);
        assertThatThrownBy(() -> client("k", 1).upload(p)).hasMessageContaining("top_level_count 불일치");
    }

    @Test
    void 체크섬이_다르면_재전송하고_계속_다르면_실패한다() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);
        responder = f -> receipt(p, true, p.sizeBytes(), "0".repeat(64), 2, 1);

        assertThatThrownBy(() -> client("k", 3).upload(p))
            .isInstanceOf(LawIngestPort.IngestException.class).hasMessageContaining("sha256 불일치");
        assertThat(bodies).hasSize(3);
    }

    @Test
    void 처음엔_어긋나다_재전송에서_맞으면_성공한다() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);
        responder = f -> f.call() == 1
            ? receipt(p, true, p.sizeBytes() - 1, p.sha256(), 2, 1)
            : receipt(p, true, p.sizeBytes(), p.sha256(), 2, 1);

        assertThat(client("k", 3).upload(p).received()).isTrue();
        assertThat(bodies).hasSize(2);
    }

    @Test
    void zip_valid가_false거나_json_수가_어긋나면_실패() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);

        responder = f -> receipt(p, false, p.sizeBytes(), p.sha256(), 2, 1);
        assertThatThrownBy(() -> client("k", 1).upload(p)).hasMessageContaining("zip.valid");

        responder = f -> receipt(p, true, p.sizeBytes(), p.sha256(), 5, 1);
        assertThatThrownBy(() -> client("k", 1).upload(p)).hasMessageContaining("zip.json");

        responder = f -> receipt(p, true, p.sizeBytes(), p.sha256(), 2, 4);
        assertThatThrownBy(() -> client("k", 1).upload(p)).hasMessageContaining("top_level_count");
    }

    @Test
    void HTTP_오류와_JSON이_아닌_응답은_실패() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);

        status = 401;
        responder = f -> "{\"detail\":\"Unauthorized\"}";
        assertThatThrownBy(() -> client("bad", 1).upload(p)).hasMessageContaining("HTTP 401");

        status = 200;
        responder = f -> "<html>gateway</html>";
        assertThatThrownBy(() -> client("k", 1).upload(p)).hasMessageContaining("JSON이 아님");
    }

    @Test
    void API_키가_없으면_호출하지_않고_실패한다() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);

        assertThatThrownBy(() -> client("", 1).upload(p)).hasMessageContaining("API 키");
        assertThatThrownBy(() -> client(" ", 1).ping()).hasMessageContaining("API 키");
        assertThat(bodies).isEmpty();
        assertThat(authHeaders).isEmpty();
    }

    @Test
    void ping은_같은_인증_헤더로_200이면_통과하고_아니면_실패() throws Exception {
        client("secret-key", 1).ping();
        assertThat(authHeaders).containsExactly("Bearer secret-key");

        status = 403;
        assertThatThrownBy(() -> client("secret-key", 1).ping()).hasMessageContaining("HTTP 403");
    }

    @Test
    void 오류_메시지에_API_키가_들어가지_않는다() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);
        status = 500;
        responder = f -> "server error";

        assertThatThrownBy(() -> client("super-secret-token", 2).upload(p))
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("super-secret-token"));
    }

    @Test
    void manifest를_같은_요청의_manifest_필드로도_보낸다() throws Exception {
        // 2026-10-27 실운영: file 만 보내자 422 "manifest 가 없습니다 — 같은 요청의 'manifest' 필드로 함께 보내주세요"
        byte[] content = "PK-fake-zip-bytes".getBytes(StandardCharsets.UTF_8);
        ZipPart p = part(content, 2, 1);
        responder = f -> receipt(p, true, p.sizeBytes(), p.sha256(), 2, 1);

        client("k", 1).upload(p);

        String body = new String(bodies.get(0), StandardCharsets.UTF_8);
        // Xenon 은 manifest 도 파일(UploadFile)로 받는다 - filename 이 없으면 422 "Expected UploadFile, received: str"
        assertThat(body).contains("name=\"manifest\"; filename=\"manifest.json\"").contains("Content-Type: application/json; charset=UTF-8")
            .contains("{\"documents\":[{\"lawId\":\"001692\"}]}");
        // manifest 가 파일보다 앞에 오고, 그래도 본문 길이를 미리 알려 chunked 를 피한다
        assertThat(body.indexOf("name=\"manifest\"")).isLessThan(body.indexOf("name=\"file\""));
        assertThat(contentLengths.get(0)).isEqualTo(String.valueOf(bodies.get(0).length));
    }

    @Test
    void 요청이_잘못됐다는_400번대_응답은_같은_ZIP을_다시_올리지_않는다() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);
        status = 422;
        responder = f -> "{\"received\":false,\"error\":\"manifest 가 없습니다\"}";

        assertThatThrownBy(() -> client("k", 3).upload(p)).hasMessageContaining("HTTP 422");

        assertThat(bodies).hasSize(1); // 3번 시도하도록 설정했어도 한 번만 올린다
    }

    @Test
    void 서버_오류_500은_재시도한다() throws Exception {
        ZipPart p = part("abc".getBytes(StandardCharsets.UTF_8), 2, 1);
        status = 500;
        responder = f -> "{\"detail\":\"boom\"}";

        assertThatThrownBy(() -> client("k", 3).upload(p)).hasMessageContaining("HTTP 500");

        assertThat(bodies).hasSize(3);
    }
}
