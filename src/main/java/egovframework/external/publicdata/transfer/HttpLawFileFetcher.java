package egovframework.external.publicdata.transfer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 국가법령정보센터 첨부 다운로드({@code flDownload.do?flSeq=...}). 인증 없이 열리고 응답이 chunked라 크기를 미리
 * 알 수 없어서, 받으면서 {@code maxBytes}를 넘으면 끊는다.
 *
 * <p>행정규칙 첨부 링크는 {@code http://}로 오고 https로 301 되므로 리다이렉트를 따라간다. 5xx·타임아웃은
 * 일시 오류로 보고 재시도하고, 404/410은 영구 실패로 돌려준다({@link FetchException#isPermanent}).</p>
 */
@Component
public class HttpLawFileFetcher implements LawFileFetcher {

    private static final Pattern FILENAME_STAR = Pattern.compile("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILENAME = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    private final HttpClient client;
    private final long maxBytes;
    private final int maxAttempts;
    private final Duration requestTimeout;

    @Autowired
    public HttpLawFileFetcher(
        @Value("${public-data.law-transfer.download.max-bytes:52428800}") long maxBytes,
        @Value("${public-data.law-transfer.download.max-attempts:3}") int maxAttempts,
        @Value("${public-data.law-transfer.download.timeout:60s}") Duration requestTimeout
    ) {
        this(HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build(), maxBytes, maxAttempts, requestTimeout);
    }

    HttpLawFileFetcher(HttpClient client, long maxBytes, int maxAttempts, Duration requestTimeout) {
        this.client = client;
        this.maxBytes = maxBytes;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.requestTimeout = requestTimeout;
    }

    @Override
    public FetchedFile fetch(String url) throws FetchException {
        FetchException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return fetchOnce(url);
            } catch (FetchException e) {
                if (e.isPermanent()) {
                    throw e;
                }
                last = e;
                sleepBeforeRetry(attempt);
            }
        }
        throw last;
    }

    private FetchedFile fetchOnce(String url) throws FetchException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                int status = response.statusCode();
                if (status == 404 || status == 410) {
                    throw new FetchException("HTTP " + status + " " + url, true);
                }
                if (status != 200) {
                    throw new FetchException("HTTP " + status + " " + url, false);
                }
                byte[] content = readBounded(in, url);
                String disposition = response.headers().firstValue("Content-Disposition").orElse(null);
                return new FetchedFile(content, fileNameOf(disposition).orElse(null),
                    response.headers().firstValue("Content-Type").orElse(null));
            }
        } catch (IOException e) {
            throw new FetchException("다운로드 실패 " + url + ": " + e.getMessage(), false, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("다운로드 중단 " + url, false, e);
        }
    }

    private byte[] readBounded(InputStream in, String url) throws IOException, FetchException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
            if (out.size() > maxBytes) {
                // 크기 초과는 다시 받아도 같다 - 영구 실패로 이 파일만 건너뛴다.
                throw new FetchException("파일이 상한(" + maxBytes + "바이트)을 넘음 " + url, true);
            }
        }
        return out.toByteArray();
    }

    /** {@code filename*=UTF-8''...}를 우선하고, 없으면 {@code filename="..."}(URL 인코딩된 한글이 오는 경우가 있어 디코딩). */
    static Optional<String> fileNameOf(String contentDisposition) {
        if (contentDisposition == null) {
            return Optional.empty();
        }
        Matcher star = FILENAME_STAR.matcher(contentDisposition);
        if (star.find()) {
            return Optional.of(decode(star.group(2).strip()));
        }
        Matcher plain = FILENAME.matcher(contentDisposition);
        if (plain.find()) {
            return Optional.of(decode(plain.group(1).strip()));
        }
        return Optional.empty();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
