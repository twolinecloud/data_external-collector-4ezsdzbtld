package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 단기예보(getVilageFcst) 정제기. 최대 5일치를 (fcstDate,fcstTime) 단위로 묶어
 * 최대 ~72행으로 피벗 - 실제 로직은 {@link KmaForecastPivotSupport} 공유.
 * TMN/TMX(최저/최고기온)는 하루 1~2번만 나오므로 대부분 시간대 행에서는 null.
 */
@Component
public class KmaVilageFcstCleanser implements PublicDataCleanser {

    /** 실측 응답에서 확인된 단기예보 카테고리 14종 (실제 API로 확인). */
    private static final List<String> CATEGORIES =
        List.of("POP", "PTY", "PCP", "REH", "SNO", "SKY", "TMP", "TMN", "TMX", "UUU", "VEC", "VVV", "WAV", "WSD");

    @Override
    public boolean supports(String operationKey) {
        return "kma-village-forecast-vilage-fcst".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(new StructureProbe("raw-item",
            KmaForecastPivotSupport.RAW_ITEM_FIELDS, KmaForecastPivotSupport.RAW_ITEM_FIELDS,
            StructureProbeSupport::unionKeys));
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            return KmaForecastPivotSupport.pivotByForecastTime(rawPayload, CATEGORIES);
        } catch (Exception e) {
            throw new CleanseException("공공데이터포털 (기상청 동네예보)", "단기예보조회", "정제 실패: " + e.getMessage(), e);
        }
    }
}
