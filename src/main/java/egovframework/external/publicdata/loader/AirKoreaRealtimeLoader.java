package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.AirQualityMapper;
import egovframework.external.utility.PipelineLogUtils;
import egovframework.external.utility.Ulid;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 에어코리아 시도별 실시간 대기오염정보 적재 - {@code kcais.tb_ext_air_quality}.
 * {@code AirKoreaRealtimeCleanser}가 이미 기관마다 최근접 측정소로 매칭해서 1행씩 넘겨주므로,
 * 여기선 컬럼별로 옮겨 담기만 한다.
 *
 * <p><b>PM10이지 황사가 아니다</b> - {@link AirQualityMapper} 클래스 주석 참고.
 * <b>판정 없이 원본 저장(2026-09-04)</b>: ASOS와 같은 원칙 - 어느 농도부터 황사로 볼지는
 * 기획 확정 전이라 조회 단(백엔드)에 맡긴다.</p>
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
public class AirKoreaRealtimeLoader implements PublicDataLoader {

    private static final Logger logger = LogManager.getLogger(AirKoreaRealtimeLoader.class);
    private static final String STAGE = "LOAD";

    /** 에어코리아 dataTime 형식(예: "2026-09-03 14:00") - ASOS의 TM(12자리 붙은 형태)과 다르다. */
    private static final DateTimeFormatter DATA_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** raw_json 재구성에 쓰는 원본 필드 목록 - AirKoreaRealtimeCleanser.RAW_ITEM_FIELDS와 동일
     * (패키지가 달라 상수 공유 대신 KmaWeatherWarningListLoader처럼 여기서 다시 명시). */
    private static final String[] RAW_FIELDS = {
        "stationName", "sidoName", "dataTime",
        "pm10Value", "pm10Grade", "pm10Flag", "pm25Value", "pm25Grade", "pm25Flag",
        "khaiValue", "khaiGrade",
        "so2Value", "so2Grade", "so2Flag", "coValue", "coGrade", "coFlag",
        "o3Value", "o3Grade", "o3Flag", "no2Value", "no2Grade", "no2Flag"
    };

    private final AirQualityMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "airkorea-realtime-measure".equals(operationKey);
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
        p.put("stationNm", row.getString("stationName"));
        p.put("sidoNm", nullable(row, "sidoName"));
        p.put("baseDtm", LocalDateTime.parse(row.getString("dataTime"), DATA_TIME_FMT));
        p.put("pm10Value", nullable(row, "pm10Value"));
        p.put("pm10Grade", nullable(row, "pm10Grade"));
        p.put("pm10Flag", nullable(row, "pm10Flag"));
        p.put("pm25Value", nullable(row, "pm25Value"));
        p.put("pm25Grade", nullable(row, "pm25Grade"));
        p.put("khaiValue", nullable(row, "khaiValue"));
        p.put("khaiGrade", nullable(row, "khaiGrade"));
        // raw_json은 "원본 응답 필드 그대로"가 취지 - facilityId/stationDistanceKm은 우리가
        // 매칭으로 얹은 값이라 여기 안 섞이게 원본 필드만으로 별도 구성한다
        // (KmaWeatherWarningListLoader의 rawOnly 패턴과 동일).
        JSONObject rawOnly = new JSONObject();
        for (String field : RAW_FIELDS) {
            rawOnly.put(field, nullable(row, field));
        }
        p.put("rawJson", rawOnly.toString());
        p.put("operationKey", dto.getOperationKey());
        p.put("collectDtm", dto.getCollectedAt());
        p.put("cleanseDtm", dto.getCleansedAt());
        return p;
    }

    /** 에어코리아 응답은 결측이 JSON null로 오는 필드가 섞여 있다(예: coFlag) - isNull 방어. */
    private String nullable(JSONObject row, String field) {
        return row.isNull(field) ? null : row.getString(field);
    }

    private String rowLabel(JSONArray rows, int index) {
        try {
            JSONObject row = rows.getJSONObject(index);
            return "(facilityId=" + row.opt("facilityId") + ", station=" + row.opt("stationName") + ")";
        } catch (Exception e) {
            return "(index=" + index + ")";
        }
    }
}
