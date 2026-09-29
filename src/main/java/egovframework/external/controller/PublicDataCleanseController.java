package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.model.CleanseResult;
import egovframework.external.model.ExecutionType;
import egovframework.external.response.Response;
import egovframework.external.service.PublicDataPipelineRunner;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 정제 수동 트리거 API. 스케줄과 무관하게 지금 쌓여있는 raw_staging COLLECTED 건을
 * 즉시 정제하고 싶을 때 사용 - 스케줄러와 같은 {@link PublicDataPipelineRunner}를 쓴다.
 */
@Tag(name = "PublicData-Cleanse", description = "raw_staging 정제 수동 트리거 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/public-data/cleanse")
public class PublicDataCleanseController {

    private final PublicDataPipelineRunner pipelineRunner;

    /**
     * {@code POST /public-data/cleanse/run} - raw_staging의 COLLECTED 건을 즉시 정제.
     *
     * <p>파라미터 없음. 호출 시점에 COLLECTED 상태인 행을 수집 실행(run) 단위로 전부 처리한다 -
     * 특정 기관/오퍼레이션만 골라 정제하는 기능은 없음. 아직 수집 중인 run은 건너뛴다(다음
     * 스케줄에서 처리). 5분 주기 자동 스케줄({@code PublicDataCleanseScheduler})과 같은 경로라
     * 로그 컬렉터에도 같은 방식(수집 execId에 CLEANSE 스텝 이어붙이기)으로 남는다.</p>
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
            CleanseResult result = pipelineRunner.cleanse(ExecutionType.MANUAL, "manual-api:cleanse");
            return Response.of(Map.of("processed", result.totalProcessed()));
        };
    }
}
