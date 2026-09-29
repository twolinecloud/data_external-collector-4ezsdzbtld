package egovframework.external.publicdata.scheduler;

import egovframework.external.model.ExecutionType;
import egovframework.external.service.PublicDataPipelineRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * raw_staging에 쌓인 COLLECTED 행을 주기적으로 정제하는 스케줄러. 수집 스케줄(최대 매시
 * 12/47분, 15분, 10분 간격)보다 더 자주 돌려서 적체가 오래 쌓이지 않게 함.
 *
 * <p>정제는 수집 실행(run) 1회 단위로, 그 수집이 받은 로그 컬렉터 execId에 CLEANSE 스텝을
 * 이어붙이는 방식으로 진행된다(2026-09-29) - 실제 로직과 이유는 {@link PublicDataPipelineRunner}.
 * 로그 컬렉터가 꺼져있으면({@code log-collector.enabled=false}) 배치 기록만 조용히 no-op.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataCleanseScheduler {

    private final PublicDataPipelineRunner pipelineRunner;

    @Scheduled(cron = "${public-data.cleanse.cron:0 */5 * * * *}")
    public void cleanse() {
        pipelineRunner.cleanse(ExecutionType.SCHEDULE, "scheduler:cleanse");
    }
}
