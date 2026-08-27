package egovframework.external.publicdata.scheduler;

import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.DataTypeClassifier;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.CleanseResult;
import egovframework.external.model.ExecutionType;
import egovframework.external.service.PublicDataCleanseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * raw_staging에 쌓인 COLLECTED 행을 주기적으로 정제하는 스케줄러. 수집 스케줄(최대 매시
 * 12/47분, 15분, 10분 간격)보다 더 자주 돌려서 적체가 오래 쌓이지 않게 함.
 *
 * <p><b>로그 컬렉터 연동(2026-08-20)</b>: 이 스케줄러의 1틱 = 로그 컬렉터 배치 1개
 * (Collect 배치와는 별도, private-doc/log-collector-api-spec.md §8). 꺼져있으면
 * ({@code log-collector.enabled=false}, 기본값) 조용히 no-op.</p>
 *
 * <p><b>배치 2개로 분리(2026-08-27)</b>: dataTypeCd(C01 공통코드)가 EXTERNAL_PUBLIC/
 * EXTERNAL_LAW로 나뉘었는데, 이 스케줄러는 오퍼레이션 구분 없이 raw_staging 전체를 한 번에
 * 훑는 구조라 배치 하나에 두 dataTypeCd를 못 담는다({@link DataTypeClassifier} 참고).
 * 그래서 cron 주기(5분)는 그대로 두고, 같은 틱 안에서 카테고리별로 배치를 2개(EXTERNAL_LAW
 * 먼저, EXTERNAL_PUBLIC 나중) 만든다 - 법령은 하루 1건 수준이라 대부분 틱에서는 빈 배치가
 * 하나 더 생기는 비용이 있음(추후 "처리할 게 없으면 배치 자체를 생략" 최적화 여지 있음).</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataCleanseScheduler {

    private final PublicDataCleanseService cleanseService;
    private final LogCollectorBatchService logCollectorBatchService;

    @Scheduled(cron = "${public-data.cleanse.cron:0 */5 * * * *}")
    public void cleanse() {
        runCategory(DataTypeClassifier.EXTERNAL_LAW, DataTypeClassifier.lawOperationKeys(), false);
        runCategory(DataTypeClassifier.EXTERNAL_PUBLIC, DataTypeClassifier.lawOperationKeys(), true);
    }

    private void runCategory(String dataTypeCd, Set<String> lawOperationKeys, boolean exclude) {
        BatchHandle handle = logCollectorBatchService.startCleanseBatch(
            dataTypeCd, ExecutionType.SCHEDULE, "scheduler:cleanse");

        CleanseResult result = cleanseService.cleansePending(lawOperationKeys, exclude);

        logCollectorBatchService.finishCleanseBatch(handle, result);
    }
}
