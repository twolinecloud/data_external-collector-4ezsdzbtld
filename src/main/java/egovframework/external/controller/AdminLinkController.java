package egovframework.external.controller;

import egovframework.external.annotation.AdminCallable;
import egovframework.external.dto.ScheduleRefreshRequest;
import egovframework.external.response.Response;
import egovframework.external.service.CollectorManualRunService;
import egovframework.external.service.ScheduleRefreshService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 관리자 서비스(admin-api)가 호출하는 내부 API - 바로 실행과 스케줄 즉시 반영.
 *
 * <p>호출 주소는 관리자 DB의 스케줄 행(TB_BATCH_SCHEDULE_SETTING)에 저장된 전체 URL이다. 공공 연계 데이터의 세부
 * 스케줄은 오퍼레이션마다 행이 있으므로 {@code RUN_URL}에 오퍼레이션을 경로로 넣는다.
 * <b>긴급 재처리는 제공하지 않는다</b> - 날씨·법령은 호출 시점의 최신 값을 가져와 원래 execId를 다시 처리할 의미가 없다.
 * 관리자 DB의 {@code REPROCESS_URL}을 비워 두면 화면의 재처리 버튼이 숨겨지고, 실패 복구는 바로 실행으로 한다.
 */
@Tag(name = "Admin-Link", description = "관리자 서비스 연동용 내부 API (바로 실행 / 스케줄 즉시 반영)")
@RequiredArgsConstructor
@RestController
public class AdminLinkController {

    private static final Logger logger = LogManager.getLogger(AdminLinkController.class);

    private final CollectorManualRunService manualRunService;
    private final ScheduleRefreshService scheduleRefreshService;

    /**
     * {@code POST /internal/collect/external-incremental/{operationKey}?triggerBy=&to=} - 오퍼레이션 바로 실행.
     *
     * <p>비동기 실행이라 즉시 202와 {@code execId}를 돌려준다(진행·결과는 배치 수행 이력에서 본다).
     * {@code to}는 정형·비정형과 같은 형식을 맞추려고 받지만 쓰지 않는다 - 항상 호출 시점 기준으로 수집한다.
     *
     * @param operationKey 오퍼레이션 키 또는 스케줄 코드(예: {@code kma-asos-hourly}, {@code KMA_ASOS_HOURLY})
     * @param triggerBy    요청한 관리자 ID(필수)
     */
    @AdminCallable
    @PostMapping("/internal/collect/external-incremental/{operationKey}")
    public Callable<ResponseEntity<Response<Object>>> runNow(@PathVariable String operationKey,
            @RequestParam(required = false) String triggerBy, @RequestParam(required = false) String to) {
        return () -> {
            if (to != null) {
                logger.info("[MANUAL] to 파라미터는 사용하지 않음(호출 시점 기준 수집) operationKey={} to={}", operationKey, to);
            }
            CollectorManualRunService.Started started = manualRunService.start(operationKey, triggerBy);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("operationKey", started.operationKey());
            body.put("dataTypeCd", started.dataTypeCd());
            body.put("execId", started.execId());
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new Response<>(true, 0, HttpStatus.ACCEPTED.value(), body));
        };
    }

    /**
     * {@code POST /internal/schedule/refresh} - 스케줄 변경 즉시 반영. <b>사용 여부(activeYn)만</b> 적용한다.
     * 호출을 놓쳐도 5분마다 DB에서 따라잡는다.
     */
    @AdminCallable
    @PostMapping("/internal/schedule/refresh")
    public Callable<Response<Object>> refresh(@RequestBody ScheduleRefreshRequest request) {
        return () -> {
            scheduleRefreshService.apply(request);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("dataTypeCd", request.dataTypeCd());
            body.put("schdCd", request.schdCd());
            body.put("activeYn", request.activeYn());
            body.put("applied", "activeYn");
            return Response.of(body);
        };
    }
}
