package egovframework.external.publicdata.cleanser;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link JsonStructureDriftDetector}의 신규/누락 필드 감지 및 예외 격리 동작 검증.
 */
class JsonStructureDriftDetectorTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final JsonStructureDriftDetector detector = new JsonStructureDriftDetector(meterRegistry);

    @Test
    void 프로브가_없으면_아무_것도_안_한다() {
        PublicDataCleanser cleanser = Mockito.mock(PublicDataCleanser.class);
        // structureProbes()는 default 구현 그대로(List.of()) - 별도 스텁 없음

        assertThatCode(() -> detector.check(cleanser, "some-key", "[]")).doesNotThrowAnyException();
        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    @Test
    void 알려진_필드에_없는_키가_관찰되면_ADDED로_메트릭을_남긴다() {
        StructureProbe probe = new StructureProbe("item", Set.of("a", "b"), Set.of("a", "b"),
            StructureProbeSupport::unionKeys);
        PublicDataCleanser cleanser = fixedProbeCleanser(probe);

        String raw = new JSONArray().put(new JSONObject().put("a", 1).put("b", 2).put("c", 3)).toString();
        detector.check(cleanser, "op", raw);

        assertThat(meterRegistry.get("public_data_cleanse_structure_drift_total")
            .tag("operationKey", "op").tag("label", "item").tag("driftType", "ADDED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 필수_필드가_관찰안되면_MISSING으로_메트릭을_남긴다() {
        StructureProbe probe = new StructureProbe("item", Set.of("a", "b"), Set.of("a", "b"),
            StructureProbeSupport::unionKeys);
        PublicDataCleanser cleanser = fixedProbeCleanser(probe);

        String raw = new JSONArray().put(new JSONObject().put("a", 1)).toString(); // b 없음

        detector.check(cleanser, "op", raw);

        assertThat(meterRegistry.get("public_data_cleanse_structure_drift_total")
            .tag("operationKey", "op").tag("label", "item").tag("driftType", "MISSING")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 선택_필드만_빠지고_필수_필드가_다_있으면_아무_경고도_없다() {
        // requiredFields에는 "a"만, knownFields에는 "a","b"(선택) - b가 없어도 MISSING 아님
        StructureProbe probe = new StructureProbe("item", Set.of("a", "b"), Set.of("a"),
            StructureProbeSupport::unionKeys);
        PublicDataCleanser cleanser = fixedProbeCleanser(probe);

        String raw = new JSONArray().put(new JSONObject().put("a", 1)).toString();
        detector.check(cleanser, "op", raw);

        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    @Test
    void 프로브_관찰_중_예외가_나도_전체_검사는_안_죽는다() {
        StructureProbe badProbe = new StructureProbe("bad", Set.of(), Set.of(),
            items -> { throw new RuntimeException("일부러 터뜨림"); });
        StructureProbe okProbe = new StructureProbe("ok", Set.of("a"), Set.of("a"), StructureProbeSupport::unionKeys);
        PublicDataCleanser cleanser = fixedProbeCleanser(badProbe, okProbe);

        String raw = new JSONArray().put(new JSONObject().put("a", 1).put("x", 1)).toString();

        assertThatCode(() -> detector.check(cleanser, "op", raw)).doesNotThrowAnyException();
        // ok 프로브는 정상적으로 계속 처리돼 ADDED가 남아야 함("x"가 새 필드)
        assertThat(meterRegistry.get("public_data_cleanse_structure_drift_total")
            .tag("operationKey", "op").tag("label", "ok").tag("driftType", "ADDED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void rawPayload_자체가_JSON이_아니어도_예외를_던지지_않는다() {
        StructureProbe probe = new StructureProbe("item", Set.of("a"), Set.of("a"), StructureProbeSupport::unionKeys);
        PublicDataCleanser cleanser = fixedProbeCleanser(probe);

        assertThatCode(() -> detector.check(cleanser, "op", "이건 JSON이 아님")).doesNotThrowAnyException();
    }

    private PublicDataCleanser fixedProbeCleanser(StructureProbe... probes) {
        return new PublicDataCleanser() {
            @Override
            public boolean supports(String operationKey) {
                return true;
            }

            @Override
            public String cleanse(String rawPayload) {
                return rawPayload;
            }

            @Override
            public List<StructureProbe> structureProbes() {
                return List.of(probes);
            }
        };
    }
}
