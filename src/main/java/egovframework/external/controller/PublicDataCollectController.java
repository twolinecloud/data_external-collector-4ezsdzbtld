package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.CollectResult;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.publicdata.collector.PublicDataCollectorRegistry;
import egovframework.external.model.ExecutionType;
import egovframework.external.response.Response;
import egovframework.external.service.PublicDataCollectionAttemptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 공공데이터 수집 수동 트리거 API.
 *
 * <p>필요시 스케줄과 무관하게 소스 하나를 즉시 실행할 수 있게 한다.
 * 실행 로직은 {@code PublicDataCollectorScheduler}(자동 스케줄)와 완전히 동일한
 * {@link PublicDataCollectionAttemptService}를 공유하므로, 스케줄/수동 어느 경로로 실행하든
 * raw_staging/collection_attempt_log에 남는 결과는 동일하다 (실행유형만 MANUAL로 구분).</p>
 *
 * <p>{@code @AdminCallable}은 붙여뒀지만, 현재 {@code AuthInterceptor}가 이를 실제로
 * 강제하진 않는다 (로깅만 수행) — 인증/인가가 붙기 전까지는 의도 표기 용도.</p>
 */
@Tag(name = "PublicData-Collect", description = "공공데이터 수집 수동 트리거 / 조회 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/public-data/collect")
public class PublicDataCollectController {

    private final PublicDataCollectorRegistry collectorRegistry;
    private final PublicDataCollectionAttemptService collectionAttemptService;
    private final LogCollectorBatchService logCollectorBatchService;

    /**
     * {@code GET /public-data/collect} - 등록된 전체 컬렉터 목록 조회.
     *
     * <p>파라미터 없음. 위치독립 오퍼레이션 + 위치의존 오퍼레이션 3종 × 59개소 + 법령/행정규칙
     * 대상 목록(2026-08-28 기준 491건) + 생활기상지수 2종 × 16개 시도를 모두 합친 전체
     * 컬렉터가 반환된다(정확한 총 개수는 대상 CSV/DB 목록에 따라 바뀌므로 하드코딩하지 않음 -
     * 실측하려면 이 API를 직접 호출). 각 항목의 {@code key}가 바로 {@link #runManually(String)}에
     * 넘길 값 - 아래에서 {@code key} 조회 후 그 값으로 수동 실행하는 흐름으로 쓰면 된다.</p>
     *
     * <p>응답 배열 원소: {@code key}(수동 실행용 식별자), {@code sourceName}(공공데이터 출처명),
     * {@code apiName}(오퍼레이션명 - 위치의존이면 기관명이 괄호로 병기됨, 예:
     * "단기예보조회 (서울지방교정청)").</p>
     */
    @AdminCallable
    @GetMapping
    public Callable<Response<Object>> list() {
        return () -> Response.of(collectorRegistry.all().stream()
            .map(c -> Map.of(
                "key", c.key(),
                "sourceName", c.sourceName(),
                "apiName", c.apiName()
            ))
            .toList());
    }

    /**
     * {@code POST /public-data/collect/{key}/run} - 컬렉터 1개를 즉시 수동 실행.
     *
     * <p>스케줄과 무관하게 지금 바로 실행하고 싶을 때 사용. <b>이 엔드포인트는 요청 1건당
     * 컬렉터 1개(오퍼레이션 1개 × 기관 1곳)만 실행한다</b> - 위치의존 오퍼레이션의 59개소를
     * 한꺼번에 도는 기능은 없으며, 그건 {@code PublicDataCollectorScheduler}의 자동 스케줄만
     * 수행한다. 59개소 각각을 수동으로 돌리려면 이 API를 기관 수만큼 반복 호출해야 한다.</p>
     *
     * <p>실행 결과는 스케줄 실행과 동일한 {@link PublicDataCollectionAttemptService}를
     * 공유하므로 raw_staging/collection_attempt_log/메트릭에 남는 기록도 동일 - 실행유형만
     * {@code MANUAL}로 구분된다.</p>
     *
     * @param key {@code GET /public-data/collect}(위 {@link #list()})로 조회한 컬렉터 식별자.
     *            위치독립 오퍼레이션은 {@code "kma-weather-warning-list"}처럼 오퍼레이션명 그대로,
     *            위치의존 오퍼레이션은 {@code "{오퍼레이션}--{facilityId}"} 형태
     *            (예: {@code "kma-village-forecast-vilage-fcst--1270280"} = 단기예보조회, 대전지방교정청).
     *            존재하지 않는 key면 {@code NotFoundException}(HTTP 404)
     */
    @AdminCallable
    @PostMapping("/{key}/run")
    public Callable<Response<Object>> runManually(@PathVariable String key) {
        return () -> {
            PublicDataCollector collector = collectorRegistry.get(key);

            BatchHandle handle = logCollectorBatchService.startCollectBatch(
                collector.operationKey(), ExecutionType.MANUAL, "manual-api:" + collector.key());
            CollectResult result = collectionAttemptService.run(collector, ExecutionType.MANUAL);
            logCollectorBatchService.finishCollectBatch(handle, List.of(result));

            return Response.of(Map.of(
                "key", collector.key(),
                "sourceName", collector.sourceName(),
                "apiName", collector.apiName(),
                "triggeredAt", LocalDateTime.now()
            ));
        };
    }
}
