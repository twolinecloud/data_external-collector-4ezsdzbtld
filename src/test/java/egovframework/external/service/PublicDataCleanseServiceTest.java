package egovframework.external.service;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.CleanseException;
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

    @Mock
    private RawStagingStore rawStagingStore;

    @Mock
    private PublicDataCleanserRegistry cleanserRegistry;

    @Mock
    private PublicDataCleanser cleanser;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private PublicDataCleanseService service() {
        return new PublicDataCleanseService(rawStagingStore, cleanserRegistry, meterRegistry);
    }

    @Test
    void COLLECTED_행을_정제기로_정제해서_CLEANSED로_전이시키고_메트릭을_남긴다() throws CleanseException {
        RawStagingDto dto = pendingRow(1L);
        when(rawStagingStore.findByStatus("COLLECTED", 100))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(cleanserRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(cleanser));
        when(cleanser.cleanse("[{\"t1h\":\"20\"}]")).thenReturn("[{\"t1h\":\"20\",\"reh\":null}]");

        int processed = service().cleanseAllPending();

        assertThat(processed).isEqualTo(1);
        verify(rawStagingStore).markCleansed(eq(1L), eq("[{\"t1h\":\"20\",\"reh\":null}]"), eq(null));
        verify(rawStagingStore, never()).markCleanseFailed(any(), any(), any());

        assertThat(meterRegistry.get("public_data_cleanse_attempts_total")
            .tag("operationKey", OPERATION_KEY)
            .tag("status", "SUCCESS")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 정제기를_못_찾으면_예외_없이_CLEANSE_FAILED로_남긴다() {
        RawStagingDto dto = pendingRow(2L);
        when(rawStagingStore.findByStatus("COLLECTED", 100))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(cleanserRegistry.find(OPERATION_KEY)).thenReturn(Optional.empty());

        int processed = service().cleanseAllPending();

        assertThat(processed).isEqualTo(1);
        verify(rawStagingStore).markCleanseFailed(eq(2L), org.mockito.ArgumentMatchers.contains("정제기 없음"), eq(null));

        assertThat(meterRegistry.get("public_data_cleanse_attempts_total")
            .tag("operationKey", OPERATION_KEY)
            .tag("status", "FAILED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 정제기가_CleanseException을_던지면_CLEANSE_FAILED로_남기고_배치는_계속된다() throws CleanseException {
        RawStagingDto dto = pendingRow(3L);
        when(rawStagingStore.findByStatus("COLLECTED", 100))
            .thenReturn(List.of(dto))
            .thenReturn(List.of());
        when(cleanserRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(cleanser));
        when(cleanser.cleanse(dto.getRawPayload()))
            .thenThrow(new CleanseException("소스", "API", "필드 없음"));

        int processed = service().cleanseAllPending();

        assertThat(processed).isEqualTo(1);
        verify(rawStagingStore).markCleanseFailed(eq(3L), eq("필드 없음"), eq(null));

        assertThat(meterRegistry.get("public_data_cleanse_attempts_total")
            .tag("operationKey", OPERATION_KEY)
            .tag("status", "FAILED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void findByStatus가_빈_배치를_반환할때까지_반복해서_모두_처리한다() throws CleanseException {
        RawStagingDto first = pendingRow(4L);
        RawStagingDto second = pendingRow(5L);
        when(rawStagingStore.findByStatus("COLLECTED", 100))
            .thenReturn(List.of(first))
            .thenReturn(List.of(second))
            .thenReturn(List.of());
        when(cleanserRegistry.find(OPERATION_KEY)).thenReturn(Optional.of(cleanser));
        when(cleanser.cleanse(any())).thenReturn("[]");

        int processed = service().cleanseAllPending();

        assertThat(processed).isEqualTo(2);
        verify(rawStagingStore, times(3)).findByStatus("COLLECTED", 100);
    }

    private RawStagingDto pendingRow(Long id) {
        return RawStagingDto.builder()
            .id(id)
            .sourceName("공공데이터포털 (기상청 동네예보)")
            .apiName("단기예보조회")
            .operationKey(OPERATION_KEY)
            .facilityId("f101")
            .rawPayload("[{\"t1h\":\"20\"}]")
            .status("COLLECTED")
            .build();
    }
}
