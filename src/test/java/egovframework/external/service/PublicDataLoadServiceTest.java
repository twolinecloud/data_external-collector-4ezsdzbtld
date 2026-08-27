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

    private PublicDataLoadService service(boolean enabled) {
        return new PublicDataLoadService(rawStagingStore, loaderRegistry, meterRegistry, enabled);
    }

    @Test
    void enabled가_false면_raw_staging을_전혀_건드리지_않고_빈_결과를_반환한다() {
        LoadResult result = service(false).loadAllPending();

        assertThat(result).isEqualTo(new LoadResult(0, 0, 0));
        verify(rawStagingStore, never()).findByStatus(any(), anyInt(), any(), anyBoolean());
    }

    @Test
    void CLEANSED_행을_적재기로_적재해서_LOADED로_전이시키고_메트릭을_남긴다() throws LoadException {
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
    void 적재기를_못_찾으면_예외_없이_LOAD_FAILED로_남긴다() {
        RawStagingDto dto = cleansedRow(2L);
        when(rawStagingStore.findByStatus("CLEANSED", 100, Set.of(), false))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(loaderRegistry.find(OPERATION_KEY)).thenReturn(Optional.empty());

        LoadResult result = service(true).loadAllPending();

        assertThat(result.totalProcessed()).isEqualTo(1);
        assertThat(result.failCount()).isEqualTo(1);
        verify(rawStagingStore).markLoadFailed(eq(2L), org.mockito.ArgumentMatchers.contains("적재기 없음"));

        assertThat(meterRegistry.get("public_data_load_attempts_total")
            .tag("operationKey", OPERATION_KEY)
            .tag("status", "FAILED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 적재기가_LoadException을_던지면_LOAD_FAILED로_남기고_배치는_계속된다() throws LoadException {
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
