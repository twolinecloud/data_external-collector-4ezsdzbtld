package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.model.PurgeResult;
import egovframework.external.response.Response;
import egovframework.external.service.PublicDataPurgeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * admin-db 보존기간 초과 데이터 수동 폐기(purge) 트리거 API. 스케줄(매일 새벽 3시)과
 * 무관하게 지금 바로 정리하고 싶을 때 사용 - {@link PublicDataPurgeService}를 스케줄러와 공유한다.
 */
@Tag(name = "PublicData-Purge", description = "admin-db 보존기간 초과 데이터 수동 폐기(purge) API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/public-data/purge")
public class PublicDataPurgeController {

    private final PublicDataPurgeService purgeService;

    /**
     * {@code POST /public-data/purge/run} - 보존기간({@code public-data.purge.retention-days},
     * 기본 30일) 초과 데이터를 즉시 삭제.
     *
     * <p>파라미터 없음. {@code public-data.purge.enabled=false}(기본값)면 아무 것도 안 하고
     * {@code {"totalDeleted": 0, "successTableCount": 0, "failTableCount": 0}}을 반환한다.</p>
     *
     * @return 삭제 총 건수 / 성공한 테이블 수 / 실패한 테이블 수
     */
    @AdminCallable
    @PostMapping("/run")
    public Callable<Response<Object>> runManually() {
        return () -> {
            PurgeResult result = purgeService.purgeExpired();
            return Response.of(Map.of(
                "totalDeleted", result.totalDeleted(),
                "successTableCount", result.successTableCount(),
                "failTableCount", result.failTableCount()
            ));
        };
    }
}
