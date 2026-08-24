package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.LivingAirDiffusionIdxMapper;
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
 * 대기정체지수조회 적재 - {@code kcais.tb_ext_living_air_diffusion_idx}. {@link KmaUVIdxLoader}와
 * 완전히 동일한 패턴(오퍼레이션/테이블만 다름) - {@code KmaLivingWthrIdxCleanser} 클래스 주석 참고.
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KmaAirDiffusionIdxLoader implements PublicDataLoader {

    private final LivingAirDiffusionIdxMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "kma-living-air-diffusion-idx".equals(operationKey);
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
