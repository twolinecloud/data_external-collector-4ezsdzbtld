package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.response.Response;
import egovframework.external.rule.AlertLevel;
import egovframework.external.rule.AlertResult;
import egovframework.external.rule.RuleEvaluationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 지형 기반 rule-base 재해 알림 평가 수동 트리거 API (private-doc/terrain-rule-base-spec.md).
 * {@link PublicDataCleanseController}와 같은 패턴 - CLEANSED 상태로 남아있는 날씨/재난문자
 * 정제 결과를 지금 바로 평가하고 싶을 때 스케줄 없이 사용.
 */
@Tag(name = "PublicData-Alert", description = "지형 기반 rule-base 재해 알림 평가 수동 트리거 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/public-data/alert")
public class RuleEvaluationController {

    private final RuleEvaluationService ruleEvaluationService;

    /**
     * {@code POST /public-data/alert/run} - 시설 59개소 × 재해 2종(산사태/침수) 전량 재평가.
     *
     * @return {@code evaluated}(총 평가 건수, 항상 118) / {@code alerting}(등급이 "없음"이 아닌 건수)
     */
    @AdminCallable
    @PostMapping("/run")
    public Callable<Response<Object>> runManually() {
        return () -> {
            List<AlertResult> results = ruleEvaluationService.evaluateAll();
            long alerting = results.stream().filter(r -> r.level() != AlertLevel.NONE).count();
            return Response.of(Map.of(
                "evaluated", results.size(),
                "alerting", alerting
            ));
        };
    }
}
