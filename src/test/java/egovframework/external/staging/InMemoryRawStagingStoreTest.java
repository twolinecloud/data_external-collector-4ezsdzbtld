package egovframework.external.staging;

import egovframework.external.dto.RawStagingDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InMemoryRawStagingStore}의 operationKey 필터링 단위 테스트 - EXTERNAL_PUBLIC/
 * EXTERNAL_LAW 배치 분리(2026-08-27)의 실제 조회 로직을 검증한다. 클라이언트 쪽에서
 * 가져온 뒤 건너뛰는 방식이 아니라 조회 시점에 걸러야 하는 이유(무한루프 방지)도 여기서
 * 같이 확인 - {@link RawStagingStore#findByStatus(String, int, Set, boolean)} 참고.
 *
 * <p>2026-09-02부터 {@link InMemoryRawStagingStore#insert}의 보관 규칙도 여기서 검증한다 -
 * 수집 케이스(법령/예보/재난문자)마다 종결 행 회수 결과가 어떻게 갈리는지, 적재가 밀려 있는
 * 행은 어떤 경우에도 회수되지 않는지, 그리고 유효기간을 밝힌 소스(기상청)의 만료 폐기가
 * 회수 규칙이 닿지 않는 자리를 어떻게 메우는지.</p>
 */
class InMemoryRawStagingStoreTest {

    private final InMemoryRawStagingStore store = new InMemoryRawStagingStore();

    @Test
    void operationKeys가_비어있으면_상태만_맞으면_전부_반환한다() {
        store.insert(dto("moleg-criminal-law"));
        store.insert(dto("kma-weather-warning-list"));

        List<RawStagingDto> result = store.findByStatus("COLLECTED", 100, Set.of(), false);

        assertThat(result).hasSize(2);
    }

    @Test
    void exclude_false면_operationKeys에_속하는_것만_반환한다() {
        store.insert(dto("moleg-criminal-law"));
        store.insert(dto("kma-weather-warning-list"));
        store.insert(dto("safetydata-disaster-msg-list"));

        List<RawStagingDto> result = store.findByStatus("COLLECTED", 100, Set.of("moleg-criminal-law"), false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperationKey()).isEqualTo("moleg-criminal-law");
    }

    @Test
    void exclude_true면_operationKeys에_속하지_않는_것만_반환한다() {
        store.insert(dto("moleg-criminal-law"));
        store.insert(dto("kma-weather-warning-list"));
        store.insert(dto("safetydata-disaster-msg-list"));

        List<RawStagingDto> result = store.findByStatus("COLLECTED", 100, Set.of("moleg-criminal-law"), true);

        assertThat(result).hasSize(2)
            .extracting(RawStagingDto::getOperationKey)
            .containsExactlyInAnyOrder("kma-weather-warning-list", "safetydata-disaster-msg-list");
    }

    @Test
    void 적재_실패는_시도_횟수를_1씩_올리고_LOAD_FAILED로_남긴다() {
        RawStagingDto dto = dto("safetydata-disaster-msg-list");
        store.insert(dto);

        store.markLoadFailed(dto.getId(), "컬럼 길이 초과");
        assertThat(dto.getStatus()).isEqualTo("LOAD_FAILED");
        assertThat(dto.getLoadAttemptCount()).isEqualTo(1);

        store.markLoadFailed(dto.getId(), "또 실패");
        assertThat(dto.getLoadAttemptCount()).isEqualTo(2);
        assertThat(dto.getLoadFailureLog()).isEqualTo("또 실패");
    }

    @Test
    void 포기한_행은_LOAD_ABANDONED가_되어_재시도_조회에서_빠진다() {
        RawStagingDto dto = dto("safetydata-disaster-msg-list");
        store.insert(dto);
        store.markLoadFailed(dto.getId(), "1차 실패");

        assertThat(store.findByStatus("LOAD_FAILED", 100, Set.of(), false)).hasSize(1);

        store.markLoadAbandoned(dto.getId(), "한도 소진");

        // 상태값을 분리한 목적 - 조회에서 자연스럽게 빠져야 적재 루프가 같은 행을 계속
        // 붙들지 않는다(RawStagingStore#markLoadAbandoned 주석 참고).
        assertThat(store.findByStatus("LOAD_FAILED", 100, Set.of(), false)).isEmpty();
        assertThat(dto.getStatus()).isEqualTo("LOAD_ABANDONED");
        assertThat(dto.getLoadAttemptCount()).isEqualTo(2);
    }

    @Test
    void 상태가_다르면_필터와_무관하게_제외된다() {
        RawStagingDto dto = dto("moleg-criminal-law");
        store.insert(dto);
        store.markCleansed(dto.getId(), "[]", null); // status: COLLECTED -> CLEANSED

        List<RawStagingDto> result = store.findByStatus("COLLECTED", 100, Set.of("moleg-criminal-law"), false);

        assertThat(result).isEmpty();
    }

    // --- 종결 행 회수 (2026-09-02) - InMemoryRawStagingStore#insert 주석의 수집 케이스 3종 ---

    @Test
    void 법령_재수집은_포기된_이전_행을_회수해_한_행만_남긴다() {
        RawStagingDto 어제 = dto("moleg-criminal-law", "moleg-criminal-law--001692");
        store.insert(어제);
        store.markLoadFailed(어제.getId(), "적재기 없음");
        store.markLoadAbandoned(어제.getId(), "한도 소진");

        store.insert(dto("moleg-criminal-law", "moleg-criminal-law--001692"));

        // 며칠이 지나도 최종적인 하나만 남아야 한다 - 이전 행은 회수되어 조회에서 사라진다.
        assertThat(store.findByStatus("LOAD_ABANDONED", 100, Set.of(), false)).isEmpty();
        assertThat(store.findByStatus("COLLECTED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void 적재_대상이_아닌_법령은_LOAD_SKIPPED로_종결되어_다음_수집에_회수된다() {
        RawStagingDto 어제 = dto("moleg-criminal-law", "moleg-criminal-law--001692");
        store.insert(어제);
        store.markLoadSkipped(어제.getId(), "적재기 없음: operationKey=moleg-criminal-law");

        store.insert(dto("moleg-criminal-law", "moleg-criminal-law--001692"));

        // 적재 합의 전 채널이라 실패가 아니라 종결이고, 종결이라서 다음 수집이 자리를 회수한다.
        // 이게 안 되면 법제처 행이 CLEANSED로 남아 하루 491건씩 다시 쌓인다.
        assertThat(store.findByStatus("LOAD_SKIPPED", 100, Set.of(), false)).isEmpty();
        assertThat(store.findByStatus("COLLECTED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void 건너뛴_행은_적재_시도_횟수를_올리지_않는다() {
        RawStagingDto dto = dto("moleg-criminal-law", "moleg-criminal-law--001692");
        store.insert(dto);

        store.markLoadSkipped(dto.getId(), "적재기 없음");

        assertThat(dto.getStatus()).isEqualTo("LOAD_SKIPPED");
        assertThat(dto.getLoadAttemptCount()).isZero();
        // 재시도 조회에도 잡히면 안 된다.
        assertThat(store.findByStatus("LOAD_FAILED", 100, Set.of(), false)).isEmpty();
    }

    @Test
    void 예보는_적재된_이전_발표시각_행을_회수한다() {
        RawStagingDto 이전시각 = dto("kma-village-forecast-ultra-srt-ncst", "kma-...-ncst--1270280");
        store.insert(이전시각);
        store.markLoaded(이전시각.getId());

        store.insert(dto("kma-village-forecast-ultra-srt-ncst", "kma-...-ncst--1270280"));

        // 시각별 이력은 최종 테이블 (facility_id, base_dtm)이 들고 있으므로 staging엔 최신 1행만.
        assertThat(store.findByStatus("LOADED", 100, Set.of(), false)).isEmpty();
        assertThat(store.findByStatus("COLLECTED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void 재난문자는_적재가_끝난_이전_배치만_회수한다() {
        String collectorKey = "safetydata-disaster-msg-list";
        RawStagingDto 이전배치 = dto("safetydata-disaster-msg-list", collectorKey);
        store.insert(이전배치);
        store.markLoaded(이전배치.getId());

        store.insert(dto("safetydata-disaster-msg-list", collectorKey));

        // 문자 자체는 최종 테이블에 (sn, facility_id)로 쌓이고, staging엔 최신 배치만 남는다.
        assertThat(store.findByStatus("LOADED", 100, Set.of(), false)).isEmpty();
        assertThat(store.findByStatus("COLLECTED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void 적재_지연으로_아직_처리중인_행은_회수하지_않는다() {
        String collectorKey = "safetydata-disaster-msg-list";
        RawStagingDto 밀린배치 = dto("safetydata-disaster-msg-list", collectorKey);
        store.insert(밀린배치);
        store.markCleansed(밀린배치.getId(), "[]", null);

        store.insert(dto("safetydata-disaster-msg-list", collectorKey));

        // 미적재분을 덮으면 그 배치가 조용히 유실되므로, 잠깐 두 행으로 늘어나는 쪽을 택한다.
        assertThat(store.findByStatus("CLEANSED", 100, Set.of(), false)).hasSize(1);
        assertThat(store.findByStatus("COLLECTED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void 실패한_적재를_재시도하는_중에도_회수하지_않는다() {
        String collectorKey = "kma-weather-warning-list";
        RawStagingDto 재시도중 = dto("kma-weather-warning-list", collectorKey);
        store.insert(재시도중);
        store.markLoadFailed(재시도중.getId(), "1차 실패");

        store.insert(dto("kma-weather-warning-list", collectorKey));

        // LOAD_FAILED는 다음 주기 재시도 대상이므로 종결이 아니다.
        assertThat(store.findByStatus("LOAD_FAILED", 100, Set.of(), false)).hasSize(1);
        assertThat(store.findByStatus("COLLECTED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void collectorKey가_다르면_서로_회수하지_않는다() {
        RawStagingDto 다른법령 = dto("moleg-criminal-law", "moleg-criminal-law--001692");
        store.insert(다른법령);
        store.markLoaded(다른법령.getId());

        store.insert(dto("moleg-criminal-law", "moleg-criminal-law--002761"));

        // 같은 operationKey를 공유해도 수집 단위가 다르면 남의 행을 지우면 안 된다.
        assertThat(store.findByStatus("LOADED", 100, Set.of(), false)).hasSize(1);
        assertThat(store.findByStatus("COLLECTED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void collectorKey가_없으면_회수하지_않는다() {
        RawStagingDto 이전 = dto("moleg-criminal-law");
        store.insert(이전);
        store.markLoaded(이전.getId());

        store.insert(dto("moleg-criminal-law"));

        // 회수 대상을 특정할 수 없으므로 예전처럼 그냥 쌓인다.
        assertThat(store.findByStatus("LOADED", 100, Set.of(), false)).hasSize(1);
        assertThat(store.findByStatus("COLLECTED", 100, Set.of(), false)).hasSize(1);
    }

    // --- 유효기간 만료 (2026-09-02) - 기상 데이터는 날짜 기준으로 어제 0시 이후만 유효하다.
    //     만료 판정 자체는 expiresAt 하나로 하므로 여기서는 경계 전/후만 본다(날짜 계산은
    //     KmaLocationCollectorFactoryTest가 검증). ---

    @Test
    void 기한이_지난_행은_적재_전이라도_폐기된다() {
        RawStagingDto 어제예보 = dto("kma-village-forecast-vilage-fcst", "kma-...-fcst--1270280");
        어제예보.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        store.insert(어제예보);
        store.markCleansed(어제예보.getId(), "[]", null);

        // 다른 소스의 수집이 들어와도 만료 폐기는 collectorKey를 가리지 않고 돈다.
        store.insert(dto("safetydata-disaster-msg-list", "safetydata-disaster-msg-list"));

        assertThat(store.findByStatus("CLEANSED", 100, Set.of(), false)).isEmpty();
    }

    @Test
    void 기한이_남은_행은_적재가_밀려도_재시도_대기로_남는다() {
        RawStagingDto 어제예보 = dto("kma-village-forecast-vilage-fcst", "kma-...-fcst--1270280");
        어제예보.setExpiresAt(LocalDate.now().plusDays(1).atStartOfDay());
        store.insert(어제예보);
        store.markLoadFailed(어제예보.getId(), "일시적 장애");

        store.insert(dto("safetydata-disaster-msg-list", "safetydata-disaster-msg-list"));

        // 어제 0시 이후 수집분은 적재에 실패해도 메모리에 들고 있어야 한다.
        assertThat(store.findByStatus("LOAD_FAILED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void 기한이_없는_소스는_적재가_밀려도_폐기되지_않는다() {
        RawStagingDto 재난문자 = dto("safetydata-disaster-msg-list", "safetydata-disaster-msg-list");
        store.insert(재난문자);
        store.markLoadFailed(재난문자.getId(), "일시적 장애");

        store.insert(dto("moleg-criminal-law", "moleg-criminal-law--001692"));

        // expiresAt이 null인 소스(법령/재난문자)는 기한 폐기 대상이 아니다 - 재시도가 생명이라서.
        assertThat(store.findByStatus("LOAD_FAILED", 100, Set.of(), false)).hasSize(1);
    }

    @Test
    void 수집이_끊긴_소스의_만료된_행도_정리된다() {
        RawStagingDto 사라진시설 = dto("kma-village-forecast-vilage-fcst", "kma-...-fcst--없어진시설");
        사라진시설.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        store.insert(사라진시설);
        store.markLoaded(사라진시설.getId());

        // 이 collectorKey로는 다시는 수집이 안 들어오므로, 회수 규칙만으로는 영영 남는다.
        store.insert(dto("kma-village-forecast-vilage-fcst", "kma-...-fcst--1270280"));

        assertThat(store.findByStatus("LOADED", 100, Set.of(), false)).isEmpty();
    }

    private RawStagingDto dto(String operationKey) {
        return dto(operationKey, null);
    }

    private RawStagingDto dto(String operationKey, String collectorKey) {
        return RawStagingDto.builder()
            .sourceName("소스")
            .apiName("API")
            .operationKey(operationKey)
            .collectorKey(collectorKey)
            .rawPayload("[]")
            .build();
    }
}
