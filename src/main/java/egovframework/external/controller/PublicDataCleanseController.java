package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.DataTypeClassifier;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.CleanseResult;
import egovframework.external.model.ExecutionType;
import egovframework.external.response.Response;
import egovframework.external.service.PublicDataCleanseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * 정제 수동 트리거 API. 스케줄과 무관하게 지금 쌓여있는 raw_staging COLLECTED 건을
 * 즉시 정제하고 싶을 때 사용 - {@link PublicDataCleanseService}를 스케줄러와 공유한다.
 */
@Tag(name = "PublicData-Cleanse", description = "raw_staging 정제 수동 트리거 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/public-data/cleanse")
public class PublicDataCleanseController {

    private final PublicDataCleanseService cleanseService;
    private final LogCollectorBatchService logCollectorBatchService;

    /**
     * {@code POST /public-data/cleanse/run} - raw_staging의 COLLECTED 건을 즉시 정제.
     *
     * <p>파라미터 없음. 대상을 고를 수 없고 <b>호출 시점에 COLLECTED 상태인 행 전부</b>를
     * (100건씩 배치로) 소진될 때까지 처리한다 - 특정 기관/오퍼레이션만 골라 정제하는 기능은
     * 없음. 5분 주기 자동 스케줄({@code PublicDataCleanseScheduler})과 동일한
     * {@link PublicDataCleanseService}를 공유하므로, 지금 바로 처리하고 싶을 때(적체 확인,
     * 디버깅 등) 스케줄을 기다리지 않고 쓰면 된다.</p>
     *
     * <p>정제기를 못 찾거나 정제 중 오류가 나도 그 행만 {@code CLEANSE_FAILED}로 남고 호출
     * 자체는 실패하지 않는다 - 응답의 {@code processed}는 성공/실패 구분 없이 처리를
     * "시도"한 총 건수.</p>
     *
     * @return {@code {"processed": N}} - 이번 호출로 처리된(성공+실패 합계) 건수
     */
    @AdminCallable
    @PostMapping("/run")
    public Callable<Response<Object>> runManually() {
        return () -> {
            // 스케줄러와 동일하게 EXTERNAL_LAW/EXTERNAL_PUBLIC 배치로 나눠서 보고한다
            // (2026-08-27, PublicDataCleanseScheduler 참고) - 응답의 processed는 둘의 합계.
            int lawProcessed = runCategory(DataTypeClassifier.EXTERNAL_LAW, DataTypeClassifier.lawOperationKeys(), false);
            int publicProcessed = runCategory(DataTypeClassifier.EXTERNAL_PUBLIC, DataTypeClassifier.lawOperationKeys(), true);
            return Response.of(Map.of("processed", lawProcessed + publicProcessed));
        };
    }

    private int runCategory(String dataTypeCd, Set<String> lawOperationKeys, boolean exclude) {
        BatchHandle handle = logCollectorBatchService.startCleanseBatch(
            dataTypeCd, ExecutionType.MANUAL, "manual-api:cleanse");

        CleanseResult result = cleanseService.cleansePending(lawOperationKeys, exclude);

        logCollectorBatchService.finishCleanseBatch(handle, result);
        return result.totalProcessed();
    }
}
