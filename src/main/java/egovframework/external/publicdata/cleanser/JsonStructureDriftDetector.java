package egovframework.external.publicdata.cleanser;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 정제기가 {@link PublicDataCleanser#structureProbes()}로 선언해둔 "알고 있는 필드 구조"와
 * 실제 rawPayload를 대조해서, 우리가 모르는 새 필드가 생겼거나 있어야 할 필드가 없어졌으면
 * 로그(+메트릭)로 남기는 보조 기능.
 *
 * <p>목적은 "공공데이터포털/법제처 API가 스펙 공지 없이 응답 구조를 바꿨을 때, 정제기가 그
 * 변화를 모른 채 조용히 일부 데이터를 놓치거나 엉뚱하게 처리하는 걸 막는 것" - 정제 자체를
 * 막거나 실패시키진 않는다(경고만 남김). 검사 로직 자체가 실패해도 정제 파이프라인 본체에는
 * 영향이 없어야 하므로 예외를 절대 밖으로 던지지 않는다.</p>
 */
@Component
public class JsonStructureDriftDetector {

    private static final Logger logger = LogManager.getLogger(JsonStructureDriftDetector.class);

    private final MeterRegistry meterRegistry;

    public JsonStructureDriftDetector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 정제 성공/실패와 무관하게 매 시도마다 호출 - 실패 시 왜 실패했는지 단서가 될 수도 있어서. */
    public void check(PublicDataCleanser cleanser, String operationKey, String rawPayload) {
        List<StructureProbe> probes = cleanser.structureProbes();
        if (probes.isEmpty()) {
            return;
        }
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            for (StructureProbe probe : probes) {
                checkOne(operationKey, probe, rawItems);
            }
        } catch (Exception e) {
            logger.warn("[STRUCTURE-DRIFT] 검사 자체 실패(정제 결과에는 영향 없음): operationKey={}, error={}",
                operationKey, e.getMessage());
        }
    }

    private void checkOne(String operationKey, StructureProbe probe, JSONArray rawItems) {
        Set<String> observed;
        try {
            observed = probe.observer().apply(rawItems);
        } catch (Exception e) {
            logger.warn("[STRUCTURE-DRIFT] 프로브 관찰 실패(정제 결과에는 영향 없음): operationKey={}, label={}, error={}",
                operationKey, probe.label(), e.getMessage());
            return;
        }

        Set<String> added = new LinkedHashSet<>(observed);
        added.removeAll(probe.knownFields());
        if (!added.isEmpty()) {
            logger.warn("[STRUCTURE-DRIFT][NEW-FIELD] operationKey={} label={} 새 필드 발견={} - 정제기 구조 갱신 검토 필요",
                operationKey, probe.label(), added);
            recordDrift(operationKey, probe.label(), "ADDED");
        }

        Set<String> missing = new LinkedHashSet<>(probe.requiredFields());
        missing.removeAll(observed);
        if (!missing.isEmpty()) {
            logger.warn("[STRUCTURE-DRIFT][MISSING-FIELD] operationKey={} label={} 예상 필드 없음={} - API 응답 구조가 바뀌었을 수 있음",
                operationKey, probe.label(), missing);
            recordDrift(operationKey, probe.label(), "MISSING");
        }
    }

    private void recordDrift(String operationKey, String label, String driftType) {
        Counter.builder("public_data_cleanse_structure_drift_total")
            .description("정제 원본 JSON 구조 드리프트(신규/누락 필드) 감지 건수")
            .tag("operationKey", operationKey)
            .tag("label", label)
            .tag("driftType", driftType)
            .register(meterRegistry)
            .increment();
    }
}
