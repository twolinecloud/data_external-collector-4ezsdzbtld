package egovframework.external.publicdata.scheduler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** admin-api 응답 계약(GET /api/batch-schedules)을 로컬 스텁 서버로 고정한다. 외부 의존이 없다. */
class AdminBatchScheduleClientTest {

    private HttpServer server;
    private String baseUrl;

    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedMethod = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("");
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedMethod.set(exchange.getRequestMethod());
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AdminBatchScheduleClient client(String url) {
        return new AdminBatchScheduleClient(url, 1000, 2000);
    }

    @Test
    void 목록_API를_GET으로_호출해_행을_읽는다() {
        responseBody.set("""
            {"success":true,"code":0,"http_status_code":200,"result":[
              {"schdId":1,"dataTypeCd":"STRUCTURED","schdCd":null,"activeYn":"Y","schedVal":"03:10"},
              {"schdId":3,"dataTypeCd":"EXTERNAL_PUBLIC","schdCd":null,"schdNm":null,"activeYn":"N"},
              {"schdId":5,"dataTypeCd":"EXTERNAL_PUBLIC","schdCd":"KMA_ASOS_HOURLY","schdNm":"지상관측","activeYn":"Y"}
            ]}""");

        List<AdminBatchScheduleClient.Row> rows = client(baseUrl + "/").fetchAll();

        assertThat(capturedMethod.get()).isEqualTo("GET");
        assertThat(capturedPath.get()).isEqualTo("/api/batch-schedules");
        assertThat(rows).containsExactly(
            new AdminBatchScheduleClient.Row("STRUCTURED", null, "Y"),
            new AdminBatchScheduleClient.Row("EXTERNAL_PUBLIC", null, "N"),
            new AdminBatchScheduleClient.Row("EXTERNAL_PUBLIC", "KMA_ASOS_HOURLY", "Y"));
    }

    @Test
    void 주소가_비어_있으면_설정되지_않은_것이다() {
        assertThat(client("").isConfigured()).isFalse();
        assertThat(client("  ").isConfigured()).isFalse();
        assertThat(client(null).isConfigured()).isFalse();
        assertThat(client(baseUrl).isConfigured()).isTrue();
    }

    @Test
    void 비2xx_응답은_예외다() {
        responseStatus.set(500);

        assertThatThrownBy(() -> client(baseUrl).fetchAll()).isInstanceOf(RestClientException.class);
    }

    @Test
    void 기대한_형식이_아니면_예외다() {
        for (String body : new String[]{"{}", "{\"result\":{}}", "{\"result\":null}", "not json"}) {
            responseBody.set(body);

            assertThatThrownBy(() -> client(baseUrl).fetchAll()).as(body).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void 연결할_수_없으면_예외다() {
        assertThatThrownBy(() -> client("http://localhost:1").fetchAll()).isInstanceOf(RestClientException.class);
    }
}
