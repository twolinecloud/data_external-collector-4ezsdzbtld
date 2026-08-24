package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SafetyDataResponseParser} 검증.
 *
 * <p>여기 쓰인 응답 문자열은 2026-08-18 실제 API 호출로 받은 것을 그대로 축약한 것이다
 * (필드명/구조를 임의로 만들지 않음). 공공데이터포털 봉투와 다른 점 - {@code response} 래퍼가
 * 없고 {@code body}가 곧바로 배열, 페이징 정보도 최상위에 있음 - 을 고정한다.</p>
 */
class SafetyDataResponseParserTest {

    private static final String OK_RESPONSE = """
        {"header":{"resultMsg":"NORMAL SERVICE","resultCode":"00","errorMsg":null},
         "numOfRows":2,"pageNo":1,"totalCount":60685,
         "body":[
           {"MSG_CN":"현재 대조기 기간으로 밀물 속도가 매우 빠릅니다.","RCPTN_RGN_NM":"충청남도 당진시 석문면",
            "CRT_DT":"2026/08/18 02:00:10","REG_YMD":"2026/08/18 02:01:05.000000000",
            "EMRG_STEP_NM":"안전안내","SN":266798,"DST_SE_NM":"기타",
            "MDFCN_YMD":"2026/08/18 02:10:35.000000000"},
           {"MSG_CN":"오늘 새벽 03:00 호우주의보 발효.","RCPTN_RGN_NM":"경상남도 거제시 ",
            "CRT_DT":"2026/08/18 03:51:18","REG_YMD":"2026/08/18 03:51:55.000000000",
            "EMRG_STEP_NM":"안전안내","SN":266799,"DST_SE_NM":"호우",
            "MDFCN_YMD":"2026/08/18 04:01:45.000000000"}]}
        """;

    /** 실측 확인된 실패 응답 - body가 null이고 errorMsg에 한글 사유가 들어온다. */
    private static final String ERROR_RESPONSE = """
        {"header":{"resultMsg":"SERVICE KEY IS NOT REGISTERED ERROR","resultCode":"30",
                   "errorMsg":"등록되지 않은 서비스키"},"body":null}
        """;

    @Test
    void body_배열의_각_항목을_개별_JSON으로_반환한다() {
        SafetyDataResponseParser.ParsedPage page = SafetyDataResponseParser.parse(OK_RESPONSE);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0)).contains("\"SN\":266798").contains("당진시 석문면");
        assertThat(page.items().get(1)).contains("\"SN\":266799").contains("\"DST_SE_NM\":\"호우\"");
    }

    @Test
    void totalCount는_body가_아니라_최상위에서_읽는다() {
        // 공공데이터포털은 response.body.totalCount인데 이 API는 최상위 - 페이지네이션이 여기 걸려있다
        assertThat(SafetyDataResponseParser.parse(OK_RESPONSE).totalCount()).isEqualTo(60685);
    }

    @Test
    void resultCode가_00이_아니면_SafetyDataApiException을_던진다() {
        assertThatThrownBy(() -> SafetyDataResponseParser.parse(ERROR_RESPONSE))
            .isInstanceOf(SafetyDataApiException.class)
            .hasMessageContaining("30")
            .hasMessageContaining("등록되지 않은 서비스키");
    }

    @Test
    void 정상이지만_결과가_없으면_빈_목록을_반환한다() {
        String empty = "{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE\"},"
            + "\"totalCount\":0,\"body\":null}";

        SafetyDataResponseParser.ParsedPage page = SafetyDataResponseParser.parse(empty);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isZero();
    }

    @Test
    void errorMsg가_null이면_resultMsg를_사유로_쓴다() {
        String noErrorMsg = "{\"header\":{\"resultCode\":\"99\",\"resultMsg\":\"UNKNOWN ERROR\","
            + "\"errorMsg\":null},\"body\":null}";

        assertThatThrownBy(() -> SafetyDataResponseParser.parse(noErrorMsg))
            .isInstanceOf(SafetyDataApiException.class)
            .hasMessageContaining("UNKNOWN ERROR");
    }
}
