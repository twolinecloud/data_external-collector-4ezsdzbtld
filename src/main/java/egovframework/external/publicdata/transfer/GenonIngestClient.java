package egovframework.external.publicdata.transfer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Xenon(GenOS Code Serving 800) 법령 수신 API 클라이언트 - {@code private-doc/xenon_protocol.pdf} 기준.
 *
 * <ul>
 *   <li>{@code POST {base}/api/v1/law/ingest}, {@code Authorization: Bearer}, multipart 필드 {@code file}(application/zip) 1개</li>
 *   <li>{@code GET {base}/api/v1/ping} - 같은 인증 헤더로 200이면 정상</li>
 * </ul>
 *
 * <p>수백 MB 파일이라 메모리에 올리지 않고 파일을 스트리밍한다(multipart 본문을 직접 이어붙임 - 파일 한 개뿐이라
 * 헤더/푸터 바이트와 파일 스트림을 잇는 것으로 충분하다).</p>
 *
 * <p><b>무결성 대조</b>: 규약은 보내기 전 계산한 sha256과 응답 sha256, 그리고 size_bytes·zip.json·zip.top_level_count를
 * 대조해 하나라도 어긋나거나 zip.valid가 false면 재전송하라고 한다(같은 파일을 두 번 보내도 무해). 재전송은
 * {@code max-attempts}까지 한다.</p>
 *
 * <p>요청은 멀티파트 두 파트다: {@code manifest}(파일, manifest.json)와 {@code file}(ZIP). manifest는 ZIP 안에 넣지 않으므로
 * 응답의 {@code zip.json}은 본문 JSON 수, {@code top_level_count}는 최상위 폴더 수와 정확히 같아야 한다(2026-10-27).</p>
 */
@Component
public class XenonIngestClient implements LawIngestPort {

    private static final Logger logger = LogManager.getLogger(XenonIngestClient.class);

    private final HttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final int maxAttempts;
    private final Duration uploadTimeout;
    private final long retryDelayMillis;

    @Autowired
    public XenonIngestClient(
        @Value("${public-data.law-transfer.xenon.base-url}") String baseUrl,
        @Value("${public-data.law-transfer.xenon.api-key:}") String apiKey,
        @Value("${public-data.law-transfer.xenon.max-attempts:3}") int maxAttempts,
        @Value("${public-data.law-transfer.xenon.upload-timeout:10m}") Duration uploadTimeout
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
            baseUrl, apiKey, maxAttempts, uploadTimeout, 5_000L);
    }

    XenonIngestClient(HttpClient client, String baseUrl, String apiKey, int maxAttempts,
                      Duration uploadTimeout, long retryDelayMillis) {
        this.client = client;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.uploadTimeout = uploadTimeout;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public void ping() throws IngestException {
        requireApiKey();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/ping"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + apiKey)
            .GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new IngestException("Xenon ping 실패: HTTP " + response.statusCode());
        }
    }

    @Override
    public Receipt upload(ZipPart part) throws IngestException {
        requireApiKey();
        IngestException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Receipt receipt = uploadOnce(part);
                verify(part, receipt);
                return receipt;
            } catch (IngestException e) {
                last = e;
                logger.warn("[SEND] Xenon 전송 실패 part={} attempt={}/{}: {}", part.index(), attempt, maxAttempts, e.getMessage());
                if (!e.retryable()) {
                    break; // 요청 자체가 잘못됐다는 응답이라 같은 ZIP을 다시 올려도 같다(수십 MB를 헛되이 반복하지 않는다)
                }
                if (attempt < maxAttempts) {
                    sleep(retryDelayMillis * attempt);
                }
            }
        }
        throw last;
    }

    private Receipt uploadOnce(ZipPart part) throws IngestException {
        String boundary = "----law" + UUID.randomUUID().toString().replace("-", "");
        String fileName = part.file().getFileName().toString();
        // Xenon 규약: manifest 는 ZIP 에 넣지 않고 같은 요청의 'manifest' 파트로 보낸다(없으면 422 "manifest 가 없습니다").
        // 그 파트도 파일(UploadFile)이어야 해서 filename 을 붙인다 - 없으면 422 "Expected UploadFile, received: str".
        // 작은 필드를 먼저 보내고 ZIP 을 뒤에 붙인다.
        byte[] manifest = (part.manifestJson() == null ? "" : part.manifestJson()).getBytes(StandardCharsets.UTF_8);
        byte[] manifestHead = ("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"manifest\"; filename=\"manifest.json\"\r\n"
            + "Content-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] fileHead = ("\r\n--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
            + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] head = new byte[manifestHead.length + manifest.length + fileHead.length];
        System.arraycopy(manifestHead, 0, head, 0, manifestHead.length);
        System.arraycopy(manifest, 0, head, manifestHead.length, manifest.length);
        System.arraycopy(fileHead, 0, head, manifestHead.length + manifest.length, fileHead.length);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/law/ingest"))
            .timeout(uploadTimeout)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.fromPublisher(
                HttpRequest.BodyPublishers.ofInputStream(() -> multipartBody(part, head, tail)),
                head.length + part.sizeBytes() + tail.length)) // 길이를 알려 chunked 업로드를 피한다(게이트웨이 호환)
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            int code = response.statusCode();
            boolean clientError = code >= 400 && code < 500 && code != 408 && code != 429;
            throw new IngestException("Xenon 응답 HTTP " + code + ": " + abbreviate(response.body()), !clientError);
        }
        return parse(response.body());
    }

    private static InputStream multipartBody(ZipPart part, byte[] head, byte[] tail) {
        try {
            return new SequenceInputStream(Collections.enumeration(java.util.List.of(
                new java.io.ByteArrayInputStream(head),
                Files.newInputStream(part.file()),
                new java.io.ByteArrayInputStream(tail))));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static Receipt parse(String body) throws IngestException {
        try {
            JSONObject json = new JSONObject(body);
            JSONObject zip = json.optJSONObject("zip");
            if (zip == null) {
                throw new IngestException("응답에 zip 정보 없음: " + abbreviate(body));
            }
            return new Receipt(
                json.optBoolean("received", false),
                json.optString("filename", ""),
                json.optLong("size_bytes", -1),
                json.optString("sha256", ""),
                zip.optBoolean("valid", false),
                zip.optInt("entries", -1),
                zip.optInt("json", -1),
                zip.optInt("attachments", -1),
                zip.optInt("top_level_count", -1));
        } catch (JSONException e) {
            throw new IngestException("응답이 JSON이 아님: " + abbreviate(body), e);
        }
    }

    static void verify(ZipPart part, Receipt r) throws IngestException {
        if (!r.received()) {
            throw new IngestException("received=false");
        }
        if (!r.zipValid()) {
            throw new IngestException("zip.valid=false (ZIP이 손상됐다고 응답)");
        }
        if (!part.sha256().equalsIgnoreCase(r.sha256())) {
            throw new IngestException("sha256 불일치: 보냄=" + part.sha256() + " 응답=" + r.sha256());
        }
        if (part.sizeBytes() != r.sizeBytes()) {
            throw new IngestException("size_bytes 불일치: 보냄=" + part.sizeBytes() + " 응답=" + r.sizeBytes());
        }
        if (r.zipJson() != part.jsonCount()) {
            throw new IngestException("zip.json 불일치: 본문=" + part.jsonCount() + " 응답=" + r.zipJson());
        }
        if (r.topLevelCount() != part.topLevelFolders()) {
            throw new IngestException("zip.top_level_count 불일치: 폴더=" + part.topLevelFolders() + " 응답=" + r.topLevelCount());
        }
        logger.info("[SEND] Xenon 수신 확인 part={} size={} json={}(본문 {}) top_level={}(폴더 {}) attachments={}(보냄 {})",
            part.index(), r.sizeBytes(), r.zipJson(), part.jsonCount(), r.topLevelCount(), part.topLevelFolders(),
            r.zipAttachments(), part.attachmentCount());
    }

    private HttpResponse<String> send(HttpRequest request) throws IngestException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IngestException("Xenon 호출 실패: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IngestException("Xenon 호출 중단", e);
        }
    }

    private void requireApiKey() throws IngestException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IngestException("Xenon API 키가 설정되지 않음 (XENON_API_KEY)");
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 600 ? s : s.substring(0, 600) + "...";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
