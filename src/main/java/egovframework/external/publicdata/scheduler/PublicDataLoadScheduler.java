package egovframework.external.publicdata.scheduler;

import egovframework.external.model.ExecutionType;
import egovframework.external.service.PublicDataLoadService;
import egovframework.external.service.PublicDataPipelineRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * raw_staging의 CLEANSED/LOAD_FAILED 행을 주기적으로 admin-db 최종 테이블에 적재하는 스케줄러.
 * 첫 적재는 평소 수집 직후 바로 이뤄지므로({@link PublicDataPipelineRunner#collectAndAdvance},
 * 2026-09-29) 이 스케줄러의 주 역할은 적재 실패 재시도 주기이고, 나머지는 남은 행을 줍는 안전망이다.
 * {@code public-data.load.enabled=false}(기본값)면 {@link PublicDataLoadService}가 raw_staging을
 * 건드리지 않으므로 조용히 no-op이 된다.
 *
 * <p>적재는 수집 실행(run) 1회 단위로, 정제가 열어둔 로그 컬렉터 execId에 STORE 스텝을 이어붙이고
 * 배치를 닫는 방식으로 진행된다(2026-09-29) - 실제 로직과 이유는 {@link PublicDataPipelineRunner}.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataLoadScheduler {

    private final PublicDataPipelineRunner pipelineRunner;

    @Scheduled(cron = "${public-data.load.cron:0 */5 * * * *}")
    public void load() {
        pipelineRunner.load(ExecutionType.SCHEDULE, "scheduler:load");
    }
}
