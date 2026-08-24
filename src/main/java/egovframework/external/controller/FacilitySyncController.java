package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.model.FacilitySyncResult;
import egovframework.external.response.Response;
import egovframework.external.service.FacilitySyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 교정기관 목록 자동 동기화 조회/수동 트리거/검토 승인 API. {@code tb_dim_instt}(대시보드
 * 관리 기관 마스터) 대비 우리 시설 목록의 변경분을 확인·확정하고 싶을 때 사용.
 *
 * <p>{@code public-data.facility-sync.enabled=true}일 때만 빈으로 등록됨(2026-08-24 수정) -
 * {@code FacilitySyncService} 클래스 주석 참고(꺼져있으면 이 API 자체가 404).</p>
 */
@Tag(name = "PublicData-FacilitySync", description = "교정기관 목록 자동 동기화(tb_dim_instt 대조) 조회/수동 트리거/승인 API")
@RequiredArgsConstructor
@RestController
@ConditionalOnProperty(prefix = "public-data.facility-sync", name = "enabled", havingValue = "true")
@RequestMapping("/public-data/facility-sync")
public class FacilitySyncController {

    private final FacilitySyncService facilitySyncService;

    /**
     * {@code GET /public-data/facility-sync/queue} - 현재 PENDING인 검토 큐 전체 조회
     * (신규/제외검토 항목, 오래된 순). 신규 항목은 자동 지오코딩 결과(성공 시 proposed_lat/
     * proposed_lon/proposed_nx/proposed_ny, 실패 시 geocode_status만)가 같이 나온다.
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

    /**
     * {@code POST /public-data/facility-sync/queue/{reviewId}/approve} - 검토 큐 항목을
     * 확정해서 {@code tb_ext_weather_facility}에 즉시 반영한다. 자동 지오코딩이 실패했던
     * 항목은 요청 바디에 {@code lat}/{@code lon}을 직접 넣어야 한다(사람이 조사한 좌표).
     * 자동 지오코딩이 성공한 항목은 바디 없이 호출하면 그 제안값을 그대로 쓴다.
     *
     * <p><b>주의</b>: admin-db엔 즉시 반영되지만 실제 날씨 수집(스케줄러)은 여전히
     * {@code kma-facility-locations.csv} 기준이라, 수집을 시작하려면 CSV도 별도로
     * 수동 반영해야 한다(Phase C 미구현).</p>
     *
     * @param body {@code {"lat": 37.42, "lon": 126.98}} - 선택, 생략 시 자동 지오코딩 제안값 사용
     */
    @AdminCallable
    @PostMapping("/queue/{reviewId}/approve")
    public Callable<Response<Object>> approve(@PathVariable String reviewId,
            @RequestBody(required = false) Map<String, Double> body) {
        return () -> {
            Double lat = body == null ? null : body.get("lat");
            Double lon = body == null ? null : body.get("lon");
            facilitySyncService.approve(reviewId, lat, lon);
            return Response.of(Map.of("reviewId", reviewId, "status", "RESOLVED"));
        };
    }

    /** {@code POST /public-data/facility-sync/queue/{reviewId}/reject} - 검토 큐 항목 무시 처리. */
    @AdminCallable
    @PostMapping("/queue/{reviewId}/reject")
    public Callable<Response<Object>> reject(@PathVariable String reviewId) {
        return () -> {
            facilitySyncService.reject(reviewId);
            return Response.of(Map.of("reviewId", reviewId, "status", "IGNORED"));
        };
    }
}
