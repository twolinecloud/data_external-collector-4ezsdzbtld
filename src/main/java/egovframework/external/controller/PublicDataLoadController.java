package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.model.ExecutionType;
import egovframework.external.model.LoadResult;
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
 * 적재(Load) 수동 트리거 API. 스케줄과 무관하게 지금 쌓여있는 raw_staging CLEANSED 건을
 * 즉시 admin-db에 적재하고 싶을 때 사용 - 스케줄러와 같은 {@link PublicDataPipelineRunner}를 쓴다.
 */
@Tag(name = "PublicData-Load", description = "raw_staging 적재(admin-db) 수동 트리거 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/public-data/load")
public class PublicDataLoadController {

    private final PublicDataPipelineRunner pipelineRunner;

    /**
     * {@code POST /public-data/load/run} - raw_staging의 CLEANSED 건을 즉시 admin-db에 적재.
     *
     * <p>파라미터 없음. {@code public-data.load.enabled=false}(기본값)면 아무 것도 안 하고
     * {@code {"processed": 0}}을 반환한다. 켜져있으면 호출 시점에 CLEANSED 상태인 행을 수집
     * 실행(run) 단위로 전부 처리하고(아직 정제 중인 run은 건너뜀), 이전에 실패한 행(LOAD_FAILED)도
     * 한 번 재시도한다.</p>
     *
     * <p>적재기를 못 찾거나 적재 중 오류가 나도 그 행만 {@code LOAD_FAILED}로 남고 호출
     * 자체는 실패하지 않는다.</p>
     *
     * @return {@code {"processed": N}} - 이번 호출로 처리된(성공+실패 합계) 건수
     */
    @AdminCallable
    @PostMapping("/run")
    public Callable<Response<Object>> runManually() {
        return () -> {
            LoadResult result = pipelineRunner.load(ExecutionType.MANUAL, "manual-api:load");
            return Response.of(Map.of("processed", result.totalProcessed()));
        };
    }
}
