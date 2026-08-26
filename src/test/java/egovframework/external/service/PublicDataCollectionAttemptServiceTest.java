package egovframework.external.service;

import egovframework.external.dto.CollectionAttemptLogDto;
import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.CollectException;
import egovframework.external.model.AttemptStatus;
import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.staging.CollectionAttemptLogStore;
import egovframework.external.staging.RawStagingStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PublicDataCollectionAttemptService}의 수집 실행 단위 테스트.
 * 저장소 포트는 mock 처리 - 실제 구현(메모리든 DB든)과 무관하게 CI에서 항상 돈다.
 * 메트릭은 {@link SimpleMeterRegistry}(실제 구현, 인메모리)로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PublicDataCollectionAttemptServiceTest {

    private static final String SOURCE_NAME = "공공데이터포털 (기상청 동네예보)";
    private static final String API_NAME = "기온";
    private static final String COLLECTOR_KEY = "kma-village-forecast-temperature";
    private static final String OPERATION_KEY = "kma-village-forecast-vilage-fcst";
    private static final String FACILITY_ID = "f101";

    @Mock
    private RawStagingStore rawStagingStore;

    @Mock
    private CollectionAttemptLogStore collectionAttemptLogStore;

    @Mock
    private PublicDataCollector collector;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private PublicDataCollectionAttemptService service() {
        return new PublicDataCollectionAttemptService(rawStagingStore, collectionAttemptLogStore, meterRegistry);
    }

    private PublicDataCollectionAttemptService serviceWithTimeout(Duration timeout) {
        return new PublicDataCollectionAttemptService(rawStagingStore, collectionAttemptLogStore, meterRegistry, timeout);
    }

    @Test
    void 수집_성공하면_이번_수집분_전체를_JSON_배열_행_1개로_raw_staging에_적재하고_성공_로그와_메트릭을_남긴다() throws CollectException {
        when(collector.sourceName()).thenReturn(SOURCE_NAME);
        when(collector.apiName()).thenReturn(API_NAME);
        when(collector.key()).thenReturn(COLLECTOR_KEY);
        when(collector.operationKey()).thenReturn(OPERATION_KEY);
        when(collector.facilityId()).thenReturn(FACILITY_ID);
        when(collector.collect()).thenReturn(List.of("{\"temp\":1}", "{\"temp\":2}"));

        service().run(collector, ExecutionType.SCHEDULE);

        ArgumentCaptor<RawStagingDto> rawCaptor = ArgumentCaptor.forClass(RawStagingDto.class);
        verify(rawStagingStore, times(1)).insert(rawCaptor.capture());
        RawStagingDto dto = rawCaptor.getValue();
        assertThat(dto.getRawPayload()).isEqualTo("[{\"temp\":1},{\"temp\":2}]");
        assertThat(dto.getSourceName()).isEqualTo(SOURCE_NAME);
        assertThat(dto.getApiName()).isEqualTo(API_NAME);
        assertThat(dto.getOperationKey()).isEqualTo(OPERATION_KEY);
        assertThat(dto.getFacilityId()).isEqualTo(FACILITY_ID);
        assertThat(dto.getCollectorKey()).isEqualTo(COLLECTOR_KEY);

        ArgumentCaptor<CollectionAttemptLogDto> logCaptor = ArgumentCaptor.forClass(CollectionAttemptLogDto.class);
        verify(collectionAttemptLogStore, times(1)).insert(logCaptor.capture());
        CollectionAttemptLogDto log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(AttemptStatus.SUCCESS.name());
        assertThat(log.getExecutionType()).isEqualTo(ExecutionType.SCHEDULE.name());
        assertThat(log.getRecordCount()).isEqualTo(2);
        assertThat(log.getFailureLog()).isNull();

        assertThat(meterRegistry.get("public_data_collect_attempts_total")
            .tag("collectorKey", COLLECTOR_KEY)
            .tag("status", "SUCCESS")
            .counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("public_data_collect_duration_seconds")
            .tag("collectorKey", COLLECTOR_KEY)
            .timer().count()).isEqualTo(1L);
    }

    @Test
    void 수집기가_CollectException을_던지면_raw_staging은_비고_실패_로그와_메트릭만_남는다() throws CollectException {
        when(collector.sourceName()).thenReturn(SOURCE_NAME);
        when(collector.apiName()).thenReturn(API_NAME);
        when(collector.key()).thenReturn(COLLECTOR_KEY);
        when(collector.collect()).thenThrow(new CollectException(SOURCE_NAME, API_NAME, "엔드포인트 미설정"));

        service().run(collector, ExecutionType.MANUAL);

        verify(rawStagingStore, never()).insert(org.mockito.ArgumentMatchers.any());

        ArgumentCaptor<CollectionAttemptLogDto> logCaptor = ArgumentCaptor.forClass(CollectionAttemptLogDto.class);
        verify(collectionAttemptLogStore, times(1)).insert(logCaptor.capture());
        CollectionAttemptLogDto log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(AttemptStatus.FAILED.name());
        assertThat(log.getExecutionType()).isEqualTo(ExecutionType.MANUAL.name());
        assertThat(log.getRecordCount()).isEqualTo(0);
        assertThat(log.getFailureLog()).isEqualTo("엔드포인트 미설정");

        assertThat(meterRegistry.get("public_data_collect_attempts_total")
            .tag("collectorKey", COLLECTOR_KEY)
            .tag("status", "FAILED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void 수집기가_CollectException이_아닌_미처리_예외를_던져도_배치는_안죽고_실패로만_기록된다() throws CollectException {
        when(collector.sourceName()).thenReturn(SOURCE_NAME);
        when(collector.apiName()).thenReturn(API_NAME);
        when(collector.key()).thenReturn(COLLECTOR_KEY);
        when(collector.collect()).thenThrow(new RuntimeException("예상 못한 NPE 같은 것"));

        service().run(collector, ExecutionType.SCHEDULE);

        verify(rawStagingStore, never()).insert(org.mockito.ArgumentMatchers.any());

        ArgumentCaptor<CollectionAttemptLogDto> logCaptor = ArgumentCaptor.forClass(CollectionAttemptLogDto.class);
        verify(collectionAttemptLogStore, times(1)).insert(logCaptor.capture());
        CollectionAttemptLogDto log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(AttemptStatus.FAILED.name());
        assertThat(log.getFailureLog()).contains("UNHANDLED EXCEPTION").contains("RuntimeException");
    }

    @Test
    void 수집기가_타임아웃보다_오래_걸리면_실패로_기록하고_스케줄러_스레드를_붙잡지_않는다() throws CollectException {
        when(collector.sourceName()).thenReturn(SOURCE_NAME);
        when(collector.apiName()).thenReturn(API_NAME);
        when(collector.key()).thenReturn(COLLECTOR_KEY);
        when(collector.collect()).thenAnswer(invocation -> {
            // 실제 운영 타임아웃(180s)을 테스트에서 그대로 기다릴 수 없어, 아주 짧은 타임아웃을
            // 주입해서(테스트 전용 생성자) 같은 경로를 빠르게 검증한다 - 이 sleep은 워커
            // 스레드(collectExecutor)에서 도니까 테스트 자체는 타임아웃 시점에 바로 리턴된다.
            Thread.sleep(500);
            return List.of("{\"temp\":1}");
        });

        var result = serviceWithTimeout(Duration.ofMillis(50)).run(collector, ExecutionType.SCHEDULE);

        assertThat(result.status()).isEqualTo(AttemptStatus.FAILED);
        assertThat(result.failureLog()).contains("타임아웃");
        verify(rawStagingStore, never()).insert(org.mockito.ArgumentMatchers.any());
    }
}
