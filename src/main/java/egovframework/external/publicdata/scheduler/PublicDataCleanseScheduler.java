package egovframework.external.publicdata.scheduler;

import egovframework.external.model.ExecutionType;
import egovframework.external.service.PublicDataPipelineRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * raw_staging에 남은 COLLECTED 행을 주기적으로 정제하는 안전망 스케줄러. 평소엔 수집 직후
 * 바로 정제되므로({@link PublicDataPipelineRunner#collectAndAdvance}, 2026-09-29) 이 스케줄러가
 * 처리할 행은 이어서 처리하다 예외가 나 남은 분 정도다.
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
