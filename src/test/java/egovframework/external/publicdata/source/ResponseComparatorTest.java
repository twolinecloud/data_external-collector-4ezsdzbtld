package egovframework.external.publicdata.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseComparatorTest {

    @Test
    void JSON은_키_순서와_공백을_무시한다() {
        var r = ResponseComparator.compareJson(
            "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":2,\"items\":[1,2]}}}",
            "{ \"response\" : { \"body\" : { \"items\" : [1, 2], \"totalCount\" : 2 }, \"header\" : {\"resultCode\":\"00\"} } }");

        assertTrue(r.same(), r.differences().toString());
    }

    @Test
    void 숫자는_값이_같으면_같고_문자열과는_다르다() {
        assertTrue(ResponseComparator.compareJson("{\"a\":1.0}", "{\"a\":1}").same());
        var r = ResponseComparator.compareJson("{\"a\":\"1\"}", "{\"a\":1}");
        assertFalse(r.same());
        assertEquals("$.a: 문자열 1 ≠ 숫자 1", r.differences().get(0));
    }

    @Test
    void null과_키_부재는_다르다() {
        var r = ResponseComparator.compareJson("{\"a\":null}", "{}");

        assertFalse(r.same());
        assertTrue(r.differences().get(0).startsWith("$.a: 비교 응답에 없음"));
    }

    @Test
    void 배열은_순서와_길이를_본다() {
        var r = ResponseComparator.compareJson("{\"i\":[1,2,3]}", "{\"i\":[1,3]}");

        assertFalse(r.same());
        assertTrue(r.differences().get(0).contains("배열 길이 3 ≠ 2"));
        assertTrue(r.differences().stream().anyMatch(d -> d.startsWith("$.i[1]")));
    }

    @Test
    void 깊은_경로의_값_차이를_경로로_알려준다() {
        var r = ResponseComparator.compareJson(
            "{\"response\":{\"body\":{\"items\":{\"item\":[{\"obsrValue\":\"3.1\"}]}}}}",
            "{\"response\":{\"body\":{\"items\":{\"item\":[{\"obsrValue\":\"3.2\"}]}}}}");

        assertEquals("$.response.body.items.item[0].obsrValue: 문자열 3.1 ≠ 문자열 3.2", r.differences().get(0));
    }

    @Test
    void 잘못된_JSON은_차이로_보고한다() {
        var r = ResponseComparator.compareJson("{\"a\":1}", "<html>error</html>");

        assertFalse(r.same());
        assertTrue(r.differences().get(0).startsWith("JSON 파싱 실패"));
    }

    @Test
    void 텍스트는_줄바꿈_종류와_줄끝_공백만_무시한다() {
        assertTrue(ResponseComparator.compareText("a b c  \r\n1 2 3\r\n", "a b c\n1 2 3").same());

        var r = ResponseComparator.compareText("a b c\n1 2 3", "a b c\n1 2 4");
        assertFalse(r.same());
        assertEquals("줄 2: 1 2 3 ≠ 1 2 4", r.differences().get(0));
    }

    @Test
    void 텍스트는_줄_수_차이도_알려준다() {
        var r = ResponseComparator.compareText("x\ny", "x");

        assertFalse(r.same());
        assertEquals("줄 2: y ≠ (없음)", r.differences().get(0));
    }

    @Test
    void 차이는_최대_20건만_담는다() {
        StringBuilder a = new StringBuilder("{");
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < 50; i++) {
            a.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":1");
            b.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":2");
        }
        var r = ResponseComparator.compareJson(a.append('}').toString(), b.append('}').toString());

        assertEquals(20, r.differences().size());
    }
}
