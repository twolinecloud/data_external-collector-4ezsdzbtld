package egovframework.external.publicdata.transfer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "이 문서의 이 내용을 이미 Xenon에 보냈다"는 기록 - 매일 전체(약 400MB, 첨부 다운로드 약 7천 회)를 다시 보내지 않고
 * 바뀐 문서만 보내기 위한 것이다. 키는 {@link LawDocument#key()}, 값은 본문 JSON의 sha256.
 *
 * <p><b>기본은 메모리 전용</b>이라 파드가 재시작(배포 포함)되면 비고, 다음 수집에서 전체를 한 번 다시 보낸다 - 규약이
 * 중복 전송을 무해하다고 하므로 정합성 문제는 없고 비용만 든다. {@code public-data.law-transfer.ledger-file}에 영속 볼륨
 * 경로를 주면 파일로 이어받는다. 보냄 기록은 Xenon의 수신 확인(sha256 대조)이 끝난 뒤에만 남긴다.</p>
 */
@Component
public class LawTransferLedger {

    private static final Logger logger = LogManager.getLogger(LawTransferLedger.class);

    private final Map<String, String> sent = new ConcurrentHashMap<>();
    private final Path file;

    public LawTransferLedger(@Value("${public-data.law-transfer.ledger-file:}") String ledgerFile) {
        this.file = ledgerFile == null || ledgerFile.isBlank() ? null : Path.of(ledgerFile);
        load();
    }

    public boolean isUnchanged(String key, String fingerprint) {
        return fingerprint.equals(sent.get(key));
    }

    public void record(String key, String fingerprint) {
        sent.put(key, fingerprint);
    }

    public int size() {
        return sent.size();
    }

    /** 한 ZIP의 전송이 확인될 때마다 부른다 - 중간에 파드가 죽어도 이미 보낸 것은 다시 보내지 않게. */
    public synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, new JSONObject(sent).toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            // 기록 실패는 전송을 막지 않는다 - 최악의 경우 다음에 한 번 더 보낼 뿐이다.
            logger.warn("[SEND] 전송 기록 파일 저장 실패 (전송 자체는 정상): file={}, error={}", file, e.getMessage());
        }
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            JSONObject json = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            for (String key : json.keySet()) {
                sent.put(key, json.getString(key));
            }
            logger.info("[SEND] 전송 기록 {}건 이어받음: {}", sent.size(), file);
        } catch (IOException | RuntimeException e) {
            logger.warn("[SEND] 전송 기록 파일을 읽지 못해 비어 있는 것으로 시작: file={}, error={}", file, e.getMessage());
        }
    }
}
