package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.LivingUvIdxMapper;
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
 * 자외선지수조회 적재 - {@code kcais.tb_ext_living_uv_idx}. {@code KmaLivingWthrIdxCleanser}
 * 산출물엔 이미 facilityId가 채워져 있음(시도 단위 팬아웃 완료) - 다른 로더들과 달리
 * {@link RawStagingDto#getFacilityId()}가 아니라 정제된 행 자체의 {@code facilityId}를 쓴다.
 *
 * <p>{@code date}(yyyyMMddHH)+{@code offsetHours}로 base_dtm/fcst_dtm을 계산 - 다른 예보
 * 테이블의 (baseDate+baseTime, fcstDate+fcstTime) 조합과 형식이 달라 별도 파싱
 * ({@link KmaDateTimeSupport#parseYyyyMMddHH}) 사용.</p>
 *
 * <p>{@code public-data.load.enabled=true}일 때만 빈으로 등록됨 - {@code KmaUltraSrtNcstLoader}
 * 참고.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KmaUVIdxLoader implements PublicDataLoader {

    private final LivingUvIdxMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "kma-living-uv-idx".equals(operationKey);
    }

    @Override
    public void load(RawStagingDto dto) throws LoadException {
        try {
            JSONArray rows = new JSONArray(dto.getCleansedPayload());
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Map<String, Object> p = new HashMap<>();
                p.put("id", Ulid.generate());
                p.put("facilityId", row.getString("facilityId"));
                p.put("areaNo", row.getString("areaNo"));
                p.put("idxCode", row.isNull("code") ? null : row.getString("code"));
                LocalDateTime baseDtm = KmaDateTimeSupport.parseYyyyMMddHH(row.getString("date"));
                p.put("baseDtm", baseDtm);
                p.put("fcstDtm", baseDtm.plusHours(row.getInt("offsetHours")));
                p.put("idxValue", row.getString("value"));
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
