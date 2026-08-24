package egovframework.external.publicdata.scheduler;

import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.ExecutionType;
import egovframework.external.model.LoadResult;
import egovframework.external.service.PublicDataLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * raw_staging에 쌓인 CLEANSED 행을 주기적으로 admin-db 최종 테이블에 적재하는 스케줄러.
 * {@code PublicDataCleanseScheduler}와 대칭 구조 - {@code public-data.load.enabled=false}
 * (기본값)면 매 틱마다 {@link PublicDataLoadService#loadAllPending()}이 즉시 빈 결과를
 * 반환해서 조용히 no-op이 된다(이 스케줄러 자체는 조건부로 안 만들어도 안전함).
 *
 * <p>Collect/Cleanse와 동일하게 이 스케줄러의 1틱 = 로그 컬렉터 배치(execId) 1개 - Collect/
 * Cleanse와는 연결하지 않는다(2026-08-21, 사용자 요청).</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataLoadScheduler {

    private final PublicDataLoadService loadService;
    private final LogCollectorBatchService logCollectorBatchService;

    @Scheduled(cron = "${public-data.load.cron:0 */5 * * * *}")
    public void load() {
        BatchHandle handle = logCollectorBatchService.startLoadBatch(
            ExecutionType.SCHEDULE, "scheduler:load");

        LoadResult result = loadService.loadAllPending();

        logCollectorBatchService.finishLoadBatch(handle, result);
    }
}
