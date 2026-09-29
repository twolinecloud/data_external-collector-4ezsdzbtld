package egovframework.external.service;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.CleanseException;
import egovframework.external.model.CleanseResult;
import egovframework.external.publicdata.cleanser.CleansedJsonDropWriter;
import egovframework.external.publicdata.cleanser.JsonStructureDriftDetector;
import egovframework.external.publicdata.cleanser.PublicDataCleanser;
import egovframework.external.publicdata.cleanser.PublicDataCleanserRegistry;
import egovframework.external.staging.RawStagingStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PublicDataCleanseService}의 오케스트레이션 단위 테스트.
 * {@code PublicDataCollectionAttemptServiceTest}와 대칭 - 저장소/레지스트리는 mock,
 * 메트릭은 {@link SimpleMeterRegistry}(실제 구현)로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PublicDataCleanseServiceTest {

    private static final String OPERATION_KEY = "kma-village-forecast-vilage-fcst";
    private static final String RUN_ID = "01RUN0000000000000000000A";

    @Mock
    private RawStagingStore rawStagingStore;

    @Mock
    private PublicDataCleanserRegistry cleanserRegistry;

    @Mock
    private PublicDataCleanser cleanser;

    @Mock
    private CleansedJsonDropWriter jsonDropWriter;

    @Mock
    private JsonStructureDriftDetector structureDriftDetector;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private PublicDataCleanseService service() {
        return new PublicDataCleanseService(rawStagingStore, cleanserRegistry, meterRegistry, jsonDropWriter, structureDriftDetector);
    }

    @Test
    void COLLECTED_행을_정제기로_정제해서_CLEANSED로_전이시키고_메트릭을_남긴다() throws CleanseException {
        RawStagingDto dto = pendingRow(1L);
        when(rawStagingStore.findByStatusAndRunId("COLLECTED", 100, RUN_ID))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(cleanserRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(cleanser));
        when(cleanser.cleanse("[{\"t1h\":\"20\"}]")).thenReturn("[{\"t1h\":\"20\",\"reh\":null}]");

        CleanseResult result = service().cleanseRun(RUN_ID);

        assertThat(result.totalProcessed()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failCount()).isZero();
        verify(rawStagingStore).markCleansed(eq(1L), eq("[{\"t1h\":\"20\",\"reh\":null}]"), eq(null));
        verify(rawStagingStore, never()).markCleanseFailed(any(), any(), any());
        verify(jsonDropWriter).write("kma-village-forecast-vilage-fcst--f101", "[{\"t1h\":\"20\",\"reh\":null}]");
        verify(structureDriftDetector).check(cleanser, OPERATION_KEY, "[{\"t1h\":\"20\"}]");

        assertThat(meterRegistry.get("public_data_cleanse_attempts_total")
            .tag("operationKey", OPERATION_KEY)
            .tag("status", "SUCCESS")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 정제기를_못_찾으면_예외_없이_CLEANSE_FAILED로_남긴다() {
        RawStagingDto dto = pendingRow(2L);
        when(rawStagingStore.findByStatusAndRunId("COLLECTED", 100, RUN_ID))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(cleanserRegistry.find(OPERATION_KEY)).thenReturn(Optional.empty());

        CleanseResult result = service().cleanseRun(RUN_ID);

        assertThat(result.totalProcessed()).isEqualTo(1);
        assertThat(result.successCount()).isZero();
        assertThat(result.failCount()).isEqualTo(1);
        verify(rawStagingStore).markCleanseFailed(eq(2L), org.mockito.ArgumentMatchers.contains("정제기 없음"), eq(null));
        verify(jsonDropWriter, never()).write(any(), any());

        assertThat(meterRegistry.get("public_data_cleanse_attempts_total")
            .tag("operationKey", OPERATION_KEY)
            .tag("status", "FAILED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 정제기가_CleanseException을_던지면_CLEANSE_FAILED로_남기고_배치는_계속된다() throws CleanseException {
        RawStagingDto dto = pendingRow(3L);
        when(rawStagingStore.findByStatusAndRunId("COLLECTED", 100, RUN_ID))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(cleanserRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(cleanser));
        when(cleanser.cleanse(dto.getRawPayload()))
            .thenThrow(new CleanseException("소스", "API", "필드 없음"));

        CleanseResult result = service().cleanseRun(RUN_ID);

        assertThat(result.totalProcessed()).isEqualTo(1);
        assertThat(result.failCount()).isEqualTo(1);
        verify(rawStagingStore).markCleanseFailed(eq(3L), eq("필드 없음"), eq(null));
        verify(jsonDropWriter, never()).write(any(), any());
        verify(structureDriftDetector).check(cleanser, OPERATION_KEY, dto.getRawPayload());

        assertThat(meterRegistry.get("public_data_cleanse_attempts_total")
            .tag("operationKey", OPERATION_KEY)
            .tag("status", "FAILED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void findByStatusAndRunId가_빈_배치를_반환할때까지_반복해서_모두_처리한다() throws CleanseException {
        RawStagingDto first = pendingRow(4L);
        RawStagingDto second = pendingRow(5L);
        when(rawStagingStore.findByStatusAndRunId("COLLECTED", 100, RUN_ID))
            .thenReturn(List.of(first))
            .thenReturn(List.of(second))
            .thenReturn(List.of());
        when(cleanserRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(cleanser));
        when(cleanser.cleanse(any())).thenReturn("[]");

        CleanseResult result = service().cleanseRun(RUN_ID);

        assertThat(result.totalProcessed()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        verify(rawStagingStore, times(3)).findByStatusAndRunId("COLLECTED", 100, RUN_ID);
    }

    @Test
    void peekRun은_대기행이_없으면_빈값을_반환하고_상태를_바꾸지_않는다() {
        when(rawStagingStore.findByStatusAndRunId("COLLECTED", 1, RUN_ID))
            .thenReturn(List.of());

        Optional<RawStagingDto> result = service().peekRun(RUN_ID);

        assertThat(result).isEmpty();
        verify(rawStagingStore, never()).markCleansed(any(), any(), any());
    }

    @Test
    void peekRun은_대기행이_있으면_소모하지_않고_그대로_반환한다() {
        RawStagingDto row = RawStagingDto.builder()
            .id(9L)
            .operationKey("moleg-criminal-law")
            .status("COLLECTED")
            .originExecId("20260928EXT001")
            .build();
        when(rawStagingStore.findByStatusAndRunId("COLLECTED", 1, RUN_ID))
            .thenReturn(List.of(row));

        Optional<RawStagingDto> result = service().peekRun(RUN_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getOriginExecId()).isEqualTo("20260928EXT001");
        verify(rawStagingStore, never()).markCleansed(any(), any(), any());
        verify(rawStagingStore, never()).markCleanseFailed(any(), any(), any());
    }

    private RawStagingDto pendingRow(Long id) {
        return RawStagingDto.builder()
            .id(id)
            .sourceName("공공데이터포털 (기상청 동네예보)")
            .apiName("단기예보조회")
            .operationKey(OPERATION_KEY)
            .facilityId("f101")
            .collectorKey("kma-village-forecast-vilage-fcst--f101")
            .rawPayload("[{\"t1h\":\"20\"}]")
            .status("COLLECTED")
            .collectRunId(RUN_ID)
            .build();
    }
}
