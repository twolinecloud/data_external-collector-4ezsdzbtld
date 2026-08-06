package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.publicdata.collector.PublicDataCollectorRegistry;
import egovframework.external.model.ExecutionType;
import egovframework.external.response.Response;
import egovframework.external.service.PublicDataCollectionAttemptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

    @AdminCallable
    @PostMapping("/{key}/run")
    public Callable<Response<Object>> runManually(@PathVariable String key) {
        return () -> {
            PublicDataCollector collector = collectorRegistry.get(key);
            collectionAttemptService.run(collector, ExecutionType.MANUAL);
            return Response.of(Map.of(
                "key", collector.key(),
                "sourceName", collector.sourceName(),
                "apiName", collector.apiName(),
                "triggeredAt", LocalDateTime.now()
            ));
        };
    }
}
