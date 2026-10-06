package egovframework.external.service;

import egovframework.external.exception.ConflictException;
import egovframework.external.exception.InvalidParameterException;
import egovframework.external.exception.NotFoundException;
import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.DataTypeClassifier;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.publicdata.scheduler.ScheduleCodes;
import egovframework.external.publicdata.scheduler.ScheduledOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 관리자 서비스의 "바로 실행" - 오퍼레이션 하나를 지금 스케줄 1틱과 똑같이 실행한다.
 *
 * <p>위치의존 오퍼레이션은 지역(59개소) 전체를 한 번에 돌고, 수집이 끝나면 정제·적재까지 같은 execId에 이어붙인다
 * ({@link PublicDataPipelineRunner#collectAndAdvance}). 오래 걸리므로 <b>배치(execId)만 먼저 열어 응답하고 실행은
 * 백그라운드에서</b> 진행한다 - 관리자 서비스의 읽기 타임아웃(3초) 안에 202를 돌려주기 위해서다.
 *
 * <p>날씨·법령은 호출 시점의 최신 값을 가져오므로 수집 구간(to)은 받지 않는다. 스케줄 사용 여부와 무관하게 실행하고,
 * 같은 오퍼레이션이 이미 (바로 실행으로) 돌고 있으면 409로 거절해 연타를 막는다.
 */
@Service
public class CollectorManualRunService {

    private static final Logger logger = LogManager.getLogger(CollectorManualRunService.class);

    /** 시작된 바로 실행. execId는 로그 컬렉터가 꺼져 있으면 null */
    public record Started(String operationKey, String dataTypeCd, String execId) {
    }

    private final ScheduledOperations scheduledOperations;
    private final PublicDataPipelineRunner pipelineRunner;
    private final LogCollectorBatchService logCollectorBatchService;
    private final Executor executor;

    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public CollectorManualRunService(ScheduledOperations scheduledOperations, PublicDataPipelineRunner pipelineRunner,
            LogCollectorBatchService logCollectorBatchService, @Qualifier("taskScheduler") Executor executor) {
        this.scheduledOperations = scheduledOperations;
        this.pipelineRunner = pipelineRunner;
        this.logCollectorBatchService = logCollectorBatchService;
        this.executor = executor;
    }

    /**
     * @param keyOrSchdCd operationKey("kma-asos-hourly") 또는 스케줄 코드("KMA_ASOS_HOURLY")
     * @param triggerBy   요청한 관리자 ID - 배치 이력의 실행 주체로 남는다
     */
    public Started start(String keyOrSchdCd, String triggerBy) {
        if (triggerBy == null || triggerBy.isBlank()) {
            throw new InvalidParameterException(logger, "triggerBy 는 필수입니다");
        }
        String operationKey = ScheduleCodes.resolveOperation(keyOrSchdCd)
            .filter(scheduledOperations.operationKeys()::contains)
            .orElseThrow(() -> new NotFoundException(logger, "알 수 없는 오퍼레이션입니다: " + keyOrSchdCd));

        List<PublicDataCollector> collectors = scheduledOperations.collectors(operationKey).orElse(List.of());
        if (collectors.isEmpty()) {
            throw new InvalidParameterException(logger, "수집 대상 컬렉터가 없습니다: " + operationKey);
        }
        if (!running.add(operationKey)) {
            throw new ConflictException(logger, "이미 바로 실행이 진행 중입니다: " + operationKey);
        }

        try {
            BatchHandle handle = logCollectorBatchService.startCollectBatch(operationKey, ExecutionType.MANUAL, triggerBy.trim());
            executor.execute(() -> runInBackground(operationKey, collectors, triggerBy.trim(), handle));
            logger.info("[MANUAL] 바로 실행 접수 operationKey={} triggerBy={} execId={} collectors={}",
                operationKey, triggerBy, handle.execId(), collectors.size());
            return new Started(operationKey, DataTypeClassifier.dataTypeCd(operationKey), handle.active() ? handle.execId() : null);
        } catch (RuntimeException e) {
            running.remove(operationKey);
            throw e;
        }
    }

    private void runInBackground(String operationKey, List<PublicDataCollector> collectors, String triggerBy, BatchHandle handle) {
        try {
            pipelineRunner.collectAndAdvance(operationKey, collectors, ExecutionType.MANUAL, triggerBy, handle);
        } catch (RuntimeException e) {
            // 열려 있는 배치는 LogCollectorStaleBatchScheduler가 정리한다
            logger.error("[MANUAL] 바로 실행 중 예외 operationKey={} triggerBy={} execId={}", operationKey, triggerBy, handle.execId(), e);
        } finally {
            running.remove(operationKey);
        }
    }
}
