package egovframework.external.publicdata.scheduler;

import egovframework.external.service.PublicDataCleanseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * raw_staging에 쌓인 COLLECTED 행을 주기적으로 정제하는 스케줄러. 수집 스케줄(최대 매시
 * 12/47분, 15분, 10분 간격)보다 더 자주 돌려서 적체가 오래 쌓이지 않게 함.
 */
@Component
@RequiredArgsConstructor
public class PublicDataCleanseScheduler {

    private final PublicDataCleanseService cleanseService;

    @Scheduled(cron = "${public-data.cleanse.cron:0 */5 * * * *}")
    public void cleanse() {
        cleanseService.cleanseAllPending();
    }
}
