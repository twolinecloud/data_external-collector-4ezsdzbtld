package egovframework.external.publicdata.scheduler;

import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.service.PublicDataPipelineRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 오래 열린 로그 컬렉터 배치 정리(2026-09-29). 수집이 연 배치는 정제·적재가 이어받아 닫는데,
 * 파드 재시작(배포 포함)으로 raw_staging과 진행 상태가 메모리에서 사라지면 그 배치를 닫을 주체가
 * 없어진다. 기동 직후 한 번, 이후 주기적으로 {@code threshold}보다 오래 RUNNING인 우리 배치를
 * FAIL로 닫는다.
 *
 * <p>기준 시간은 정상 흐름보다 충분히 길어야 한다 - 법령 491건 수집만 최대 약 40분(운영 실측
 * 2,380초), 적재 재시도가 최대 3주기(약 15분)라 기본 3시간.</p>
 */
@Component
public class LogCollectorStaleBatchScheduler {

    private static final Logger logger = LogManager.getLogger(LogCollectorStaleBatchScheduler.class);

    private final LogCollectorBatchService logCollectorBatchService;
    private final PublicDataPipelineRunner pipelineRunner;
    private final Duration threshold;

    public LogCollectorStaleBatchScheduler(LogCollectorBatchService logCollectorBatchService,
            PublicDataPipelineRunner pipelineRunner,
            @Value("${log-collector.stale-batch.threshold:3h}") Duration threshold) {
        this.logCollectorBatchService = logCollectorBatchService;
        this.pipelineRunner = pipelineRunner;
        this.threshold = threshold;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        sweep();
    }

    @Scheduled(cron = "${log-collector.stale-batch.cron:0 7/30 * * * *}")
    public void sweep() {
        int closed = logCollectorBatchService.closeStaleBatches(threshold);
        int forgotten = pipelineRunner.forgetRunsOlderThan(threshold);
        if (closed > 0 || forgotten > 0) {
            logger.warn("[LOG-COLLECTOR] 오래 열린 배치 정리 - 종료 {}건, 진행상태 정리 {}건 (기준 {})",
                closed, forgotten, threshold);
        }
    }
}
