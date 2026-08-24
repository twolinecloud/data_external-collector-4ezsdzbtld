package egovframework.external.publicdata.scheduler;

import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.CleanseResult;
import egovframework.external.model.ExecutionType;
import egovframework.external.service.PublicDataCleanseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * raw_staging에 쌓인 COLLECTED 행을 주기적으로 정제하는 스케줄러. 수집 스케줄(최대 매시
 * 12/47분, 15분, 10분 간격)보다 더 자주 돌려서 적체가 오래 쌓이지 않게 함.
 *
 * <p><b>로그 컬렉터 연동(2026-08-20)</b>: 이 스케줄러의 1틱 = 로그 컬렉터 배치 1개
 * (Collect 배치와는 별도, private-doc/log-collector-api-spec.md §8). 꺼져있으면
 * ({@code log-collector.enabled=false}, 기본값) 조용히 no-op.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataCleanseScheduler {

    private final PublicDataCleanseService cleanseService;
    private final LogCollectorBatchService logCollectorBatchService;

    @Scheduled(cron = "${public-data.cleanse.cron:0 */5 * * * *}")
    public void cleanse() {
        BatchHandle handle = logCollectorBatchService.startCleanseBatch(
            ExecutionType.SCHEDULE, "scheduler:cleanse");

        CleanseResult result = cleanseService.cleanseAllPending();

        logCollectorBatchService.finishCleanseBatch(handle, result);
    }
}
