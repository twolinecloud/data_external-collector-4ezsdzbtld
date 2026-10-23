package egovframework.external.publicdata.transfer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LawDocumentParserTest {

    private static final String LAW = """
        [{"법령": {
          "기본정보": {"법령명_한글": "출입국관리법 시행규칙", "법령ID": "008494", "법종구분": {"content": "법무부령", "법종구분코드": "A0048"},
                     "공포일자": "20261023", "시행일자": "20261023", "소관부처": {"content": "법무부", "소관부처코드": "1270000"}},
          "별표": {"별표단위": [
            {"별표구분": "별표", "별표번호": "0001", "별표가지번호": "00", "별표제목": "체류자격별 체류기간",
             "별표서식파일링크": "/LSW/flDownload.do?flSeq=1", "별표서식PDF파일링크": "/LSW/flDownload.do?flSeq=2",
             "별표서식이미지파일링크": ["/LSW/flDownload.do?flSeq=3", "/LSW/flDownload.do?flSeq=4"]},
            {"별표구분": "서식", "별표번호": "0086", "별표가지번호": "02", "별표제목": "신청서",
             "별표서식파일링크": "/LSW/flDownload.do?flSeq=5", "별표서식이미지파일링크": "/LSW/flDownload.do?flSeq=6"}
          ]}
        }}]
        """;

    private static final String ADMIN_RULE = """
        [{"AdmRulService": {
          "행정규칙기본정보": {"행정규칙명": "가석방 업무지침 ", "행정규칙ID": "35334", "행정규칙종류": "예규",
                         "발령일자": "20260330", "시행일자": "20260330", "소관부처명": "법무부"},
          "별표": {"별표단위": {"별표구분": "별표", "별표번호": "0001", "별표가지번호": "00", "별표제목": "선정 기준",
                           "별표서식파일링크": "/LSW/flDownload.do?flSeq=10", "별표서식PDF파일링크": "/LSW/flDownload.do?flSeq=11"}},
          "첨부파일": {"첨부파일링크": ["http://law.go.kr/flDownload.do?flSeq=20", "http://law.go.kr/flDownload.do?flSeq=21"],
                   "첨부파일명": ["가석방 업무지침.hwp", "가석방 업무지침.pdf"]}
        }}]
        """;

    @Test
    void 법령은_기본정보와_별표를_읽고_상대경로_링크에_호스트를_붙인다() {
        LawDocument doc = LawDocumentParser.parse(LAW).get(0);

        assertThat(doc.docType()).isEqualTo(LawDocument.LAW);
        assertThat(doc.lawId()).isEqualTo("008494");
        assertThat(doc.name()).isEqualTo("출입국관리법 시행규칙");
        assertThat(doc.kind()).isEqualTo("법무부령");
        assertThat(doc.ministry()).isEqualTo("법무부");
        assertThat(doc.promulgationDate()).isEqualTo("20261023");
        assertThat(doc.annexes()).hasSize(2);
        LawDocument.Annex first = doc.annexes().get(0);
        assertThat(first.formUrl()).isEqualTo("https://www.law.go.kr/LSW/flDownload.do?flSeq=1");
        assertThat(first.pdfUrl()).isEqualTo("https://www.law.go.kr/LSW/flDownload.do?flSeq=2");
        assertThat(first.imageUrls()).hasSize(2);
        // 이미지 링크가 배열이 아니라 문자열 하나로 오는 경우, PDF 링크가 없는 경우
        LawDocument.Annex second = doc.annexes().get(1);
        assertThat(second.imageUrls()).containsExactly("https://www.law.go.kr/LSW/flDownload.do?flSeq=6");
        assertThat(second.pdfUrl()).isNull();
        assertThat(doc.attachments()).isEmpty();
    }

    @Test
    void 행정규칙은_이름_끝_공백을_자르고_별표단위_하나가_객체로_와도_읽으며_첨부는_https로_올린다() {
        LawDocument doc = LawDocumentParser.parse(ADMIN_RULE).get(0);

        assertThat(doc.docType()).isEqualTo(LawDocument.ADMIN_RULE);
        assertThat(doc.lawId()).isEqualTo("35334");
        assertThat(doc.name()).isEqualTo("가석방 업무지침");
        assertThat(doc.kind()).isEqualTo("예규");
        assertThat(doc.annexes()).hasSize(1);
        assertThat(doc.attachments()).containsExactly(
            new LawDocument.Attachment("가석방 업무지침.hwp", "https://law.go.kr/flDownload.do?flSeq=20"),
            new LawDocument.Attachment("가석방 업무지침.pdf", "https://law.go.kr/flDownload.do?flSeq=21"));
    }

    @Test
    void 첨부파일이_하나뿐이라_링크가_문자열로_와도_읽는다() {
        String payload = """
            [{"AdmRulService": {"행정규칙기본정보": {"행정규칙명": "x", "행정규칙ID": "1", "행정규칙종류": "훈령"},
              "첨부파일": {"첨부파일링크": "http://law.go.kr/flDownload.do?flSeq=9", "첨부파일명": "a.zip"}}}]
            """;

        assertThat(LawDocumentParser.parse(payload).get(0).attachments())
            .containsExactly(new LawDocument.Attachment("a.zip", "https://law.go.kr/flDownload.do?flSeq=9"));
    }

    @Test
    void 별표가_없으면_빈_목록이고_본문은_원문_JSON_그대로_담는다() {
        String payload = new JSONArray().put(new JSONObject()
            .put("법령", new JSONObject().put("기본정보", new JSONObject().put("법령ID", "1").put("법령명_한글", "형법")))).toString();

        LawDocument doc = LawDocumentParser.parse(payload).get(0);

        assertThat(doc.annexes()).isEmpty();
        assertThat(new JSONObject(doc.body()).has("법령")).isTrue();
        assertThat(doc.key()).isEqualTo("LAW:1");
    }

    @Test
    void 법령도_행정규칙도_아닌_본문이나_ID가_없으면_예외() {
        assertThatThrownBy(() -> LawDocumentParser.parse("[{\"Law\": \"없음\"}]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LawDocumentParser.parse("[{\"법령\": {\"기본정보\": {\"법령명_한글\": \"x\"}}}]"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 링크가_비어_있으면_null() {
        assertThat(LawDocumentParser.absolute("")).isNull();
        assertThat(LawDocumentParser.absolute(null)).isNull();
        assertThat(LawDocumentParser.absolute("https://x/y")).isEqualTo("https://x/y");
        assertThat(List.of(LawDocumentParser.absolute("/a"))).containsExactly("https://www.law.go.kr/a");
    }
}
