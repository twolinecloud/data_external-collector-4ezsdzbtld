package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공공데이터포털 공통 응답봉투 파싱 검증. 실제 기상청 API 응답에서 관찰되는 형태
 * (item이 배열/단일객체/없음, resultCode 정상/비정상)를 각각 커버.
 */
class KmaResponseParserTest {

    @Test
    void item이_배열이면_각각을_JSON_문자열로_반환한다() {
        String body = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":[{"category":"T1H","obsrValue":"25"},{"category":"REH","obsrValue":"60"}]}}}}
            """;

        List<String> items = KmaResponseParser.extractItems(body);

        assertThat(items).hasSize(2);
        assertThat(items.get(0)).contains("T1H").contains("25");
        assertThat(items.get(1)).contains("REH").contains("60");
    }

    @Test
    void item이_단일_객체로_와도_1건짜리_리스트로_반환한다() {
        // 공공데이터포털 API 공통 특징: 결과 1건일 때는 item이 배열이 아니라 객체로 옴
        String body = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":{"category":"T1H","obsrValue":"25"}}}}}
            """;

        List<String> items = KmaResponseParser.extractItems(body);

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).contains("T1H");
    }

    @Test
    void items가_비어있으면_빈_리스트를_반환한다() {
        String body = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"totalCount":0}}}
            """;

        List<String> items = KmaResponseParser.extractItems(body);

        assertThat(items).isEmpty();
    }

    @Test
    void resultCode가_정상이_아니면_KmaApiException을_던지고_코드와_메시지를_그대로_담는다() {
        String body = """
            {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"}}}
            """;

        assertThatThrownBy(() -> KmaResponseParser.extractItems(body))
            .isInstanceOf(KmaApiException.class)
            .satisfies(e -> {
                KmaApiException ex = (KmaApiException) e;
                assertThat(ex.getResultCode()).isEqualTo("22");
                assertThat(ex.getResultMsg()).isEqualTo("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR");
                assertThat(ex.getMessage()).contains("일일 호출 한도 초과");
            });
    }

    @Test
    void 형식이_아예_깨진_JSON이면_KmaApiException이_아닌_다른_예외를_던진다() {
        // KmaApiClient가 "업무적 실패"와 "형식 오류"를 구분해서 메시지를 남길 수 있어야 하므로,
        // 형식 자체가 깨진 경우는 KmaApiException이 아니어야 한다 (org.json의 JSONException 등).
        assertThatThrownBy(() -> KmaResponseParser.extractItems("이건 JSON이 아님"))
            .isNotInstanceOf(KmaApiException.class);
    }
}
