package egovframework.external.rule;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.publicdata.cleanser.CleansedJsonDropWriter;
import egovframework.external.staging.InMemoryRawStagingStore;
import egovframework.external.staging.RawStagingStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuleEvaluationService} 종단 검증 - 실제 {@link InMemoryRawStagingStore}에 CLEANSED
 * 행을 넣고 raw_staging → 정적 취약도(facility-vulnerability.csv) → 최종 등급까지 전체 경로가
 * 맞물려 동작하는지 확인한다. JSON drop은 비활성화(enabled=false)로 파일 IO 없이 테스트.
 */
class RuleEvaluationServiceTest {

    // 1272038 영월교도소: landslideVuln=HIGH, floodVuln=LOW (facility-vulnerability.csv 실측)
    private static final String YEONGWOL = "1272038";

    private RawStagingStore store;
    private RuleEvaluationService service;

    private void setUp() {
        store = new InMemoryRawStagingStore();
        CleansedJsonDropWriter disabledWriter = new CleansedJsonDropWriter(false, "unused");
        service = new RuleEvaluationService(store, new FacilityVulnerabilityLoader(),
            new FacilitySidoLoader(), new KmaWarningStationLoader(), disabledWriter);
    }

    private void insertCleansed(String operationKey, String facilityId, String collectorKey, String cleansedPayload) {
        RawStagingDto dto = RawStagingDto.builder()
            .sourceName("test").apiName("test")
            .operationKey(operationKey).facilityId(facilityId).collectorKey(collectorKey)
            .rawPayload("[]")
            .build();
        store.insert(dto);
        store.markCleansed(dto.getId(), cleansedPayload, null);
    }

    @Test
    void 정제된_데이터가_없으면_59개소x2재해_118건이_전부_NONE으로_평가된다() {
        setUp();

        List<AlertResult> results = service.evaluateAll();

        assertThat(results).hasSize(118);
        assertThat(results).allSatisfy(r -> assertThat(r.level()).isEqualTo(AlertLevel.NONE));
    }

    @Test
    void 강수량_트리거가_기관의_취약도와_결합되어_등급을_낸다() {
        setUp();
        // 3시간 누적 90mm(호우경보 기준) - 단기예보(pcp) 정제 결과 형태
        String weather = "["
            + "{\"fcstDate\":\"20260818\",\"fcstTime\":\"1200\",\"pcp\":\"30.0mm\"},"
            + "{\"fcstDate\":\"20260818\",\"fcstTime\":\"1300\",\"pcp\":\"30.0mm\"},"
            + "{\"fcstDate\":\"20260818\",\"fcstTime\":\"1400\",\"pcp\":\"30.0mm\"}]";
        insertCleansed("kma-village-forecast-vilage-fcst", YEONGWOL,
            "kma-village-forecast-vilage-fcst--" + YEONGWOL, weather);

        List<AlertResult> results = service.evaluateAll();

        AlertResult landslide = findResult(results, YEONGWOL, HazardType.LANDSLIDE);
        assertThat(landslide.weatherTrigger()).isEqualTo(WeatherTrigger.RAIN_ALERT);
        assertThat(landslide.vulnerability()).isEqualTo(VulnerabilityGrade.HIGH);
        assertThat(landslide.regionTriggered()).isFalse();
        assertThat(landslide.level()).isEqualTo(AlertLevel.SEVERE); // HIGH x RAIN_ALERT

        AlertResult flood = findResult(results, YEONGWOL, HazardType.FLOOD);
        assertThat(flood.vulnerability()).isEqualTo(VulnerabilityGrade.LOW);
        assertThat(flood.level()).isEqualTo(AlertLevel.CAUTION); // LOW x RAIN_ALERT
    }

    @Test
    void 재난문자_지역신호가_해당_재해만_한단계_상향시킨다() {
        setUp();
        String disasterMsg = "[{\"sn\":1,\"facilityId\":\"" + YEONGWOL + "\",\"dstSeNm\":\"산사태\"}]";
        insertCleansed("safetydata-disaster-msg-list", null,
            "safetydata-disaster-msg-list", disasterMsg);

        List<AlertResult> results = service.evaluateAll();

        AlertResult landslide = findResult(results, YEONGWOL, HazardType.LANDSLIDE);
        assertThat(landslide.regionTriggered()).isTrue();
        assertThat(landslide.weatherTrigger()).isEqualTo(WeatherTrigger.NONE);
        assertThat(landslide.level()).isEqualTo(AlertLevel.INFO); // 없음 -> 한단계 상향

        // "산사태"는 FLOOD로 매핑되지 않으므로 침수 쪽은 영향 없어야 함
        AlertResult flood = findResult(results, YEONGWOL, HazardType.FLOOD);
        assertThat(flood.regionTriggered()).isFalse();
        assertThat(flood.level()).isEqualTo(AlertLevel.NONE);
    }

    @Test
    void 기상특보_호우주의보가_활성이면_같은_시도_시설의_침수만_상향된다() {
        // 105(강릉)의 관할구역은 강원특별자치도 - 영월교도소가 여기 속함
        setUp();
        String warning = "[{\"stnId\":\"105\",\"title\":\"[특보] 제08-1호 : 2026.08.18.09:00 / 호우주의보 발표 (*)\","
            + "\"tmFc\":202608180900,\"tmSeq\":1}]";
        insertCleansed("kma-weather-warning-list", null, "kma-weather-warning-list", warning);

        List<AlertResult> results = service.evaluateAll();

        AlertResult flood = findResult(results, YEONGWOL, HazardType.FLOOD);
        assertThat(flood.regionTriggered()).isTrue();
        assertThat(flood.regionSources()).containsExactly("weatherWarning");
        assertThat(flood.level()).isEqualTo(AlertLevel.INFO); // LOW x NONE -> 없음, 지역신호로 한단계 상향

        // 산사태는 KMA 특보 현상이 아니므로 기상특보로는 트리거되지 않는다
        AlertResult landslide = findResult(results, YEONGWOL, HazardType.LANDSLIDE);
        assertThat(landslide.regionTriggered()).isFalse();
    }

    @Test
    void 발표보다_늦은_해제가_있으면_기상특보_트리거는_비활성이다() {
        setUp();
        // 발표(09:00) 이후 해제(11:00) - 최신 것(해제)만 유효해야 함
        String warning = "["
            + "{\"stnId\":\"105\",\"title\":\"[특보] 제08-1호 : 2026.08.18.09:00 / 호우주의보 발표 (*)\",\"tmFc\":202608180900,\"tmSeq\":1},"
            + "{\"stnId\":\"105\",\"title\":\"[특보] 제08-2호 : 2026.08.18.11:00 / 호우주의보 해제 (*)\",\"tmFc\":202608181100,\"tmSeq\":2}]";
        insertCleansed("kma-weather-warning-list", null, "kma-weather-warning-list", warning);

        List<AlertResult> results = service.evaluateAll();

        assertThat(findResult(results, YEONGWOL, HazardType.FLOOD).regionTriggered()).isFalse();
    }

    @Test
    void 재난문자와_기상특보가_동시에_감지되면_출처가_둘_다_기록된다() {
        setUp();
        String warning = "[{\"stnId\":\"105\",\"title\":\"[특보] 제08-1호 : 2026.08.18.09:00 / 호우주의보 발표 (*)\","
            + "\"tmFc\":202608180900,\"tmSeq\":1}]";
        insertCleansed("kma-weather-warning-list", null, "kma-weather-warning-list", warning);
        String disasterMsg = "[{\"sn\":1,\"facilityId\":\"" + YEONGWOL + "\",\"dstSeNm\":\"호우\"}]";
        insertCleansed("safetydata-disaster-msg-list", null, "safetydata-disaster-msg-list", disasterMsg);

        List<AlertResult> results = service.evaluateAll();

        AlertResult flood = findResult(results, YEONGWOL, HazardType.FLOOD);
        assertThat(flood.regionSources()).containsExactlyInAnyOrder("disasterMsg", "weatherWarning");
        assertThat(flood.level()).isEqualTo(AlertLevel.INFO); // 출처가 여럿이어도 상향은 한 단계뿐
    }

    @Test
    void 정제기_없는_operationKey는_조용히_무시된다() {
        setUp();
        insertCleansed("kma-weather-warning-list", null, "kma-weather-warning-list", "[]");

        List<AlertResult> results = service.evaluateAll();

        assertThat(results).hasSize(118);
        assertThat(results).allSatisfy(r -> assertThat(r.level()).isEqualTo(AlertLevel.NONE));
    }

    private AlertResult findResult(List<AlertResult> results, String facilityId, HazardType hazard) {
        Optional<AlertResult> found = results.stream()
            .filter(r -> r.facilityId().equals(facilityId) && r.hazardType() == hazard)
            .findFirst();
        assertThat(found).isPresent();
        return found.get();
    }
}
