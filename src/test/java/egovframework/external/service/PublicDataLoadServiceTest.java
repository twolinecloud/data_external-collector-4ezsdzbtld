package egovframework.external.service;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.model.LoadResult;
import egovframework.external.publicdata.loader.PublicDataLoader;
import egovframework.external.publicdata.loader.PublicDataLoaderRegistry;
import egovframework.external.staging.RawStagingStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PublicDataLoadService}의 오케스트레이션 단위 테스트.
 * {@code PublicDataCleanseServiceTest}와 대칭 - enabled=false일 때의 전면 no-op 가드도 검증.
 */
@ExtendWith(MockitoExtension.class)
class PublicDataLoadServiceTest {

    private static final String OPERATION_KEY = "kma-village-forecast-vilage-fcst";

    @Mock
    private RawStagingStore rawStagingStore;

    @Mock
    private PublicDataLoaderRegistry loaderRegistry;

    @Mock
    private PublicDataLoader loader;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private static final int MAX_ATTEMPTS = 3;

    /**
     * 재시도 대상이 없는 상태를 명시. 서비스가 CLEANSED보다 LOAD_FAILED를 먼저 조회하므로,
     * 이 스텁이 없으면 Mockito strict stub이 "CLEANSED 스텁을 두고 다른 인자로 호출했다"며
     * PotentialStubbingProblem을 던진다.
     */
    private void noRetryBacklog() {
        when(rawStagingStore.findByStatus("LOAD_FAILED", 100, Set.of(), false)).thenReturn(List.of());
    }

    private PublicDataLoadService service(boolean enabled) {
        return new PublicDataLoadService(rawStagingStore, loaderRegistry, meterRegistry, enabled, MAX_ATTEMPTS);
    }

    @Test
    void enabled가_false면_raw_staging을_전혀_건드리지_않고_빈_결과를_반환한다() {
        LoadResult result = service(false).loadAllPending();

        assertThat(result).isEqualTo(new LoadResult(0, 0, 0));
        verify(rawStagingStore, never()).findByStatus(any(), anyInt(), any(), anyBoolean());
    }

    @Test
    void CLEANSED_행을_적재기로_적재해서_LOADED로_전이시키고_메트릭을_남긴다() throws LoadException {
        noRetryBacklog();
        RawStagingDto dto = cleansedRow(1L);
        when(rawStagingStore.findByStatus("CLEANSED", 100, Set.of(), false))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(loaderRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(loader));

        LoadResult result = service(true).loadAllPending();

        assertThat(result.totalProcessed()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failCount()).isZero();
        verify(loader).load(dto);
        verify(rawStagingStore).markLoaded(1L);
        verify(rawStagingStore, never()).markLoadFailed(any(), any());

        assertThat(meterRegistry.get("public_data_load_attempts_total")
            .tag("operationKey", OPERATION_KEY)
            .tag("status", "SUCCESS")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 적재기를_못_찾으면_실패가_아니라_LOAD_SKIPPED로_종결한다() {
        noRetryBacklog();
        RawStagingDto dto = cleansedRow(2L);
        when(rawStagingStore.findByStatus("CLEANSED", 100, Set.of(), false))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(loaderRegistry.find(OPERATION_KEY)).thenReturn(Optional.empty());

        LoadResult result = service(true).loadAllPending();

        // 적재 대상이 아닌 행은 이 단계가 한 일이 없으므로 배치 집계에 잡히지 않는다 -
        // 실패로 세면 법제처 배치가 매일 수백 건 실패로 보고된다.
        assertThat(result.totalProcessed()).isZero();
        assertThat(result.failCount()).isZero();
        verify(rawStagingStore).markLoadSkipped(eq(2L), org.mockito.ArgumentMatchers.contains("적재기 없음"));
        verify(rawStagingStore, never()).markLoadFailed(anyLong(), anyString());

        assertThat(meterRegistry.get("public_data_load_skipped_total")
            .tag("operationKey", OPERATION_KEY)
            .counter().count()).isEqualTo(1.0);
        // "영구 유실" 알람과 실패 카운터는 건드리지 않아야 한다.
        assertThat(meterRegistry.find("public_data_load_abandoned_total").counter()).isNull();
        assertThat(meterRegistry.find("public_data_load_attempts_total").counter()).isNull();
    }

    @Test
    void 적재기가_LoadException을_던지면_LOAD_FAILED로_남기고_배치는_계속된다() throws LoadException {
        noRetryBacklog();
        RawStagingDto dto = cleansedRow(3L);
        when(rawStagingStore.findByStatus("CLEANSED", 100, Set.of(), false))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(loaderRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(loader));
        org.mockito.Mockito.doThrow(new LoadException("소스", "API", "FK 위반")).when(loader).load(dto);

        LoadResult result = service(true).loadAllPending();

        assertThat(result.failCount()).isEqualTo(1);
        verify(rawStagingStore).markLoadFailed(eq(3L), eq("FK 위반"));
    }

    @Test
    void findByStatus가_빈_배치를_반환할때까지_반복해서_모두_처리한다() throws LoadException {
        noRetryBacklog();
        RawStagingDto first = cleansedRow(4L);
        RawStagingDto second = cleansedRow(5L);
        when(rawStagingStore.findByStatus("CLEANSED", 100, Set.of(), false))
            .thenReturn(List.of(first))
            .thenReturn(List.of(second))
            .thenReturn(List.of());
        when(loaderRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(loader));

        LoadResult result = service(true).loadAllPending();

        assertThat(result.totalProcessed()).isEqualTo(2);
        verify(rawStagingStore, times(3)).findByStatus("CLEANSED", 100, Set.of(), false);
    }

    @Test
    void 지난_주기에_실패한_LOAD_FAILED_행을_먼저_재시도한다() throws LoadException {
        RawStagingDto retried = failedRow(6L, 1);
        when(rawStagingStore.findByStatus("LOAD_FAILED", 100, Set.of(), false))
            .thenReturn(List.of(retried));
        when(loaderRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(loader));

        LoadResult result = service(true).loadAllPending();

        assertThat(result.totalProcessed()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        verify(loader).load(retried);
        verify(rawStagingStore).markLoaded(6L);
    }

    @Test
    void 재시도_조회는_한_번만_해서_같은_행을_무한히_다시_잡지_않는다() throws LoadException {
        // LOAD_FAILED 재시도가 또 실패하면 그 행은 다시 LOAD_FAILED가 된다. 여기서 CLEANSED
        // 처럼 "빈 배치가 나올 때까지" 반복하면 같은 행을 영원히 붙들게 되므로, 재시도 조회는
        // 주기당 정확히 1회여야 한다.
        RawStagingDto stuck = failedRow(7L, 1);
        when(rawStagingStore.findByStatus("LOAD_FAILED", 100, Set.of(), false))
            .thenReturn(List.of(stuck));
        when(loaderRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(loader));
        org.mockito.Mockito.doThrow(new LoadException("소스", "API", "또 실패")).when(loader).load(stuck);

        LoadResult result = service(true).loadAllPending();

        assertThat(result.failCount()).isEqualTo(1);
        verify(rawStagingStore, times(1)).findByStatus("LOAD_FAILED", 100, Set.of(), false);
        verify(rawStagingStore).markLoadFailed(eq(7L), eq("또 실패"));
    }

    @Test
    void 재시도_한도를_소진하면_LOAD_ABANDONED로_종결하고_포기_메트릭을_남긴다() throws LoadException {
        // 이미 2회 실패한 행 - 이번이 3회째라 한도(MAX_ATTEMPTS=3) 도달
        RawStagingDto lastChance = failedRow(8L, MAX_ATTEMPTS - 1);
        when(rawStagingStore.findByStatus("LOAD_FAILED", 100, Set.of(), false))
            .thenReturn(List.of(lastChance));
        when(loaderRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(loader));
        org.mockito.Mockito.doThrow(new LoadException("소스", "API", "여전히 실패")).when(loader).load(lastChance);

        LoadResult result = service(true).loadAllPending();

        assertThat(result.failCount()).isEqualTo(1);
        verify(rawStagingStore).markLoadAbandoned(eq(8L), eq("여전히 실패"));
        verify(rawStagingStore, never()).markLoadFailed(eq(8L), any());

        assertThat(meterRegistry.get("public_data_load_abandoned_total")
            .tag("operationKey", OPERATION_KEY)
            .counter().count()).isEqualTo(1.0);
    }

    private RawStagingDto failedRow(Long id, int attemptCount) {
        RawStagingDto dto = cleansedRow(id);
        dto.setStatus("LOAD_FAILED");
        dto.setLoadAttemptCount(attemptCount);
        return dto;
    }

    private RawStagingDto cleansedRow(Long id) {
        return RawStagingDto.builder()
            .id(id)
            .sourceName("공공데이터포털 (기상청 동네예보)")
            .apiName("단기예보조회")
            .operationKey(OPERATION_KEY)
            .facilityId("1270280")
            .collectorKey("kma-village-forecast-vilage-fcst--1270280")
            .cleansedPayload("[{\"nx\":67,\"ny\":100}]")
            .status("CLEANSED")
            .build();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
