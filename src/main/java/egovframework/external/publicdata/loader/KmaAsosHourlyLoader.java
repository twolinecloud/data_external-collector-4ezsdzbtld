package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.collector.KmaAsosHourlyCollector;
import egovframework.external.publicdata.loader.mapper.AsosHourlyMapper;
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
 * 지상관측(ASOS) 시간자료 적재 - {@code kcais.tb_ext_asos_hourly}. {@code KmaAsosHourlyCleanser}가
 * 이미 기관마다 최근접 지점으로 매칭해서 (facilityId, stnNm 포함) 1행씩 넘겨주므로, 여기선
 * 컬럼별로 옮겨 담기만 한다.
 *
 * <p><b>판정 없이 원본 저장(2026-09-04)</b>: 안개/박무/연무 기준값이 기획 확정 전이라, 시정(vs)·
 * 습도(hm)를 그대로 저장하고 라벨링은 조회 단(백엔드)에 맡긴다({@code private-doc} 가이드 참고).
 * {@code vs}는 10m 단위 원본값 그대로다(580 = 5.8km) - 여기서 환산하지 않는다.</p>
 *
 * <p><b>행 단위 예외 처리</b>: {@code DisasterMsgLoader}(2026-08-31)와 같은 원칙 - upsert
 * 하나가 실패해도 나머지 기관 행은 끝까지 적재하고, 실패가 한 건이라도 있으면 마지막에 예외를
 * 던져 raw_staging을 LOAD_FAILED로 남긴다. upsert 멱등키가 (facility_id, base_dtm)이라
 * 재시도에서 성공분을 다시 써도 안전하다.</p>
 *
 * <p>{@code public-data.load.enabled=true}일 때만 빈으로 등록됨 - {@code KmaUltraSrtNcstLoader}
 * 참고.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KmaAsosHourlyLoader implements PublicDataLoader {

    private static final Logger logger = LogManager.getLogger(KmaAsosHourlyLoader.class);
    private static final String STAGE = "LOAD";

    /** ASOS 응답의 TM 필드 형식(분 단위까지, 예: "202609031300") - KmaDateTimeSupport의 FMT와 동일. */
    private static final DateTimeFormatter TM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final AsosHourlyMapper mapper;

    @Override
    public boolean supports(String operationKey) {
        return "kma-asos-hourly".equals(operationKey);
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
        p.put("stnId", row.getString("STN"));
        p.put("stnNm", row.getString("stnNm"));
        p.put("baseDtm", LocalDateTime.parse(row.getString("TM"), TM_FMT));
        p.put("vs", row.getString("VS"));
        p.put("hm", row.getString("HM"));
        p.put("ta", row.getString("TA"));
        p.put("ww", row.getString("WW"));
        p.put("wc", row.getString("WC"));
        p.put("wp", row.getString("WP"));
        p.put("ix", row.getString("IX"));
        // raw_json은 "원본 응답 필드 그대로"가 취지 - facilityId/stnNm/stnDistanceKm은 우리가
        // 매칭으로 얹은 값이라 여기 안 섞이게 원본 46개 필드만으로 별도 구성한다
        // (KmaWeatherWarningListLoader의 rawOnly 패턴과 동일).
        JSONObject rawOnly = new JSONObject();
        for (String field : KmaAsosHourlyCollector.FIELD_NAMES) {
            rawOnly.put(field, row.getString(field));
        }
        p.put("rawJson", rawOnly.toString());
        p.put("operationKey", dto.getOperationKey());
        p.put("collectDtm", dto.getCollectedAt());
        p.put("cleanseDtm", dto.getCleansedAt());
        return p;
    }

    private String rowLabel(JSONArray rows, int index) {
        try {
            JSONObject row = rows.getJSONObject(index);
            return "(facilityId=" + row.opt("facilityId") + ", stn=" + row.opt("STN") + ")";
        } catch (Exception e) {
            return "(index=" + index + ")";
        }
    }
}
