package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.WeatherUltraFcstMapper;
import egovframework.external.utility.Ulid;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 초단기예보조회 적재 - {@code kcais.tb_ext_weather_ultra_fcst}. {@code KmaUltraSrtFcstCleanser}
 * 산출물엔 facilityId가 없어(nx/ny만 있음) {@link RawStagingDto#getFacilityId()}에서 가져온다.
 *
 * <p>{@code public-data.load.enabled=true}일 때만 빈으로 등록됨 - {@code KmaUltraSrtNcstLoader}
 * 참고.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KmaUltraSrtFcstLoader implements PublicDataLoader {

    private static final String[] FIELDS = {"t1h", "rn1", "sky", "uuu", "vvv", "reh", "pty", "pop", "lgt", "vec", "wsd"};

    private final WeatherUltraFcstMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "kma-village-forecast-ultra-srt-fcst".equals(operationKey);
    }

    @Override
    public void load(RawStagingDto dto) throws LoadException {
        try {
            JSONArray rows = new JSONArray(dto.getCleansedPayload());
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Map<String, Object> p = new HashMap<>();
                p.put("id", Ulid.generate());
                p.put("facilityId", dto.getFacilityId());
                p.put("nx", row.getInt("nx"));
                p.put("ny", row.getInt("ny"));
                LocalDateTime baseDtm = KmaDateTimeSupport.combine(row.getString("baseDate"), row.getString("baseTime"));
                LocalDateTime fcstDtm = KmaDateTimeSupport.combine(row.getString("fcstDate"), row.getString("fcstTime"));
                p.put("baseDtm", baseDtm);
                p.put("fcstDtm", fcstDtm);
                for (String field : FIELDS) {
                    p.put(field, row.isNull(field) ? null : row.getString(field));
                }
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
