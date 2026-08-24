package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.model.FacilitySyncResult;
import egovframework.external.response.Response;
import egovframework.external.service.FacilitySyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 교정기관 목록 자동 동기화 조회/수동 트리거 API. {@code tb_dim_instt}(대시보드 관리 기관
 * 마스터) 대비 우리 시설 목록의 변경분을 확인하고 싶을 때 사용.
 */
@Tag(name = "PublicData-FacilitySync", description = "교정기관 목록 자동 동기화(tb_dim_instt 대조) 조회/수동 트리거 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/public-data/facility-sync")
public class FacilitySyncController {

    private final FacilitySyncService facilitySyncService;

    /**
     * {@code GET /public-data/facility-sync/queue} - 현재 PENDING인 검토 큐 전체 조회
     * (신규/제외검토 항목, 오래된 순).
     */
    @AdminCallable
    @GetMapping("/queue")
    public Callable<Response<Object>> queue() {
        return () -> Response.of(facilitySyncService.pendingQueue());
    }

    /**
     * {@code POST /public-data/facility-sync/run} - 스케줄(매일 새벽 4시)과 무관하게 지금
     * 바로 동기화 실행. {@code public-data.facility-sync.enabled=false}(기본값)면 아무 것도
     * 안 하고 {@code {"newCount": 0, "removedCount": 0}}을 반환한다.
     */
    @AdminCallable
    @PostMapping("/run")
    public Callable<Response<Object>> runManually() {
        return () -> {
            FacilitySyncResult result = facilitySyncService.sync();
            return Response.of(Map.of(
                "newCount", result.newCount(),
                "removedCount", result.removedCount()
            ));
        };
    }
}
