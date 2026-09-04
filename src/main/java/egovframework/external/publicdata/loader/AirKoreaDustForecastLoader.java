package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.DustForecastMapper;
import egovframework.external.utility.PipelineLogUtils;
import egovframework.external.utility.Ulid;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 에어코리아 대기질예보통보 적재 - {@code kcais.tb_ext_dust_forecast}.
 * {@code AirKoreaDustForecastCleanser}가 이미 기관마다 예보권역으로 매칭해서 1행씩 넘겨주므로,
 * 여기선 컬럼별로 옮겨 담기만 한다.
 *
 * <p>PM10 예보이지 황사 예보가 아니다 - {@link DustForecastMapper} 클래스 주석 참고.
 * 등급(좋음/보통/나쁨/매우나쁨)은 API가 이미 계산해서 주므로 판정 로직이 따로 없다 - 다만
 * 어느 등급부터 화면에 "황사"로 띄울지는 기획 확정 대기.</p>
 *
 * <p><b>행 단위 예외 처리</b>: {@code DisasterMsgLoader}(2026-08-31)와 같은 원칙 -
 * {@code KmaAsosHourlyLoader} 클래스 주석 참고.</p>
 *
 * <p>{@code public-data.load.enabled=true}일 때만 빈으로 등록됨 - {@code KmaUltraSrtNcstLoader}
 * 참고.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AirKoreaDustForecastLoader implements PublicDataLoader {

    private static final Logger logger = LogManager.getLogger(AirKoreaDustForecastLoader.class);
    private static final String STAGE = "LOAD";

    private final DustForecastMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "airkorea-dust-forecast".equals(operationKey);
    }

    @Override
    public void load(RawStagingDto dto) throws LoadException {
        JSONArray rows;
        try {
            rows = new JSONArray(dto.getCleansedPayload());
        } catch (Exception e) {
            throw new LoadException(dto.getSourceName(), dto.getApiName(),
                "적재 실패: 정제결과 파싱 불가 - " + e.getMessage(), e);
        }

        int failed = 0;
        String firstFailure = null;
        for (int i = 0; i < rows.length(); i++) {
            try {
                mapper.upsert(toParams(rows.getJSONObject(i), dto));
            } catch (Exception e) {
                failed++;
                String label = rowLabel(rows, i);
                if (firstFailure == null) {
                    firstFailure = label + " " + e.getMessage();
                }
                PipelineLogUtils.warn(logger, STAGE, dto.getSourceName(), dto.getApiName(),
                    "행 적재 건너뜀 " + label + " - " + e.getMessage());
            }
        }

        if (failed > 0) {
            throw new LoadException(dto.getSourceName(), dto.getApiName(),
                "적재 실패: " + rows.length() + "행 중 " + failed + "행 실패, 나머지는 적재됨"
                    + " (첫 실패 " + firstFailure + ")");
        }
    }

    private Map<String, Object> toParams(JSONObject row, RawStagingDto dto) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", Ulid.generate());
        p.put("facilityId", row.getString("facilityId"));
        p.put("informRegion", row.getString("informRegion"));
        p.put("baseDtm", parseDataTime(row.getString("dataTime")));
        p.put("fcstDtm", LocalDate.parse(row.getString("informData")).atStartOfDay());
        p.put("grade", row.getString("grade"));
        p.put("informCause", row.optString("informCause", null));
        // raw_json은 "원본 응답 필드 그대로"가 취지 - facilityId/informRegion은 우리가
        // 매칭으로 얹은 값이라 여기 안 섞이게 원본 필드만으로 별도 구성한다
        // (KmaWeatherWarningListLoader의 rawOnly 패턴과 동일).
        JSONObject rawOnly = new JSONObject();
        rawOnly.put("informData", row.getString("informData"));
        rawOnly.put("dataTime", row.getString("dataTime"));
        rawOnly.put("grade", row.getString("grade"));
        rawOnly.put("informCause", row.optString("informCause", ""));
        rawOnly.put("informOverall", row.optString("informOverall", ""));
        p.put("rawJson", rawOnly.toString());
        p.put("operationKey", dto.getOperationKey());
        p.put("collectDtm", dto.getCollectedAt());
        p.put("cleanseDtm", dto.getCleansedAt());
        return p;
    }

    /** "2026-09-04 05시 발표" -> LocalDateTime - AirKoreaDustForecastCleanser의 파서와 동일 형식. */
    private LocalDateTime parseDataTime(String dataTime) {
        String cleaned = dataTime.replace("발표", "").trim();
        String[] parts = cleaned.split("\\s+");
        LocalDate date = LocalDate.parse(parts[0]);
        int hour = Integer.parseInt(parts[1].replace("시", ""));
        return date.atTime(hour, 0);
    }

    private String rowLabel(JSONArray rows, int index) {
        try {
            JSONObject row = rows.getJSONObject(index);
            return "(facilityId=" + row.opt("facilityId") + ", informData=" + row.opt("informData") + ")";
        } catch (Exception e) {
            return "(index=" + index + ")";
        }
    }
}
