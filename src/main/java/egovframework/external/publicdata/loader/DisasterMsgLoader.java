package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.DisasterMsgMapper;
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
 * 긴급재난문자 적재 - {@code kcais.tb_ext_disaster_msg}. {@code DisasterMsgCleanser}가 이미
 * facilityId까지 매칭해서 (sn, facilityId) 조합 1건당 1행으로 내놓으므로, 여기선 그대로
 * upsert만 한다.
 *
 * <p>{@code public-data.load.enabled=true}일 때만 빈으로 등록됨 - {@code KmaUltraSrtNcstLoader}
 * 참고.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DisasterMsgLoader implements PublicDataLoader {

    private final DisasterMsgMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "safetydata-disaster-msg-list".equals(operationKey);
    }

    @Override
    public void load(RawStagingDto dto) throws LoadException {
        try {
            JSONArray rows = new JSONArray(dto.getCleansedPayload());
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Map<String, Object> p = new HashMap<>();
                p.put("id", Ulid.generate());
                p.put("sn", row.get("sn").toString());
                p.put("facilityId", row.getString("facilityId"));
                p.put("matchedRegionNm", row.getString("matchedRegionNm"));
                // crtDtm은 DisasterMsgCleanser가 이미 LocalDateTime.toString() 형식으로
                // 저장해둠(ISO) - 기본 파서로 그대로 읽힘
                p.put("crtDtm", LocalDateTime.parse(row.getString("crtDtm")));
                p.put("msgCn", row.getString("msgCn"));
                p.put("emrgStepNm", row.getString("emrgStepNm"));
                p.put("dstSeNm", row.getString("dstSeNm"));
                p.put("rcptnRgnNmRaw", row.getString("rcptnRgnNmRaw"));
                p.put("regDe", row.isNull("regDe") ? null : row.get("regDe").toString());
                p.put("mdfcnDe", row.isNull("mdfcnDe") ? null : row.get("mdfcnDe").toString());
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
