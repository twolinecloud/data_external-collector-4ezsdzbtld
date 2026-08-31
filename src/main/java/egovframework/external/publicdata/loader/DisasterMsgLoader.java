package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.DisasterMsgMapper;
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
import java.util.HashMap;
import java.util.Map;

/**
 * 긴급재난문자 적재 - {@code kcais.tb_ext_disaster_msg}. {@code DisasterMsgCleanser}가 이미
 * facilityId까지 매칭해서 (sn, facilityId) 조합 1건당 1행으로 내놓으므로, 여기선 그대로
 * upsert만 한다.
 *
 * <p>{@code public-data.load.enabled=true}일 때만 빈으로 등록됨 - {@code KmaUltraSrtNcstLoader}
 * 참고.</p>
 *
 * <p><b>행 단위 예외 처리(2026-08-31)</b>: 예전엔 upsert 하나가 터지면 그대로 예외가 올라가
 * 같은 배치의 남은 행이 통째로 버려졌다. 실제로 1000자를 넘는 광역 재난문자 1건 때문에
 * {@code rcptn_rgn_nm_raw VARCHAR(1000)} 제약에 걸리면서 재난문자 적재가 약 1시간 10분간
 * 전면 정지한 사고가 있었다(cleanse-db-schema-spec.md §4.1-A). 컬럼은 {@code TEXT}로 넓혔지만
 * "행 하나가 배치를 죽이는" 구조 자체가 문제라, 이제 실패한 행만 건너뛰고 나머지는 끝까지
 * 적재한다. 단 <b>실패가 한 건이라도 있으면 마지막에 예외를 던져</b> raw_staging을
 * LOAD_FAILED로 남긴다 - 조용히 삼키면 유실을 아무도 모르게 되고, upsert 멱등키가
 * {@code (sn, facility_id)}라 다음 주기 재시도에서 성공분을 다시 써도 안전하기 때문
 * ({@code PublicDataLoadService}의 재시도 참고).</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DisasterMsgLoader implements PublicDataLoader {

    private static final Logger logger = LogManager.getLogger(DisasterMsgLoader.class);
    private static final String STAGE = "LOAD";

    private final DisasterMsgMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "safetydata-disaster-msg-list".equals(operationKey);
    }

    @Override
    public void load(RawStagingDto dto) throws LoadException {
        JSONArray rows;
        try {
            rows = new JSONArray(dto.getCleansedPayload());
        } catch (Exception e) {
            // 정제결과 자체가 깨진 경우는 행 단위로 건질 게 없으니 배치 전체 실패로 올린다.
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
        return p;
    }

    /** 실패한 행을 로그에서 특정할 수 있게 하는 라벨 - 라벨 만들다 또 터지면 인덱스로 대체. */
    private String rowLabel(JSONArray rows, int index) {
        try {
            JSONObject row = rows.getJSONObject(index);
            return "(sn=" + row.opt("sn") + ", facilityId=" + row.opt("facilityId") + ")";
        } catch (Exception e) {
            return "(index=" + index + ")";
        }
    }
}
