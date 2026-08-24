package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.WeatherWarningMapper;
import egovframework.external.utility.Ulid;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 기상특보목록조회 적재 - {@code kcais.tb_ext_weather_warning}. {@code stnId}(시도 단위
 * 관할구역)를 {@code KmaWeatherWarningListCleanser}가 매칭된 교정기관 수만큼 행으로 복제해서
 * 넘겨주므로, 정제결과 각 행엔 이미 {@code facilityId}가 채워져 있다(2026-08-21 추가).
 *
 * <p>{@code public-data.load.enabled=true}일 때만 빈으로 등록됨 - {@code KmaUltraSrtNcstLoader}
 * 참고.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KmaWeatherWarningListLoader implements PublicDataLoader {

    private static final DateTimeFormatter TM_FC_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final WeatherWarningMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "kma-weather-warning-list".equals(operationKey);
    }

    @Override
    public void load(RawStagingDto dto) throws LoadException {
        try {
            JSONArray rows = new JSONArray(dto.getCleansedPayload());
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Map<String, Object> p = new HashMap<>();
                p.put("id", Ulid.generate());
                p.put("stnId", row.getString("stnId"));
                // tmFc는 JSON 숫자(YYYYMMDDHHmm, 예: 202608211020) - cleanse-db-schema-spec.md
                // §2.5 실측대로 문자열로 재포맷해서 TIMESTAMP로 파싱
                LocalDateTime tmFcDtm = LocalDateTime.parse(String.valueOf(row.getLong("tmFc")), TM_FC_FMT);
                p.put("tmFcDtm", tmFcDtm);
                p.put("tmSeq", row.getInt("tmSeq"));
                p.put("title", row.getString("title"));
                p.put("facilityId", row.getString("facilityId"));
                // raw_json은 "원본 특보 필드 그대로"가 취지 - 우리가 매칭으로 추가한 facilityId는
                // 여기 안 섞이게 원본 4필드만으로 별도 구성해서 저장한다.
                JSONObject rawOnly = new JSONObject();
                rawOnly.put("stnId", row.getString("stnId"));
                rawOnly.put("title", row.getString("title"));
                rawOnly.put("tmFc", row.get("tmFc"));
                rawOnly.put("tmSeq", row.get("tmSeq"));
                p.put("rawJson", rawOnly.toString());
                p.put("operationKey", dto.getOperationKey());
                p.put("collectDtm", dto.getCollectedAt());
                p.put("cleanseDtm", dto.getCleansedAt());
                mapper.upsert(p);
            }
        } catch (Exception e) {
            throw new LoadException(dto.getSourceName(), dto.getApiName(), "적재 실패: " + e.getMessage(), e);
        }
    }
}
