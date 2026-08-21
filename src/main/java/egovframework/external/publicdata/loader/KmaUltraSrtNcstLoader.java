package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.WeatherNcstMapper;
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
 * 초단기실황조회 적재 - {@code kcais.tb_ext_weather_ncst}. {@code KmaUltraSrtNcstCleanser}
 * 산출물엔 facilityId가 없어(nx/ny만 있음) {@link RawStagingDto#getFacilityId()}에서 가져온다.
 *
 * <p>{@code public-data.load.enabled=true}일 때만 빈으로 등록된다 - 꺼져있으면 이 빈이 의존하는
 * {@link WeatherNcstMapper}(MyBatis, AdminDbConfig 조건부) 자체가 없어서 그냥 빈 생성을
 * 안 하는 게 맞다(없으면 와이어링 에러가 남).</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KmaUltraSrtNcstLoader implements PublicDataLoader {

    private final WeatherNcstMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "kma-village-forecast-ultra-srt-ncst".equals(operationKey);
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
                p.put("baseDtm", baseDtm);
                for (String field : new String[]{"t1h", "rn1", "reh", "pty", "vec", "wsd", "uuu", "vvv"}) {
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
