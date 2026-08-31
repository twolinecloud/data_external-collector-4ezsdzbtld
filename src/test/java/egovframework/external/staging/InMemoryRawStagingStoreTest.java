package egovframework.external.staging;

import egovframework.external.dto.RawStagingDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InMemoryRawStagingStore}의 operationKey 필터링 단위 테스트 - EXTERNAL_PUBLIC/
 * EXTERNAL_LAW 배치 분리(2026-08-27)의 실제 조회 로직을 검증한다. 클라이언트 쪽에서
 * 가져온 뒤 건너뛰는 방식이 아니라 조회 시점에 걸러야 하는 이유(무한루프 방지)도 여기서
 * 같이 확인 - {@link RawStagingStore#findByStatus(String, int, Set, boolean)} 참고.
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

    private RawStagingDto dto(String operationKey) {
        return RawStagingDto.builder()
            .sourceName("소스")
            .apiName("API")
            .operationKey(operationKey)
            .rawPayload("[]")
            .build();
    }
}
