package egovframework.external.publicdata.scheduler;

import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.DataTypeClassifier;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.ExecutionType;
import egovframework.external.model.LoadResult;
import egovframework.external.service.PublicDataLoadService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * raw_staging에 쌓인 CLEANSED 행을 주기적으로 admin-db 최종 테이블에 적재하는 스케줄러.
 * {@code PublicDataCleanseScheduler}와 대칭 구조 - {@code public-data.load.enabled=false}
 * (기본값)면 매 틱마다 {@link PublicDataLoadService#loadPending}이 즉시 빈 결과를
 * 반환해서 조용히 no-op이 된다(이 스케줄러 자체는 조건부로 안 만들어도 안전함).
 *
 * <p>Collect/Cleanse와 동일하게 이 스케줄러의 1틱 = 로그 컬렉터 배치(execId) 1개 - Collect/
 * Cleanse와는 연결하지 않는다(2026-08-21, 사용자 요청).</p>
 *
 * <p><b>배치 2개로 분리(2026-08-27)</b>: {@code PublicDataCleanseScheduler}와 동일한 이유
 * ({@link DataTypeClassifier} 참고) - cron 주기는 그대로 두고 틱마다 EXTERNAL_LAW/
 * EXTERNAL_PUBLIC 배치를 각각 만든다.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataLoadScheduler {

    private final PublicDataLoadService loadService;
    private final LogCollectorBatchService logCollectorBatchService;

    @Scheduled(cron = "${public-data.load.cron:0 */5 * * * *}")
    public void load() {
        runCategory(DataTypeClassifier.EXTERNAL_LAW, DataTypeClassifier.lawOperationKeys(), false);
        runCategory(DataTypeClassifier.EXTERNAL_PUBLIC, DataTypeClassifier.lawOperationKeys(), true);
    }

    private void runCategory(String dataTypeCd, Set<String> lawOperationKeys, boolean exclude) {
        BatchHandle handle = logCollectorBatchService.startLoadBatch(
            dataTypeCd, ExecutionType.SCHEDULE, "scheduler:load");

        LoadResult result = loadService.loadPending(lawOperationKeys, exclude);

        logCollectorBatchService.finishLoadBatch(handle, result);
    }
}
