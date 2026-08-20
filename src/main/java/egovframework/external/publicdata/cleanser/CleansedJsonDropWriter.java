package egovframework.external.publicdata.cleanser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 정제(Cleanse) 완료 직후의 payload를 로컬 파일로 "떨어뜨리는"(drop) 디버그/뷰어 전용 부가 기능.
 *
 * <p>admin-db 적재 경로가 아직 없어(private-doc 21번 항목) 정제 결과는 raw_staging(인메모리)
 * 안에만 있고, 밖에서 확인할 방법이 없었다 - 이 클래스는 그 결과를 로컬 파일로 즉시 내려받아
 * (예: 겸용 HTML 뷰어) 눈으로 확인할 수 있게 하는 보조 장치다. {@code public-data.cleanse.json-drop.enabled}
 * 기본값은 {@code false}라 켜지 않으면 아무 것도 하지 않음 - 운영 동작에 영향 없음.</p>
 *
 * <p>{@code collectorKey}(= {@code PublicDataCollector.key()}, 컬렉터 인스턴스 1개당 유일)별로
 * 파일 하나에 최신 결과만 덮어쓴다(누적 안 함) - "지금 이 순간의 정제 결과"를 보는 디버그 용도지
 * 이력 보존 목적이 아니기 때문. {@code operationKey}만 쓰지 않는 이유: 법령처럼 여러 인스턴스가
 * operationKey를 공유하면서 facilityId도 없는 소스(44건 전부 "moleg-criminal-law")는
 * operationKey만으로 파일을 나누면 서로 덮어써버림 - collectorKey는 그런 경우에도 항상 유일함
 * (예: "moleg-criminal-law--001692"). 파일 쓰기 실패는 로그만 남기고 삼킨다 - 이 보조기능 때문에
 * 정제 파이프라인 본체가 죽으면 안 되므로.</p>
 */
@Component
public class CleansedJsonDropWriter {

    private static final Logger logger = LogManager.getLogger(CleansedJsonDropWriter.class);

    private final boolean enabled;
    private final Path dir;

    public CleansedJsonDropWriter(
        @Value("${public-data.cleanse.json-drop.enabled:false}") boolean enabled,
        @Value("${public-data.cleanse.json-drop.dir:private-doc/cleansed-json-drop}") String dir
    ) {
        this.enabled = enabled;
        this.dir = Path.of(dir);
    }

    /** 정제 성공 시 호출. {@code enabled=false}면 아무 것도 하지 않는다. */
    public void write(String collectorKey, String cleansedPayload) {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(collectorKey + ".json");
            Files.writeString(target, cleansedPayload, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            logger.warn("[CLEANSE][JSON-DROP] 파일 쓰기 실패 (정제 자체는 정상 처리됨): collectorKey={}, error={}",
                collectorKey, e.getMessage());
        }
    }
}
