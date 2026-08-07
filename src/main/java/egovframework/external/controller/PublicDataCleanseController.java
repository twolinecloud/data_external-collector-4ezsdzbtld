package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.response.Response;
import egovframework.external.service.PublicDataCleanseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
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

    @AdminCallable
    @PostMapping("/run")
    public Callable<Response<Object>> runManually() {
        return () -> {
            int processed = cleanseService.cleanseAllPending();
            return Response.of(Map.of("processed", processed));
        };
    }
}
