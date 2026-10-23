package egovframework.external.publicdata.transfer;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpLawFileFetcherTest {

    private HttpServer server;
    private final AtomicInteger flakyCalls = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", ex -> {
            ex.getResponseHeaders().add("Content-Disposition",
                "attachment; filename=\"%EB%B3%84%ED%91%9C1.hwpx\"");
            send(ex, 200, "내용");
        });
        server.createContext("/star", ex -> {
            ex.getResponseHeaders().add("Content-Disposition", "attachment; filename*=UTF-8''%EA%B0%80.pdf");
            send(ex, 200, "x");
        });
        server.createContext("/moved", ex -> {
            ex.getResponseHeaders().add("Location", "/ok");
            ex.sendResponseHeaders(301, -1);
            ex.close();
        });
        server.createContext("/gone", ex -> send(ex, 404, "no"));
        server.createContext("/flaky", ex -> {
            if (flakyCalls.incrementAndGet() < 3) {
                send(ex, 503, "busy");
            } else {
                send(ex, 200, "ok");
            }
        });
        server.createContext("/big", ex -> send(ex, 200, "x".repeat(2_000)));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private HttpLawFileFetcher fetcher(long maxBytes, int attempts) {
        return new HttpLawFileFetcher(
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), maxBytes, attempts, Duration.ofSeconds(5));
    }

    private static void send(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    @Test
    void 내려받고_URL_인코딩된_원본_파일명을_읽는다() throws Exception {
        LawFileFetcher.FetchedFile f = fetcher(10_000, 1).fetch(url("/ok"));

        assertThat(new String(f.content(), StandardCharsets.UTF_8)).isEqualTo("내용");
        assertThat(f.fileName()).isEqualTo("별표1.hwpx");
    }

    @Test
    void filename_star_형식도_읽는다() throws Exception {
        assertThat(fetcher(10_000, 1).fetch(url("/star")).fileName()).isEqualTo("가.pdf");
    }

    @Test
    void 리다이렉트를_따라간다() throws Exception {
        assertThat(fetcher(10_000, 1).fetch(url("/moved")).fileName()).isEqualTo("별표1.hwpx");
    }

    @Test
    void 일시_오류는_재시도해서_성공하고_404는_재시도_없이_영구_실패() throws Exception {
        assertThat(fetcher(10_000, 3).fetch(url("/flaky")).content()).isNotEmpty();
        assertThat(flakyCalls.get()).isEqualTo(3);

        assertThatThrownBy(() -> fetcher(10_000, 3).fetch(url("/gone")))
            .isInstanceOfSatisfying(LawFileFetcher.FetchException.class, e -> assertThat(e.isPermanent()).isTrue());
    }

    @Test
    void 일시_오류가_계속되면_일시_실패로_알린다() {
        flakyCalls.set(-100);

        assertThatThrownBy(() -> fetcher(10_000, 2).fetch(url("/flaky")))
            .isInstanceOfSatisfying(LawFileFetcher.FetchException.class, e -> assertThat(e.isPermanent()).isFalse());
    }

    @Test
    void 상한을_넘는_파일은_영구_실패로_끊는다() {
        assertThatThrownBy(() -> fetcher(1_000, 3).fetch(url("/big")))
            .isInstanceOfSatisfying(LawFileFetcher.FetchException.class, e -> {
                assertThat(e.isPermanent()).isTrue();
                assertThat(e.getMessage()).contains("상한");
            });
    }

    @Test
    void 확장자는_서버_파일명_힌트_머리_바이트_순으로_정한다() {
        var named = new LawFileFetcher.FetchedFile(new byte[0], "A.HWP", null);
        var unnamed = new LawFileFetcher.FetchedFile("%PDF-1.4".getBytes(StandardCharsets.UTF_8), null, null);

        assertThat(FileTypes.extensionOf(named, "x.pdf", "서식파일")).isEqualTo("hwp");
        assertThat(FileTypes.extensionOf(unnamed, "가.docx", "첨부")).isEqualTo("docx");
        assertThat(FileTypes.extensionOf(unnamed, null, "PDF")).isEqualTo("pdf");
        assertThat(FileTypes.sniff("GIF89a".getBytes(StandardCharsets.UTF_8), "이미지")).isEqualTo("gif");
        assertThat(FileTypes.sniff(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}, "서식파일")).isEqualTo("hwp");
        assertThat(FileTypes.sniff(new byte[]{'P', 'K', 3, 4}, "서식파일")).isEqualTo("hwpx");
        assertThat(FileTypes.sniff(new byte[]{'P', 'K', 3, 4}, "첨부")).isEqualTo("zip");
        assertThat(FileTypes.sniff(new byte[]{1, 2, 3}, "첨부")).isEqualTo("bin");
    }
}
