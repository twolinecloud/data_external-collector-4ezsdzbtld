package egovframework.external.service;

import egovframework.external.dto.ScheduleRefreshRequest;
import egovframework.external.exception.InvalidParameterException;
import egovframework.external.exception.NotFoundException;
import egovframework.external.logcollector.DataTypeClassifier;
import egovframework.external.publicdata.scheduler.ScheduleActivation;
import egovframework.external.publicdata.scheduler.ScheduleCodes;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 관리자 서비스가 스케줄을 바꾸고 나서 보내는 즉시 반영 요청을 처리한다.
 *
 * <p>호출을 놓쳐도 {@code ScheduleSettingSync}가 최대 5분 안에 DB에서 따라잡으므로 정합성을 책임지지 않는다 -
 * 반영 지연을 줄이는 용도다. 사용 여부(activeYn)만 적용하고, 실행 시각(execSchedTypeCd/schedVal)은 받아서 로그만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class ScheduleRefreshService {

    private static final Logger logger = LogManager.getLogger(ScheduleRefreshService.class);

    private static final Set<String> OWNED_DATA_TYPES = Set.of(DataTypeClassifier.EXTERNAL_PUBLIC, DataTypeClassifier.EXTERNAL_LAW);

    private final ScheduleActivation activation;

    public void apply(ScheduleRefreshRequest request) {
        if (request == null || !OWNED_DATA_TYPES.contains(request.dataTypeCd())) {
            throw new InvalidParameterException(logger, "이 수집기가 담당하지 않는 데이터 구분입니다: "
                + (request == null ? null : request.dataTypeCd()));
        }
        String activeYn = request.activeYn() == null ? null : request.activeYn().trim();
        if (!"Y".equals(activeYn) && !"N".equals(activeYn)) {
            throw new InvalidParameterException(logger, "activeYn 은 Y 또는 N 이어야 합니다: " + request.activeYn());
        }

        String schdCd = (request.schdCd() == null || request.schdCd().isBlank()) ? null : request.schdCd().trim();
        if (schdCd != null) {
            String operationKey = ScheduleCodes.operationOf(schdCd)
                .orElseThrow(() -> new NotFoundException(logger, "알 수 없는 스케줄 코드입니다: " + schdCd));
            if (!DataTypeClassifier.dataTypeCd(operationKey).equals(request.dataTypeCd())) {
                throw new InvalidParameterException(logger, "스케줄 코드가 데이터 구분과 맞지 않습니다: dataTypeCd="
                    + request.dataTypeCd() + ", schdCd=" + schdCd);
            }
        }

        activation.apply(request.dataTypeCd(), schdCd, "Y".equals(activeYn));
        logger.info("[SCHEDULE] 즉시 반영 요청 처리 dataTypeCd={} schdCd={} activeYn={} (execSchedTypeCd={}, schedVal={} 은 반영하지 않음 - cron 유지)",
            request.dataTypeCd(), schdCd, activeYn, request.execSchedTypeCd(), request.schedVal());
    }
}
