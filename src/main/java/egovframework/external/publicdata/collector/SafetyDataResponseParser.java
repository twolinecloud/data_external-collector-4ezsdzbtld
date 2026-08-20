package egovframework.external.publicdata.collector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 재난안전데이터공유플랫폼(safetydata.go.kr) 응답 봉투 파싱.
 *
 * <p>공공데이터포털({@link KmaResponseParser})과 <b>구조가 다르다</b>. 최상위에 {@code response}
 * 래퍼가 없고, {@code body}가 {@code items.item[]} 중첩 없이 곧바로 배열이다. 2026-08-18
 * 서비스키 발급 후 실호출로 확인한 실제 응답:</p>
 *
 * <pre>
 * {"header":{"resultMsg":"NORMAL SERVICE","resultCode":"00","errorMsg":null},
 *  "numOfRows":3,"pageNo":1,"totalCount":60685,
 *  "body":[{"SN":266798,"MSG_CN":"...","RCPTN_RGN_NM":"충청남도 당진시 석문면",
 *           "CRT_DT":"2026/08/18 02:00:10","EMRG_STEP_NM":"안전안내","DST_SE_NM":"기타",
 *           "REG_YMD":"...","MDFCN_YMD":"..."}, ...]}
 * </pre>
 *
 * <p>실패 시에는 {@code body}가 {@code null}이고 header에 사유가 담긴다:</p>
 *
 * <pre>
 * {"header":{"resultMsg":"SERVICE KEY IS NOT REGISTERED ERROR","resultCode":"30",
 *            "errorMsg":"등록되지 않은 서비스키"},"body":null}
 * </pre>
 *
 * <p>{@code numOfRows}/{@code pageNo}/{@code totalCount}도 {@code body}가 아니라 최상위에 있다.</p>
 */
public final class SafetyDataResponseParser {

    /** 정상 응답 코드. */
    private static final String SUCCESS_CODE = "00";

    private SafetyDataResponseParser() {
    }

    /**
     * 응답 1페이지 파싱 결과.
     *
     * @param items      이번 페이지 항목들(각각 JSON 문자열)
     * @param totalCount 전체 건수 - {@code items.size()}보다 크면 다음 페이지가 있다는 뜻
     */
    public record ParsedPage(List<String> items, int totalCount) {
    }

    /**
     * @throws SafetyDataApiException resultCode가 정상(00)이 아닌 경우 (JSON 구조는 정상 - 업무적 실패)
     */
    public static ParsedPage parse(String responseBody) {
        JSONObject root = new JSONObject(responseBody);

        JSONObject header = root.optJSONObject("header");
        if (header != null) {
            String resultCode = header.optString("resultCode", "");
            if (!SUCCESS_CODE.equals(resultCode)) {
                // errorMsg가 사람이 읽을 사유(한글), resultMsg는 영문 코드성 메시지라 둘 다 남긴다
                String errorMsg = header.isNull("errorMsg") ? null : header.optString("errorMsg", null);
                String resultMsg = header.optString("resultMsg", "UNKNOWN");
                throw new SafetyDataApiException(resultCode, errorMsg != null ? errorMsg : resultMsg);
            }
        }

        int totalCount = root.optInt("totalCount", 0);

        // 정상 응답이어도 조회 결과가 없으면 body가 null로 온다
        JSONArray body = root.optJSONArray("body");
        if (body == null) {
            return new ParsedPage(List.of(), totalCount);
        }

        List<String> items = new ArrayList<>(body.length());
        for (int i = 0; i < body.length(); i++) {
            JSONObject item = body.optJSONObject(i);
            if (item != null) {
                items.add(item.toString());
            }
        }
        return new ParsedPage(items, totalCount);
    }
}
