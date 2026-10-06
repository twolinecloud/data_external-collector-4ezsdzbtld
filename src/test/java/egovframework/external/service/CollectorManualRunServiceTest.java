package egovframework.external.service;

import egovframework.external.exception.ConflictException;
import egovframework.external.exception.InvalidParameterException;
import egovframework.external.exception.NotFoundException;
import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.publicdata.scheduler.ScheduledOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorManualRunServiceTest {

    private static final String OP = "kma-asos-hourly";

    @Mock
    private ScheduledOperations scheduledOperations;
    @Mock
    private PublicDataPipelineRunner pipelineRunner;
    @Mock
    private LogCollectorBatchService logCollectorBatchService;
    @Mock
    private PublicDataCollector collector;

    /** 실행을 바로 돌리지 않고 쌓아 둬서 "백그라운드에서 진행 중"인 상태를 만든다 */
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor executor = queued::add;

    private CollectorManualRunService service() {
        return new CollectorManualRunService(scheduledOperations, pipelineRunner, logCollectorBatchService, executor);
    }

    private void operationRegistered(List<PublicDataCollector> collectors) {
        when(scheduledOperations.operationKeys()).thenReturn(Set.of(OP));
        when(scheduledOperations.collectors(OP)).thenReturn(Optional.of(collectors));
    }

    private static BatchHandle handle(String execId) {
        return new BatchHandle(execId, execId + "01", LocalDateTime.now(), true);
    }

    @Test
    void 배치를_먼저_열어_execId를_돌려주고_실행은_백그라운드로_넘긴다() {
        operationRegistered(List.of(collector));
        BatchHandle h = handle("EXEC-1");
        when(logCollectorBatchService.startCollectBatch(OP, ExecutionType.MANUAL, "admin01")).thenReturn(h);

        CollectorManualRunService.Started started = service().start("KMA_ASOS_HOURLY", "admin01");

        assertThat(started.operationKey()).isEqualTo(OP);
        assertThat(started.dataTypeCd()).isEqualTo("EXTERNAL_PUBLIC");
        assertThat(started.execId()).isEqualTo("EXEC-1");
        verify(pipelineRunner, never()).collectAndAdvance(any(), any(), any(), any(), any()); // 아직 실행 전

        queued.get(0).run();
        verify(pipelineRunner).collectAndAdvance(eq(OP), eq(List.of(collector)), eq(ExecutionType.MANUAL), eq("admin01"), same(h));
    }

    @Test
    void 로그_컬렉터가_꺼져_있으면_execId는_null이고_그래도_실행한다() {
        operationRegistered(List.of(collector));
        when(logCollectorBatchService.startCollectBatch(any(), any(), any())).thenReturn(BatchHandle.inactive());

        CollectorManualRunService.Started started = service().start(OP, "admin01");

        assertThat(started.execId()).isNull();
        assertThat(queued).hasSize(1);
    }

    @Test
    void 같은_오퍼레이션이_진행_중이면_409이고_끝나면_다시_받는다() {
        operationRegistered(List.of(collector));
        when(logCollectorBatchService.startCollectBatch(any(), any(), any())).thenReturn(handle("EXEC-1"));
        CollectorManualRunService service = service();

        service.start(OP, "admin01");
        assertThatThrownBy(() -> service.start(OP, "admin02")).isInstanceOf(ConflictException.class);
        verify(logCollectorBatchService, org.mockito.Mockito.times(1)).startCollectBatch(any(), any(), any());

        queued.get(0).run(); // 끝남
        service.start(OP, "admin02");
        assertThat(queued).hasSize(2);
    }

    @Test
    void 실행_중_예외가_나도_진행_중_표시는_풀린다() {
        operationRegistered(List.of(collector));
        when(logCollectorBatchService.startCollectBatch(any(), any(), any())).thenReturn(handle("EXEC-1"));
        doThrow(new IllegalStateException("boom")).when(pipelineRunner)
            .collectAndAdvance(any(), any(), any(), any(), any());
        CollectorManualRunService service = service();

        service.start(OP, "admin01");
        queued.get(0).run();

        service.start(OP, "admin01"); // 409가 아니어야 한다
        assertThat(queued).hasSize(2);
    }

    @Test
    void 배치를_열다_실패하면_진행_중_표시를_남기지_않는다() {
        operationRegistered(List.of(collector));
        when(logCollectorBatchService.startCollectBatch(any(), any(), any())).thenThrow(new IllegalStateException("x"));
        CollectorManualRunService service = service();

        assertThatThrownBy(() -> service.start(OP, "admin01")).isInstanceOf(IllegalStateException.class);

        doReturn(handle("EXEC-2")).when(logCollectorBatchService).startCollectBatch(any(), any(), any());
        service.start(OP, "admin01");
        assertThat(queued).hasSize(1);
    }

    @Test
    void triggerBy가_없으면_거절한다() {
        assertThatThrownBy(() -> service().start(OP, " ")).isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service().start(OP, null)).isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void 등록되지_않은_오퍼레이션은_404다() {
        when(scheduledOperations.operationKeys()).thenReturn(Set.of(OP));

        assertThatThrownBy(() -> service().start("nope", "admin01")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void 수집_대상_컬렉터가_없으면_거절한다() {
        operationRegistered(List.of());

        assertThatThrownBy(() -> service().start(OP, "admin01")).isInstanceOf(InvalidParameterException.class);
        verify(logCollectorBatchService, never()).startCollectBatch(any(), any(), any());
    }
}
