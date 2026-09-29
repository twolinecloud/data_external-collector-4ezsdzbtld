package egovframework.external.service;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.DataTypeClassifier;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.logcollector.LogCollectorStatus;
import egovframework.external.model.AttemptStatus;
import egovframework.external.model.CleanseResult;
import egovframework.external.model.CollectResult;
import egovframework.external.model.ExecutionType;
import egovframework.external.model.LoadResult;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.staging.StagingRunTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PublicDataPipelineRunner} 단위 테스트 - 수집 실행(run) 단위로 정제·적재가 진행되는지,
 * 진행 중인 run은 건너뛰는지, 배치가 "더 넘길 행이 없는 단계"에서 가장 나쁜 결과로 닫히는지,
 * 적재 재시도가 원래 배치에 이어붙는지 검증한다. 잠금은 실제 {@link StagingRunTracker}를 쓴다.
 */
@ExtendWith(MockitoExtension.class)
class PublicDataPipelineRunnerTest {

    private static final String PUBLIC_OP = "kma-village-forecast-vilage-fcst";
    private static final String LAW_OP = "moleg-criminal-law";

    @Mock
    private PublicDataCollectionAttemptService collectionAttemptService;
    @Mock
    private PublicDataCleanseService cleanseService;
    @Mock
    private PublicDataLoadService loadService;
    @Mock
    private LogCollectorBatchService logCollectorBatchService;
    @Mock
    private PublicDataCollector collectorA;
    @Mock
    private PublicDataCollector collectorB;

    private final StagingRunTracker tracker = new StagingRunTracker();

    private PublicDataPipelineRunner runner() {
        return new PublicDataPipelineRunner(collectionAttemptService, cleanseService, loadService,
            logCollectorBatchService, tracker);
    }

    private static BatchHandle handle(String execId, String stepLogId) {
        return new BatchHandle(execId, stepLogId, LocalDateTime.now(), true);
    }

    private static RawStagingDto row(String runId, String operationKey, String execId) {
        return RawStagingDto.builder().collectRunId(runId).operationKey(operationKey).originExecId(execId).build();
    }

    private static CollectResult ok() {
        return new CollectResult("k", "s", "a", AttemptStatus.SUCCESS, 1, null);
    }

    private static CollectResult failed() {
        return new CollectResult("k", "s", "a", AttemptStatus.FAILED, 0, "타임아웃");
    }

    /**
     * 수집을 실제로 한 번 돌려 배치를 열어둔 run을 만든다(진행 상태가 러너 안에 남도록).
     *
     * @return 그 수집의 runId
     */
    private String openRun(PublicDataPipelineRunner r, String op, String execId, CollectResult... results) {
        when(logCollectorBatchService.startCollectBatch(eq(op), any(), any())).thenReturn(handle(execId, execId + "01"));
        var stub = when(collectionAttemptService.run(any(), any(), anyString(), eq(execId)));
        for (CollectResult result : results) {
            stub = stub.thenReturn(result);
        }
        when(cleanseService.peekRun(anyString())).thenReturn(Optional.of(row("x", op, execId)));

        List<PublicDataCollector> collectors = results.length == 1 ? List.of(collectorA) : List.of(collectorA, collectorB);
        r.collect(op, collectors, ExecutionType.SCHEDULE, "scheduler:" + op);

        ArgumentCaptor<String> runId = ArgumentCaptor.forClass(String.class);
        verify(collectionAttemptService, org.mockito.Mockito.atLeastOnce()).run(any(), any(), runId.capture(), eq(execId));
        return runId.getValue();
    }

    // ── 수집 ──────────────────────────────────────────────

    @Test
    void 수집은_컬렉터_전부에_같은_runId와_execId를_넘기고_넘길_행이_있으면_배치를_열어둔다() {
        PublicDataPipelineRunner r = runner();
        String runId = openRun(r, PUBLIC_OP, "exec-1", ok(), ok());

        ArgumentCaptor<String> runIds = ArgumentCaptor.forClass(String.class);
        verify(collectionAttemptService, times(2)).run(any(), eq(ExecutionType.SCHEDULE), runIds.capture(), eq("exec-1"));
        assertThat(runIds.getAllValues()).containsOnly(runId);
        verify(logCollectorBatchService).finishCollectStepKeepBatchOpen(any(), eq(List.of(ok(), ok())));
        verify(logCollectorBatchService, never()).finishCollectBatch(any(), any());
        assertThat(r.openRunCount()).isEqualTo(1);
        assertThat(tracker.tryAcquire(runId)).isTrue(); // 수집이 끝나면 잠금이 풀린다
    }

    @Test
    void 수집_결과_행이_없으면_수집에서_배치를_닫는다() {
        PublicDataPipelineRunner r = runner();
        BatchHandle h = handle("exec-nodata", "exec-nodata01");
        when(logCollectorBatchService.startCollectBatch(any(), any(), any())).thenReturn(h);
        when(collectionAttemptService.run(any(), any(), anyString(), any())).thenReturn(failed());
        when(cleanseService.peekRun(anyString())).thenReturn(Optional.empty());

        r.collect("kma-weather-warning-list", List.of(collectorA), ExecutionType.SCHEDULE, "scheduler:x");

        verify(logCollectorBatchService).finishCollectBatch(h, List.of(failed()));
        verify(logCollectorBatchService, never()).finishCollectStepKeepBatchOpen(any(), any());
        assertThat(r.openRunCount()).isZero();
    }

    // ── 수집 직후 이어서 처리 ─────────────────────────────

    @Test
    void 수집_직후_같은_run을_정제하고_적재까지_마친_뒤_배치를_닫는다() {
        PublicDataPipelineRunner r = runner();
        when(logCollectorBatchService.startCollectBatch(eq(PUBLIC_OP), any(), any())).thenReturn(handle("exec-1", "exec-101"));
        when(collectionAttemptService.run(any(), any(), anyString(), eq("exec-1"))).thenReturn(ok());
        when(cleanseService.peekRun(anyString())).thenReturn(Optional.of(row("x", PUBLIC_OP, "exec-1")));
        BatchHandle cleanseStep = handle("exec-1", "exec-102");
        BatchHandle loadStep = handle("exec-1", "exec-103");
        when(logCollectorBatchService.continueBatchWithCleanseStep("exec-1")).thenReturn(Optional.of(cleanseStep));
        when(logCollectorBatchService.continueBatchWithLoadStep("exec-1")).thenReturn(Optional.of(loadStep));
        when(cleanseService.cleanseRun(anyString())).thenReturn(new CleanseResult(1, 1, 0));
        when(loadService.isEnabled()).thenReturn(true);
        when(loadService.peekRun(anyString())).thenReturn(Optional.of(row("x", PUBLIC_OP, "exec-1")));
        when(loadService.loadRun(anyString())).thenReturn(new LoadResult(59, 59, 0));
        when(loadService.hasPendingRetry(anyString())).thenReturn(false);

        r.collectAndAdvance(PUBLIC_OP, List.of(collectorA), ExecutionType.SCHEDULE, "scheduler:" + PUBLIC_OP);

        ArgumentCaptor<String> runId = ArgumentCaptor.forClass(String.class);
        verify(collectionAttemptService).run(any(), any(), runId.capture(), eq("exec-1"));
        verify(cleanseService).cleanseRun(runId.getValue());
        verify(loadService).loadRun(runId.getValue());
        verify(logCollectorBatchService).finishCleanseStepKeepBatchOpen(cleanseStep, new CleanseResult(1, 1, 0));
        verify(logCollectorBatchService).finishLoadStepKeepBatchOpen(loadStep, new LoadResult(59, 59, 0));
        verify(logCollectorBatchService).closeBatch(eq("exec-1"), any(), eq(LogCollectorStatus.SUCCESS), eq(59), eq(59), eq(0));
        assertThat(r.openRunCount()).isZero();
        assertThat(tracker.tryAcquire(runId.getValue())).isTrue();
    }

    @Test
    void 법령은_수집_직후_정제에서_배치를_닫는다() {
        PublicDataPipelineRunner r = runner();
        when(logCollectorBatchService.startCollectBatch(eq(LAW_OP), any(), any())).thenReturn(handle("exec-law", "exec-law01"));
        when(collectionAttemptService.run(any(), any(), anyString(), eq("exec-law"))).thenReturn(ok());
        when(cleanseService.peekRun(anyString())).thenReturn(Optional.of(row("x", LAW_OP, "exec-law")));
        when(logCollectorBatchService.continueBatchWithCleanseStep("exec-law")).thenReturn(Optional.of(handle("exec-law", "exec-law02")));
        when(cleanseService.cleanseRun(anyString())).thenReturn(new CleanseResult(1, 1, 0));
        when(loadService.isEnabled()).thenReturn(false);

        r.collectAndAdvance(LAW_OP, List.of(collectorA), ExecutionType.MANUAL, "manual-api:" + LAW_OP);

        verify(logCollectorBatchService).closeBatch(eq("exec-law"), any(), eq(LogCollectorStatus.SUCCESS), eq(1), eq(1), eq(0));
        verify(loadService, never()).loadRun(any());
        assertThat(r.openRunCount()).isZero();
    }

    @Test
    void 로그컬렉터가_꺼져있어도_수집_직후_정제는_진행된다() {
        when(logCollectorBatchService.startCollectBatch(any(), any(), any())).thenReturn(BatchHandle.inactive());
        when(collectionAttemptService.run(any(), any(), anyString(), eq(null))).thenReturn(ok());
        when(cleanseService.peekRun(anyString())).thenReturn(Optional.of(row("x", PUBLIC_OP, null)));
        when(logCollectorBatchService.startCleanseBatch(any(), any(), any())).thenReturn(BatchHandle.inactive());
        when(cleanseService.cleanseRun(anyString())).thenReturn(new CleanseResult(1, 1, 0));
        when(loadService.isEnabled()).thenReturn(false);

        runner().collectAndAdvance(PUBLIC_OP, List.of(collectorA), ExecutionType.SCHEDULE, "scheduler:" + PUBLIC_OP);

        verify(cleanseService).cleanseRun(anyString());
    }

    @Test
    void 이어서_처리하다_예외가_나도_수집_결과는_돌려주고_잠금과_배치는_남겨_스케줄러에_맡긴다() {
        PublicDataPipelineRunner r = runner();
        when(logCollectorBatchService.startCollectBatch(eq(PUBLIC_OP), any(), any())).thenReturn(handle("exec-1", "exec-101"));
        when(collectionAttemptService.run(any(), any(), anyString(), eq("exec-1"))).thenReturn(ok());
        when(cleanseService.peekRun(anyString())).thenReturn(Optional.of(row("x", PUBLIC_OP, "exec-1")));
        when(logCollectorBatchService.continueBatchWithCleanseStep("exec-1")).thenReturn(Optional.of(handle("exec-1", "exec-102")));
        when(cleanseService.cleanseRun(anyString())).thenThrow(new IllegalStateException("정제 중 오류"));

        List<CollectResult> results = r.collectAndAdvance(PUBLIC_OP, List.of(collectorA), ExecutionType.SCHEDULE, "scheduler:" + PUBLIC_OP);

        assertThat(results).containsExactly(ok());
        verify(logCollectorBatchService, never()).closeBatch(any(), any(), any(), anyInt(), anyInt(), anyInt());
        assertThat(r.openRunCount()).isEqualTo(1); // 다음 정제 주기가 같은 execId로 이어받는다
        ArgumentCaptor<String> runId = ArgumentCaptor.forClass(String.class);
        verify(collectionAttemptService).run(any(), any(), runId.capture(), eq("exec-1"));
        assertThat(tracker.tryAcquire(runId.getValue())).isTrue(); // 잠금은 풀린다
    }

    // ── 정제 ──────────────────────────────────────────────

    @Test
    void 수집_중인_run은_정제가_건너뛴다() {
        tracker.tryAcquire("run-busy");
        when(cleanseService.pendingRunIds()).thenReturn(List.of("run-busy"));

        CleanseResult result = runner().cleanse(ExecutionType.SCHEDULE, "scheduler:cleanse");

        assertThat(result).isEqualTo(new CleanseResult(0, 0, 0));
        verify(cleanseService, never()).cleanseRun(any());
        verifyNoInteractions(logCollectorBatchService);
    }

    @Test
    void 같은_API의_run_두개는_각자_자기_execId에_이어붙고_적재로_넘길_행이_있으면_열어둔다() {
        when(cleanseService.pendingRunIds()).thenReturn(List.of("run-1", "run-2"));
        when(cleanseService.peekRun("run-1")).thenReturn(Optional.of(row("run-1", PUBLIC_OP, "exec-1")));
        when(cleanseService.peekRun("run-2")).thenReturn(Optional.of(row("run-2", PUBLIC_OP, "exec-2")));
        BatchHandle h1 = handle("exec-1", "exec-102");
        BatchHandle h2 = handle("exec-2", "exec-202");
        when(logCollectorBatchService.continueBatchWithCleanseStep("exec-1")).thenReturn(Optional.of(h1));
        when(logCollectorBatchService.continueBatchWithCleanseStep("exec-2")).thenReturn(Optional.of(h2));
        when(cleanseService.cleanseRun("run-1")).thenReturn(new CleanseResult(59, 59, 0));
        when(cleanseService.cleanseRun("run-2")).thenReturn(new CleanseResult(58, 57, 1));
        when(loadService.isEnabled()).thenReturn(true);
        when(loadService.peekRun(anyString())).thenReturn(Optional.of(row("x", PUBLIC_OP, "x")));

        CleanseResult total = runner().cleanse(ExecutionType.SCHEDULE, "scheduler:cleanse");

        verify(logCollectorBatchService).finishCleanseStepKeepBatchOpen(h1, new CleanseResult(59, 59, 0));
        verify(logCollectorBatchService).finishCleanseStepKeepBatchOpen(h2, new CleanseResult(58, 57, 1));
        verify(logCollectorBatchService, never()).closeBatch(any(), any(), any(), anyInt(), anyInt(), anyInt());
        assertThat(total).isEqualTo(new CleanseResult(117, 116, 1));
        assertThat(tracker.tryAcquire("run-1")).isTrue();
    }

    @Test
    void 정제가_전부_실패해서_적재로_넘길_행이_없으면_정제에서_FAIL로_닫는다() {
        PublicDataPipelineRunner r = runner();
        String runId = openRun(r, PUBLIC_OP, "exec-1", ok());
        when(cleanseService.pendingRunIds()).thenReturn(List.of(runId));
        when(cleanseService.peekRun(runId)).thenReturn(Optional.of(row(runId, PUBLIC_OP, "exec-1")));
        when(logCollectorBatchService.continueBatchWithCleanseStep("exec-1")).thenReturn(Optional.of(handle("exec-1", "exec-102")));
        when(cleanseService.cleanseRun(runId)).thenReturn(new CleanseResult(1, 0, 1));
        when(loadService.isEnabled()).thenReturn(true);
        when(loadService.peekRun(runId)).thenReturn(Optional.empty());

        r.cleanse(ExecutionType.SCHEDULE, "scheduler:cleanse");

        verify(logCollectorBatchService).closeBatch(eq("exec-1"), any(), eq(LogCollectorStatus.FAIL), eq(1), eq(0), eq(1));
        assertThat(r.openRunCount()).isZero();
    }

    @Test
    void 적재가_꺼져있으면_정제에서_닫는다() {
        PublicDataPipelineRunner r = runner();
        String runId = openRun(r, PUBLIC_OP, "exec-1", ok());
        when(cleanseService.pendingRunIds()).thenReturn(List.of(runId));
        when(cleanseService.peekRun(runId)).thenReturn(Optional.of(row(runId, PUBLIC_OP, "exec-1")));
        when(logCollectorBatchService.continueBatchWithCleanseStep("exec-1")).thenReturn(Optional.of(handle("exec-1", "exec-102")));
        when(cleanseService.cleanseRun(runId)).thenReturn(new CleanseResult(1, 1, 0));
        when(loadService.isEnabled()).thenReturn(false);

        r.cleanse(ExecutionType.SCHEDULE, "scheduler:cleanse");

        verify(logCollectorBatchService).closeBatch(eq("exec-1"), any(), eq(LogCollectorStatus.SUCCESS), eq(1), eq(1), eq(0));
    }

    @Test
    void 법령_run은_정제에서_닫는다() {
        PublicDataPipelineRunner r = runner();
        String runId = openRun(r, LAW_OP, "exec-law", ok());
        when(cleanseService.pendingRunIds()).thenReturn(List.of(runId));
        when(cleanseService.peekRun(runId)).thenReturn(Optional.of(row(runId, LAW_OP, "exec-law")));
        when(logCollectorBatchService.continueBatchWithCleanseStep("exec-law")).thenReturn(Optional.of(handle("exec-law", "exec-law02")));
        when(cleanseService.cleanseRun(runId)).thenReturn(new CleanseResult(491, 491, 0));

        r.cleanse(ExecutionType.SCHEDULE, "scheduler:cleanse");

        verify(logCollectorBatchService).closeBatch(eq("exec-law"), any(), eq(LogCollectorStatus.SUCCESS), eq(491), eq(491), eq(0));
        verify(loadService, never()).peekRun(any());
    }

    @Test
    void 정제_이어받기에_실패하면_새_정제_배치를_만들고_그_자리에서_닫는다() {
        when(cleanseService.pendingRunIds()).thenReturn(List.of("run-x"));
        when(cleanseService.peekRun("run-x")).thenReturn(Optional.of(row("run-x", PUBLIC_OP, null)));
        BatchHandle fresh = handle("exec-fresh", "exec-fresh01");
        when(logCollectorBatchService.startCleanseBatch(
            DataTypeClassifier.EXTERNAL_PUBLIC, ExecutionType.MANUAL, "manual-api:cleanse")).thenReturn(fresh);
        when(cleanseService.cleanseRun("run-x")).thenReturn(new CleanseResult(1, 1, 0));

        runner().cleanse(ExecutionType.MANUAL, "manual-api:cleanse");

        verify(logCollectorBatchService, never()).continueBatchWithCleanseStep(any());
        verify(logCollectorBatchService).finishCleanseBatch(fresh, new CleanseResult(1, 1, 0));
    }

    // ── 적재 ──────────────────────────────────────────────

    @Test
    void 적재가_꺼져있으면_아무것도_하지_않는다() {
        when(loadService.isEnabled()).thenReturn(false);

        LoadResult result = runner().load(ExecutionType.SCHEDULE, "scheduler:load");

        assertThat(result).isEqualTo(new LoadResult(0, 0, 0));
        verify(loadService, never()).pendingRunIds();
        verifyNoInteractions(logCollectorBatchService);
    }

    @Test
    void 적재가_전부_성공하면_배치를_닫되_최종_상태는_앞_단계의_가장_나쁜_결과를_따른다() {
        PublicDataPipelineRunner r = runner();
        String runId = openRun(r, PUBLIC_OP, "exec-1", ok(), failed()); // 수집 PARTIAL
        when(loadService.isEnabled()).thenReturn(true);
        when(loadService.pendingRunIds()).thenReturn(List.of(runId));
        when(loadService.peekRun(runId)).thenReturn(Optional.of(row(runId, PUBLIC_OP, "exec-1")));
        BatchHandle h = handle("exec-1", "exec-103");
        when(logCollectorBatchService.continueBatchWithLoadStep("exec-1")).thenReturn(Optional.of(h));
        when(loadService.loadRun(runId)).thenReturn(new LoadResult(58, 58, 0));
        when(loadService.hasPendingRetry(runId)).thenReturn(false);

        r.load(ExecutionType.SCHEDULE, "scheduler:load");

        verify(logCollectorBatchService).finishLoadStepKeepBatchOpen(h, new LoadResult(58, 58, 0));
        verify(logCollectorBatchService).closeBatch(eq("exec-1"), any(), eq(LogCollectorStatus.PARTIAL), eq(58), eq(58), eq(0));
        assertThat(r.openRunCount()).isZero();
    }

    @Test
    void 적재_실패가_남으면_배치를_열어두고_재시도가_성공하면_원래_배치를_SUCCESS로_닫는다() {
        PublicDataPipelineRunner r = runner();
        String runId = openRun(r, PUBLIC_OP, "exec-1", ok());
        when(loadService.isEnabled()).thenReturn(true);
        BatchHandle h = handle("exec-1", "exec-103");
        when(logCollectorBatchService.continueBatchWithLoadStep("exec-1")).thenReturn(Optional.of(h));

        // 1주기: 59건 중 2건 실패 → 재시도 대기가 남아 배치는 열린 채
        when(loadService.pendingRetryRunIds()).thenReturn(List.of());
        when(loadService.pendingRunIds()).thenReturn(List.of(runId));
        when(loadService.peekRun(runId)).thenReturn(Optional.of(row(runId, PUBLIC_OP, "exec-1")));
        when(loadService.loadRun(runId)).thenReturn(new LoadResult(59, 57, 2));
        when(loadService.hasPendingRetry(runId)).thenReturn(true);
        r.load(ExecutionType.SCHEDULE, "scheduler:load");
        verify(logCollectorBatchService, never()).closeBatch(any(), any(), any(), anyInt(), anyInt(), anyInt());
        assertThat(r.openRunCount()).isEqualTo(1);

        // 2주기: 재시도 2건 성공 → 원래 배치를 SUCCESS로 닫는다
        when(loadService.pendingRetryRunIds()).thenReturn(List.of(runId));
        when(loadService.pendingRunIds()).thenReturn(List.of());
        when(loadService.peekRetry(runId)).thenReturn(Optional.of(row(runId, PUBLIC_OP, "exec-1")));
        when(loadService.retryRun(runId)).thenReturn(new LoadResult(2, 2, 0));
        when(loadService.hasPendingRetry(runId)).thenReturn(false);
        r.load(ExecutionType.SCHEDULE, "scheduler:load");

        verify(logCollectorBatchService, times(2)).continueBatchWithLoadStep("exec-1");
        verify(logCollectorBatchService).closeBatch(eq("exec-1"), any(), eq(LogCollectorStatus.SUCCESS), eq(59), eq(59), eq(0));
        verify(logCollectorBatchService, never()).startLoadBatch(any(), any(), any());
        assertThat(r.openRunCount()).isZero();
    }

    @Test
    void 재시도_끝에_포기된_행이_있으면_PARTIAL로_닫는다() {
        PublicDataPipelineRunner r = runner();
        String runId = openRun(r, PUBLIC_OP, "exec-1", ok());
        when(loadService.isEnabled()).thenReturn(true);
        when(logCollectorBatchService.continueBatchWithLoadStep("exec-1")).thenReturn(Optional.of(handle("exec-1", "exec-103")));
        when(loadService.pendingRunIds()).thenReturn(List.of(runId)).thenReturn(List.of());
        when(loadService.peekRun(runId)).thenReturn(Optional.of(row(runId, PUBLIC_OP, "exec-1")));
        when(loadService.loadRun(runId)).thenReturn(new LoadResult(10, 9, 1));
        when(loadService.pendingRetryRunIds()).thenReturn(List.of()).thenReturn(List.of(runId));
        when(loadService.peekRetry(runId)).thenReturn(Optional.of(row(runId, PUBLIC_OP, "exec-1")));
        when(loadService.retryRun(runId)).thenReturn(new LoadResult(1, 0, 1)); // 한도 소진 → ABANDONED
        when(loadService.hasPendingRetry(runId)).thenReturn(true).thenReturn(false);

        r.load(ExecutionType.SCHEDULE, "scheduler:load");
        r.load(ExecutionType.SCHEDULE, "scheduler:load");

        verify(logCollectorBatchService).closeBatch(eq("exec-1"), any(), eq(LogCollectorStatus.PARTIAL), eq(10), eq(9), eq(1));
    }

    @Test
    void 이어붙일_배치_정보가_없는_재시도는_재시도_전용_새_배치로_남긴다() {
        when(loadService.isEnabled()).thenReturn(true);
        when(loadService.pendingRetryRunIds()).thenReturn(List.of("run-orphan"));
        when(loadService.peekRetry("run-orphan")).thenReturn(Optional.of(row("run-orphan", PUBLIC_OP, "exec-gone")));
        BatchHandle retry = handle("exec-retry", "exec-retry01");
        when(logCollectorBatchService.startLoadBatch(
            DataTypeClassifier.EXTERNAL_PUBLIC, ExecutionType.SCHEDULE, "scheduler:load:retry")).thenReturn(retry);
        when(loadService.retryRun("run-orphan")).thenReturn(new LoadResult(2, 1, 1));

        LoadResult total = runner().load(ExecutionType.SCHEDULE, "scheduler:load");

        verify(logCollectorBatchService).finishLoadBatch(retry, new LoadResult(2, 1, 1));
        verify(logCollectorBatchService, never()).continueBatchWithLoadStep(any());
        assertThat(total).isEqualTo(new LoadResult(2, 1, 1));
    }

    @Test
    void 법령_run은_로그컬렉터_배치_없이_적재_대상_아님_처리만_한다() {
        when(loadService.isEnabled()).thenReturn(true);
        when(loadService.pendingRunIds()).thenReturn(List.of("run-law"));
        when(loadService.peekRun("run-law")).thenReturn(Optional.of(row("run-law", LAW_OP, "exec-law")));
        when(loadService.loadRun("run-law")).thenReturn(new LoadResult(0, 0, 0));

        runner().load(ExecutionType.SCHEDULE, "scheduler:load");

        verify(loadService).loadRun("run-law");
        verifyNoInteractions(logCollectorBatchService);
    }

    @Test
    void 정제_중인_run은_적재가_건너뛴다() {
        tracker.tryAcquire("run-busy");
        when(loadService.isEnabled()).thenReturn(true);
        when(loadService.pendingRunIds()).thenReturn(List.of("run-busy"));

        runner().load(ExecutionType.SCHEDULE, "scheduler:load");

        verify(loadService, never()).loadRun(any());
        verifyNoInteractions(logCollectorBatchService);
    }

    // ── 정리 ──────────────────────────────────────────────

    @Test
    void 오래된_진행상태는_잊는다() {
        PublicDataPipelineRunner r = runner();
        openRun(r, PUBLIC_OP, "exec-1", ok());

        assertThat(r.forgetRunsOlderThan(Duration.ofHours(3))).isZero();
        assertThat(r.forgetRunsOlderThan(Duration.ofSeconds(-1))).isEqualTo(1);
        assertThat(r.openRunCount()).isZero();
    }
}
