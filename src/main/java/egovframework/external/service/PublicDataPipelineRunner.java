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
import egovframework.external.utility.Ulid;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 수집→정제→적재를 <b>수집 실행 1회(collectRunId) 단위</b>로 진행시키면서, 그 실행이 받은 로그
 * 컬렉터 execId 하나에 단계를 이어붙인다(2026-09-29). 스케줄러와 수동 트리거 컨트롤러가 모두
 * 이 클래스를 쓴다 - 둘이 다른 방식으로 배치를 만들면 수집이 열어둔 배치를 아무도 닫지 못한다.
 *
 * <p><b>배치를 닫는 규칙</b>: 각 단계는 끝날 때 스텝만 닫고, 배치는 "이 run에서 다음 단계로
 * 넘어갈 행이 더 없을 때" 그 단계가 닫는다. 그래서 수집이 0건이면 수집에서, 정제가 전부 실패하거나
 * 적재가 꺼져 있으면 정제에서, 법령(적재기 없음)은 정제에서, 공공데이터는 적재 재시도까지 전부
 * 끝난 뒤(LOADED 또는 LOAD_ABANDONED) 적재에서 닫힌다. 최종 상태는 거쳐온 단계 중 가장 나쁜 결과다
 * - 수집이 PARTIAL이면 적재가 전부 성공해도 PARTIAL.</p>
 *
 * <p>같은 run을 두 단계가 동시에 만지지 않도록 {@link StagingRunTracker}로 잠근다 - 스케줄러
 * 스레드풀이 여러 개라 수집·정제·적재가 동시에 돈다. 잠겨 있는 run은 그 틱에 건너뛰고 다음 틱에
 * 처리한다. 이어받기(continue)에 실패하면 그 단계는 새 배치로 기록하고 그 자리에서 닫는다 - 원래
 * 배치가 열린 채 남으면 {@link LogCollectorBatchService#closeStaleBatches}가 정리한다.</p>
 *
 * <p><b>수집 직후 바로 이어서 처리(2026-09-29)</b>: 스케줄러·수동 트리거는
 * {@link #collectAndAdvance}로 수집이 끝나자마자 같은 잠금 안에서 그 run을 정제·적재까지 진행한다 -
 * 5분 주기 정제/적재 스케줄러를 기다리면 admin-db 반영이 최대 10분 늦고, 그동안 배치가 RUNNING으로
 * 남아 소요시간도 대기시간만큼 부풀었다. 정제/적재 스케줄러({@link #cleanse}/{@link #load})는 적재
 * 실패 재시도와, 이어서 처리하다 예외가 나 남은 run을 줍는 안전망으로 그대로 둔다.</p>
 *
 * <p>열린 배치의 진행 상태({@link RunState})는 메모리에만 있다 - raw_staging과 같은 단일 인스턴스
 * 전제이고, 재시작하면 raw_staging 행과 함께 사라진다.</p>
 */
@Service
@RequiredArgsConstructor
public class PublicDataPipelineRunner {

    private static final Logger logger = LogManager.getLogger(PublicDataPipelineRunner.class);

    private final PublicDataCollectionAttemptService collectionAttemptService;
    private final PublicDataCleanseService cleanseService;
    private final PublicDataLoadService loadService;
    private final LogCollectorBatchService logCollectorBatchService;
    private final StagingRunTracker runTracker;

    /** 열려 있는 배치의 진행 상태, collectRunId별. */
    private final Map<String, RunState> openRuns = new ConcurrentHashMap<>();

    static final class RunState {
        final String execId;
        final LocalDateTime startedAt;
        LogCollectorStatus worst;
        /** 첫 적재 시도의 대상 건수 - 재시도는 같은 행을 다시 세므로 더하지 않는다. */
        int loadTarget;
        /** 첫 시도 + 재시도로 결국 적재된 건수. */
        int loadSuccess;

        RunState(String execId, LocalDateTime startedAt) {
            this.execId = execId;
            this.startedAt = startedAt;
        }
    }

    // ── 수집 ──────────────────────────────────────────────

    /**
     * 오퍼레이션 1회 수집만 하고 정제·적재는 스케줄러에 맡긴다(컬렉터가 몇 개든 - 59개소 순회도 run 1개,
     * execId 1개). 운영 경로는 {@link #collectAndAdvance} - 이건 테스트에서 정제 전 상태를 만들 때 쓴다.
     */
    List<CollectResult> collect(String operationKey, List<PublicDataCollector> collectors,
            ExecutionType executionType, String triggerBy) {
        String runId = Ulid.generate();
        runTracker.tryAcquire(runId);
        try {
            return collectRun(runId, operationKey, collectors, executionType, triggerBy, null);
        } finally {
            runTracker.release(runId);
        }
    }

    /**
     * 수집한 뒤 잠금을 놓지 않고 그 run을 곧바로 정제·적재까지 진행한다 - 스케줄러와 수동 트리거용.
     * 정제·적재에서 예외가 나도 수집 결과는 그대로 돌려주고, 남은 행은 정제/적재 스케줄러가 줍는다.
     */
    public List<CollectResult> collectAndAdvance(String operationKey, List<PublicDataCollector> collectors,
            ExecutionType executionType, String triggerBy) {
        return collectAndAdvance(operationKey, collectors, executionType, triggerBy, null);
    }

    /**
     * {@link #collectAndAdvance(String, List, ExecutionType, String)}와 같되, 호출자가 이미 열어 둔 배치로 이어서 실행한다.
     * 바로 실행 API가 execId를 요청 응답에 먼저 돌려주고 수집은 백그라운드에서 진행할 때 쓴다.
     *
     * @param preStarted {@link LogCollectorBatchService#startCollectBatch}로 이미 연 수집 배치. null이면 여기서 연다
     */
    public List<CollectResult> collectAndAdvance(String operationKey, List<PublicDataCollector> collectors,
            ExecutionType executionType, String triggerBy, BatchHandle preStarted) {
        String runId = Ulid.generate();
        runTracker.tryAcquire(runId);
        try {
            List<CollectResult> results = collectRun(runId, operationKey, collectors, executionType, triggerBy, preStarted);
            advance(runId, operationKey, executionType, triggerBy);
            return results;
        } finally {
            runTracker.release(runId);
        }
    }

    private List<CollectResult> collectRun(String runId, String operationKey, List<PublicDataCollector> collectors,
            ExecutionType executionType, String triggerBy, BatchHandle preStarted) {
        BatchHandle handle = preStarted != null
            ? preStarted
            : logCollectorBatchService.startCollectBatch(operationKey, executionType, triggerBy);
        String execId = handle.active() ? handle.execId() : null;

        List<CollectResult> results = new ArrayList<>(collectors.size());
        for (PublicDataCollector collector : collectors) {
            results.add(collectionAttemptService.run(collector, executionType, runId, execId));
        }

        if (!handle.active()) {
            return results;
        }
        if (cleanseService.peekRun(runId).isEmpty()) {
            // 넘길 행이 없다(0건 수집 - 특보 없음·NODATA 등, 또는 전부 실패) - 여기서 닫는다.
            logCollectorBatchService.finishCollectBatch(handle, results);
            return results;
        }
        logCollectorBatchService.finishCollectStepKeepBatchOpen(handle, results);
        RunState state = new RunState(execId, handle.startedAt());
        state.worst = collectStatus(results);
        openRuns.put(runId, state);
        return results;
    }

    /** 잠금을 쥔 run 하나를 정제 → (적재가 켜져 있으면) 적재까지. 로그 컬렉터가 꺼져 있어도 데이터는 진행시킨다. */
    private void advance(String runId, String operationKey, ExecutionType executionType, String triggerBy) {
        try {
            Optional<RawStagingDto> cleanseHead = cleanseService.peekRun(runId);
            if (cleanseHead.isEmpty()) {
                return;
            }
            cleanseRun(runId, cleanseHead.get(), executionType, triggerBy);
            if (!loadService.isEnabled()) {
                return;
            }
            loadService.peekRun(runId).ifPresent(head -> loadRun(runId, head, executionType, triggerBy));
        } catch (RuntimeException e) {
            logger.warn("[PIPELINE] 수집 직후 정제·적재 중 예외 - 남은 행은 스케줄러가 이어서 처리 operationKey={} runId={}",
                operationKey, runId, e);
        }
    }

    private static LogCollectorStatus collectStatus(List<CollectResult> results) {
        int success = (int) results.stream().filter(r -> r.status() == AttemptStatus.SUCCESS).count();
        return LogCollectorStatus.aggregate(success, results.size() - success);
    }

    // ── 정제 ──────────────────────────────────────────────

    /** 정제 대기 중인 run을 하나씩 정제. @return 이번 호출에서 처리한 전체 합계 */
    public CleanseResult cleanse(ExecutionType executionType, String triggerBy) {
        int total = 0;
        int success = 0;
        int fail = 0;
        for (String runId : cleanseService.pendingRunIds()) {
            if (!runTracker.tryAcquire(runId)) {
                continue; // 아직 수집 중이거나 다른 단계가 처리 중 - 다음 틱에
            }
            try {
                Optional<RawStagingDto> head = cleanseService.peekRun(runId);
                if (head.isEmpty()) {
                    continue;
                }
                CleanseResult result = cleanseRun(runId, head.get(), executionType, triggerBy);
                total += result.totalProcessed();
                success += result.successCount();
                fail += result.failCount();
            } finally {
                runTracker.release(runId);
            }
        }
        return new CleanseResult(total, success, fail);
    }

    private CleanseResult cleanseRun(String runId, RawStagingDto head, ExecutionType executionType, String triggerBy) {
        String dataTypeCd = DataTypeClassifier.dataTypeCd(head.getOperationKey());
        Optional<BatchHandle> continued = Optional.ofNullable(head.getOriginExecId())
            .flatMap(logCollectorBatchService::continueBatchWithCleanseStep);

        if (continued.isEmpty()) {
            BatchHandle fresh = logCollectorBatchService.startCleanseBatch(dataTypeCd, executionType, triggerBy);
            CleanseResult result = cleanseService.cleanseRun(runId);
            logCollectorBatchService.finishCleanseBatch(fresh, result);
            return result;
        }

        BatchHandle handle = continued.get();
        CleanseResult result = cleanseService.cleanseRun(runId);
        logCollectorBatchService.finishCleanseStepKeepBatchOpen(handle, result);

        RunState state = stateOf(runId, handle);
        state.worst = LogCollectorStatus.worst(state.worst,
            LogCollectorStatus.aggregate(result.successCount(), result.failCount()));

        boolean handOffToLoad = !DataTypeClassifier.EXTERNAL_LAW.equals(dataTypeCd)
            && loadService.isEnabled()
            && loadService.peekRun(runId).isPresent();
        if (!handOffToLoad) {
            // 법령(적재 단계 없음) / 정제 전부 실패 / 적재 비활성 - 여기서 닫는다.
            closeRun(runId, state, result.totalProcessed(), result.successCount(), result.failCount());
        }
        return result;
    }

    // ── 적재 ──────────────────────────────────────────────

    /**
     * 지난 주기에 실패한 행을 run별로 재시도한 뒤, 적재 대기 중인 run을 하나씩 적재.
     * 재시도를 먼저 하는 건 이번 주기에 막 실패한 행을 곧바로 다시 치지 않기 위해서다(일시적
     * 장애에 한 주기 여유).
     *
     * @return 이번 호출에서 처리한 전체 합계
     */
    public LoadResult load(ExecutionType executionType, String triggerBy) {
        if (!loadService.isEnabled()) {
            return new LoadResult(0, 0, 0);
        }
        Tally tally = new Tally();
        for (String runId : loadService.pendingRetryRunIds()) {
            withRun(runId, () -> loadService.peekRetry(runId)
                .ifPresent(head -> tally.add(retryRun(runId, head, executionType, triggerBy))));
        }
        for (String runId : loadService.pendingRunIds()) {
            withRun(runId, () -> loadService.peekRun(runId)
                .ifPresent(head -> tally.add(loadRun(runId, head, executionType, triggerBy))));
        }
        return new LoadResult(tally.total, tally.success, tally.fail);
    }

    private LoadResult loadRun(String runId, RawStagingDto head, ExecutionType executionType, String triggerBy) {
        if (DataTypeClassifier.isLaw(head.getOperationKey())) {
            // 법령은 적재기가 없어 LOAD_SKIPPED 종결만 한다 - 배치는 정제에서 이미 닫혔다.
            return loadService.loadRun(runId);
        }
        Optional<BatchHandle> continued = Optional.ofNullable(head.getOriginExecId())
            .flatMap(logCollectorBatchService::continueBatchWithLoadStep);
        if (continued.isEmpty()) {
            BatchHandle fresh = logCollectorBatchService.startLoadBatch(
                DataTypeClassifier.EXTERNAL_PUBLIC, executionType, triggerBy);
            LoadResult result = loadService.loadRun(runId);
            logCollectorBatchService.finishLoadBatch(fresh, result);
            return result;
        }

        BatchHandle handle = continued.get();
        LoadResult result = loadService.loadRun(runId);
        logCollectorBatchService.finishLoadStepKeepBatchOpen(handle, result);

        RunState state = stateOf(runId, handle);
        state.loadTarget += result.totalProcessed();
        state.loadSuccess += result.successCount();
        closeIfSettled(runId, state);
        return result;
    }

    private LoadResult retryRun(String runId, RawStagingDto head, ExecutionType executionType, String triggerBy) {
        RunState state = openRuns.get(runId);
        Optional<BatchHandle> continued = state == null
            ? Optional.empty()
            : logCollectorBatchService.continueBatchWithLoadStep(state.execId);
        if (continued.isEmpty()) {
            // 이어붙일 배치 정보가 없다(첫 적재가 새 배치로 대체됐거나 재시작 등) - 재시도만의 새 배치.
            BatchHandle fresh = logCollectorBatchService.startLoadBatch(
                DataTypeClassifier.EXTERNAL_PUBLIC, executionType, triggerBy + ":retry");
            LoadResult result = loadService.retryRun(runId);
            logCollectorBatchService.finishLoadBatch(fresh, result);
            return result;
        }

        LoadResult result = loadService.retryRun(runId);
        logCollectorBatchService.finishLoadStepKeepBatchOpen(continued.get(), result);
        state.loadSuccess += result.successCount();
        closeIfSettled(runId, state);
        return result;
    }

    /** 이 run에 재시도할 행이 더 없으면(전부 LOADED 또는 LOAD_ABANDONED) 최종 결과로 닫는다. */
    private void closeIfSettled(String runId, RunState state) {
        if (loadService.hasPendingRetry(runId)) {
            return;
        }
        int abandoned = Math.max(0, state.loadTarget - state.loadSuccess);
        state.worst = LogCollectorStatus.worst(state.worst,
            LogCollectorStatus.aggregate(state.loadSuccess, abandoned));
        closeRun(runId, state, state.loadTarget, state.loadSuccess, abandoned);
    }

    // ── 공통 ──────────────────────────────────────────────

    private RunState stateOf(String runId, BatchHandle handle) {
        // 상태가 없으면(수집이 이 인스턴스 밖에서 일어났거나 재시작 직후) 이어받은 핸들 기준으로 만든다.
        return openRuns.computeIfAbsent(runId, id -> new RunState(handle.execId(), handle.startedAt()));
    }

    private void closeRun(String runId, RunState state, int target, int success, int fail) {
        logCollectorBatchService.closeBatch(state.execId, state.startedAt, state.worst, target, success, fail);
        openRuns.remove(runId);
    }

    private void withRun(String runId, Runnable work) {
        if (!runTracker.tryAcquire(runId)) {
            return; // 아직 정제 중이거나 다른 단계가 처리 중 - 다음 틱에
        }
        try {
            work.run();
        } finally {
            runTracker.release(runId);
        }
    }

    /**
     * 열린 채로 {@code maxAge}를 넘긴 run 상태를 잊는다 - 행이 유효기간 만료 등으로 raw_staging에서
     * 사라져 아무도 닫지 못하게 된 run의 메모리 정리. 배치 자체는
     * {@link LogCollectorBatchService#closeStaleBatches}가 닫는다.
     *
     * @return 잊은 run 수
     */
    public int forgetRunsOlderThan(Duration maxAge) {
        LocalDateTime cutoff = LocalDateTime.now().minus(maxAge);
        int before = openRuns.size();
        openRuns.values().removeIf(s -> s.startedAt.isBefore(cutoff));
        return before - openRuns.size();
    }

    int openRunCount() {
        return openRuns.size();
    }

    private static final class Tally {
        int total;
        int success;
        int fail;

        void add(LoadResult r) {
            total += r.totalProcessed();
            success += r.successCount();
            fail += r.failCount();
        }
    }
}
