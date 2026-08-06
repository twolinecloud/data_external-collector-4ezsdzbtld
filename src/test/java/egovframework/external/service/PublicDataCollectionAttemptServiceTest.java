package egovframework.external.service;

import egovframework.external.dto.CollectionAttemptLogDto;
import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.CollectException;
import egovframework.external.model.AttemptStatus;
import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.staging.CollectionAttemptLogStore;
import egovframework.external.staging.RawStagingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PublicDataCollectionAttemptService}의 수집 실행 단위 테스트.
 * 저장소 포트는 전부 mock 처리 - 실제 구현(메모리든 DB든)과 무관하게 CI에서 항상 돈다.
 */
@ExtendWith(MockitoExtension.class)
class PublicDataCollectionAttemptServiceTest {

    private static final String SOURCE_NAME = "공공데이터포털 (기상청 동네예보)";
    private static final String API_NAME = "기온";

    @Mock
    private RawStagingStore rawStagingStore;

    @Mock
    private CollectionAttemptLogStore collectionAttemptLogStore;

    @Mock
    private PublicDataCollector collector;

    private PublicDataCollectionAttemptService service() {
        return new PublicDataCollectionAttemptService(rawStagingStore, collectionAttemptLogStore);
    }

    @Test
    void 수집_성공하면_레코드마다_raw_staging에_적재하고_성공_로그를_남긴다() throws CollectException {
        when(collector.sourceName()).thenReturn(SOURCE_NAME);
        when(collector.apiName()).thenReturn(API_NAME);
        when(collector.collect()).thenReturn(List.of("{\"temp\":1}", "{\"temp\":2}"));

        service().run(collector, ExecutionType.SCHEDULE);

        ArgumentCaptor<RawStagingDto> rawCaptor = ArgumentCaptor.forClass(RawStagingDto.class);
        verify(rawStagingStore, times(2)).insert(rawCaptor.capture());
        assertThat(rawCaptor.getAllValues())
            .extracting(RawStagingDto::getRawPayload)
            .containsExactly("{\"temp\":1}", "{\"temp\":2}");
        assertThat(rawCaptor.getAllValues())
            .allSatisfy(dto -> {
                assertThat(dto.getSourceName()).isEqualTo(SOURCE_NAME);
                assertThat(dto.getApiName()).isEqualTo(API_NAME);
            });

        ArgumentCaptor<CollectionAttemptLogDto> logCaptor = ArgumentCaptor.forClass(CollectionAttemptLogDto.class);
        verify(collectionAttemptLogStore, times(1)).insert(logCaptor.capture());
        CollectionAttemptLogDto log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(AttemptStatus.SUCCESS.name());
        assertThat(log.getExecutionType()).isEqualTo(ExecutionType.SCHEDULE.name());
        assertThat(log.getRecordCount()).isEqualTo(2);
        assertThat(log.getFailureLog()).isNull();
    }

    @Test
    void 수집기가_CollectException을_던지면_raw_staging은_비고_실패_로그만_남는다() throws CollectException {
        when(collector.sourceName()).thenReturn(SOURCE_NAME);
        when(collector.apiName()).thenReturn(API_NAME);
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
    }

    @Test
    void 수집기가_CollectException이_아닌_미처리_예외를_던져도_배치는_안죽고_실패로만_기록된다() throws CollectException {
        when(collector.sourceName()).thenReturn(SOURCE_NAME);
        when(collector.apiName()).thenReturn(API_NAME);
        when(collector.collect()).thenThrow(new RuntimeException("예상 못한 NPE 같은 것"));

        service().run(collector, ExecutionType.SCHEDULE);

        verify(rawStagingStore, never()).insert(org.mockito.ArgumentMatchers.any());

        ArgumentCaptor<CollectionAttemptLogDto> logCaptor = ArgumentCaptor.forClass(CollectionAttemptLogDto.class);
        verify(collectionAttemptLogStore, times(1)).insert(logCaptor.capture());
        CollectionAttemptLogDto log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(AttemptStatus.FAILED.name());
        assertThat(log.getFailureLog()).contains("UNHANDLED EXCEPTION").contains("RuntimeException");
    }
}
