package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 초단기예보(getUltraSrtFcst) 정제기. 6시간치를 (fcstDate,fcstTime) 단위로 묶어
 * 최대 6행으로 피벗 - 실제 로직은 {@link KmaForecastPivotSupport} 공유.
 */
@Component
public class KmaUltraSrtFcstCleanser implements PublicDataCleanser {

    /** weather-api.docx 기준 초단기예보 카테고리. */
    private static final List<String> CATEGORIES =
        List.of("T1H", "RN1", "SKY", "UUU", "VVV", "REH", "PTY", "POP", "LGT", "VEC", "WSD");

    @Override
    public boolean supports(String operationKey) {
        return "kma-village-forecast-ultra-srt-fcst".equals(operationKey);
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            return KmaForecastPivotSupport.pivotByForecastTime(rawPayload, CATEGORIES);
        } catch (Exception e) {
            throw new CleanseException("공공데이터포털 (기상청 동네예보)", "초단기예보조회", "정제 실패: " + e.getMessage(), e);
        }
    }
}
