package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.FacilityRegion;
import egovframework.external.publicdata.collector.FacilityRegionLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 긴급재난문자(safetydata-disaster-msg-list) 정제기.
 *
 * <p>{@link egovframework.external.publicdata.collector.DisasterMsgCollector}가 이미
 * {@link egovframework.external.publicdata.collector.SafetyDataResponseParser}로 envelope을
 * 벗겨 메시지 1건씩 배열로 넘기고(다른 컬렉터들처럼 raw_staging엔 JSON 배열 1개로 합쳐져 저장됨),
 * 여기서는 envelope을 다시 벗길 필요가 없다 - {@code rawPayload}는 곧바로 메시지 객체 배열이다.</p>
 *
 * <p><b>사용자 요구사항(cleanse-db-schema-spec.md §4.0/§4.2)</b>: 전국 재난문자를 다 쌓지 않고
 * 교정기관 지역과 매칭되는 것만 저장한다. 매칭 로직은 §4.2에 설계된 그대로 - {@code RCPTN_RGN_NM}을
 * ','로 쪼개고 공백을 전부 제거한 뒤, 기관의 {@code sido+sigungu}(마찬가지로 공백 없음)로
 * 시작하면(startsWith) 매칭. 시군구까지만 온 메시지도, 읍면동까지 내려간 메시지도 같은 로직으로
 * 처리된다 - 가정: 읍면동은 항상 시군구 뒤에 붙는 순서로 온다(지금까지 실측 표본은 전부 이 순서,
 * 더 큰 표본으로 재검증 필요).</p>
 *
 * <p>한 메시지가 여러 지역/여러 기관과 겹치면 (SN, facilityId) 조합마다 행을 하나씩 낸다 - 날씨
 * 테이블들이 facility_id로 1테이블-N행인 것과 같은 의도적 비정규화 패턴. 어느 기관과도 매칭되지
 * 않는 메시지는 정제 결과에서 빠진다(저장 안 함).</p>
 */
@Component
public class DisasterMsgCleanser implements PublicDataCleanser {

    /** 2026-08-18 실 서비스키로 확인된 필드 전체(당일 재난문자 27건 전량). */
    private static final Set<String> RAW_ITEM_FIELDS = Set.of(
        "SN", "MSG_CN", "RCPTN_RGN_NM", "CRT_DT", "REG_YMD", "EMRG_STEP_NM", "DST_SE_NM", "MDFCN_YMD");

    private static final DateTimeFormatter SOURCE_DATETIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final List<FacilityRegion> facilityRegions;

    public DisasterMsgCleanser(FacilityRegionLoader facilityRegionLoader) {
        this.facilityRegions = facilityRegionLoader.all();
    }

    @Override
    public boolean supports(String operationKey) {
        return "safetydata-disaster-msg-list".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(new StructureProbe("raw-item", RAW_ITEM_FIELDS, RAW_ITEM_FIELDS, StructureProbeSupport::unionKeys));
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            JSONArray result = new JSONArray();
            for (int i = 0; i < rawItems.length(); i++) {
                appendMatches(rawItems.getJSONObject(i), result);
            }
            return result.toString();
        } catch (Exception e) {
            throw new CleanseException("재난안전데이터공유플랫폼 (행정안전부)", "긴급재난문자 목록조회",
                "정제 실패: " + e.getMessage(), e);
        }
    }

    private void appendMatches(JSONObject msg, JSONArray result) {
        requireField(msg, "SN");
        requireField(msg, "MSG_CN");
        requireField(msg, "CRT_DT");
        requireField(msg, "EMRG_STEP_NM");
        requireField(msg, "DST_SE_NM");
        String rcptnRgnNmRaw = msg.optString("RCPTN_RGN_NM", "");

        // facility별로 "그 facility를 실제로 매칭시킨 첫 지역 조각"을 따로 기억한다. 도 전체
        // 호우주의보처럼 한 메시지에 여러 시군구가 콤마로 나열되는 경우(실측: 경남 12개 시군구가
        // 한 메시지에 다 들어있었음, 2026-08-18) facility마다 매칭에 쓰인 조각이 다르므로,
        // 메시지 전체에서 딱 하나만 기억하면(과거 버그) 엉뚱한 facility에 다른 facility의
        // 매칭 지역이 찍힌다 - 예: 밀양시 조각으로 매칭된 밀양구치소와 통영시 조각으로 매칭된
        // 통영구치소가 둘 다 "밀양시"로 잘못 기록됐던 것을 실제 라이브 데이터로 발견하고 고침.
        Map<String, String> matchedRegionByFacility = new LinkedHashMap<>();

        for (String piece : rcptnRgnNmRaw.split(",")) {
            String normalized = piece.replaceAll("\\s+", "");
            if (normalized.isEmpty()) {
                continue;
            }
            for (FacilityRegion region : facilityRegions) {
                if (normalized.startsWith(region.regionKey())) {
                    matchedRegionByFacility.putIfAbsent(region.facilityId(), piece.trim());
                }
            }
        }

        if (matchedRegionByFacility.isEmpty()) {
            return; // 우리 기관 소재지와 무관한 문자 - 저장하지 않음
        }

        for (Map.Entry<String, String> entry : matchedRegionByFacility.entrySet()) {
            JSONObject row = new JSONObject();
            row.put("sn", msg.get("SN"));
            row.put("facilityId", entry.getKey());
            row.put("matchedRegionNm", entry.getValue());
            row.put("crtDtm", parseDateTime(msg.getString("CRT_DT")));
            row.put("msgCn", msg.getString("MSG_CN"));
            row.put("emrgStepNm", msg.getString("EMRG_STEP_NM"));
            row.put("dstSeNm", msg.getString("DST_SE_NM"));
            row.put("rcptnRgnNmRaw", rcptnRgnNmRaw);
            // REG_YMD/MDFCN_YMD는 이름과 달리 실측값이 날짜(YMD)가 아니라 초 이하 단위까지 포함한
            // 전체 타임스탬프 문자열이었다(예: "2026/08/18 02:01:05.000000000") - 형식을 함부로
            // 재단하지 않고 원문 그대로 통과시킨다. cleanse-db-schema-spec.md §4.1의
            // reg_de/mdfcn_de VARCHAR(8) 가정은 이 실측과 안 맞아 문서를 같이 고쳤다.
            row.put("regDe", msg.has("REG_YMD") ? msg.get("REG_YMD") : JSONObject.NULL);
            row.put("mdfcnDe", msg.has("MDFCN_YMD") ? msg.get("MDFCN_YMD") : JSONObject.NULL);
            result.put(row);
        }
    }

    private void requireField(JSONObject msg, String field) {
        if (!msg.has(field) || msg.isNull(field)) {
            throw new IllegalStateException("재난문자 항목에 " + field + " 필드 없음: " + msg);
        }
    }

    private String parseDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw, SOURCE_DATETIME).toString();
        } catch (Exception e) {
            throw new IllegalStateException("CRT_DT 파싱 실패(형식 불일치): " + raw, e);
        }
    }
}
