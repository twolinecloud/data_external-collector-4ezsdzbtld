package egovframework.external.publicdata.transfer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MolegLawMstLookupTest {

    private static final String TWO = """
        {"LawSearch": {"law": [
          {"법령ID": "001702", "법령일련번호": "111", "시행일자": "20250101", "현행연혁코드": "현행"},
          {"법령ID": "001702", "법령일련번호": "222", "시행일자": "20260910", "현행연혁코드": "현행"},
          {"법령ID": "009999", "법령일련번호": "333", "시행일자": "20260910", "현행연혁코드": "현행"}]}}
        """;

    @Test
    void 법령ID가_같고_시행일자도_같은_항목의_MST를_고른다() throws Exception {
        assertThat(MolegLawMstLookup.pick(TWO, "001702", "20260910", true)).isEqualTo("222");
    }

    @Test
    void 시행일자가_맞는_항목이_없으면_법령ID가_같은_첫_항목() throws Exception {
        assertThat(MolegLawMstLookup.pick(TWO, "001702", "20000101", true)).isEqualTo("111");
    }

    @Test
    void 시행예정_검색처럼_폴백을_끄면_시행일자가_정확히_같은_항목만_고른다() throws Exception {
        assertThat(MolegLawMstLookup.pick(TWO, "001702", "20260910", false)).isEqualTo("222");
        assertThat(MolegLawMstLookup.pick(TWO, "001702", "20000101", false)).isNull();
    }

    @Test
    void 법령ID가_다른_항목은_고르지_않는다() throws Exception {
        assertThat(MolegLawMstLookup.pick(TWO, "777777", "20260910", true)).isNull();
    }

    @Test
    void 결과가_하나뿐이라_객체로_와도_읽고_없으면_null() throws Exception {
        String one = "{\"LawSearch\": {\"law\": {\"법령ID\": \"1\", \"법령일련번호\": \"55\", \"시행일자\": \"20260101\"}}}";
        assertThat(MolegLawMstLookup.pick(one, "1", "20260101", true)).isEqualTo("55");
        assertThat(MolegLawMstLookup.pick("{\"LawSearch\": {\"totalCnt\": \"0\"}}", "1", "20260101", true)).isNull();
    }

    @Test
    void JSON이_아닌_응답은_다시_시도할_수_있는_실패다() {
        assertThatThrownBy(() -> MolegLawMstLookup.pick("<html>error</html>", "1", "", true))
            .isInstanceOf(LawMstLookup.LookupException.class);
    }
}
