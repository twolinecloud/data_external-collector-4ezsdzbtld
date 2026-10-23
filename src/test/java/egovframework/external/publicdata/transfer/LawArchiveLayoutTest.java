package egovframework.external.publicdata.transfer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LawArchiveLayoutTest {

    private static LawDocument law(String kind) {
        return new LawDocument(LawDocument.LAW, "1", "이름", kind, "", "", "", "{}", List.of(), List.of());
    }

    private static LawDocument rule(String kind) {
        return new LawDocument(LawDocument.ADMIN_RULE, "1", "이름", kind, "", "", "", "{}", List.of(), List.of());
    }

    @Test
    void 법령_유형은_법종구분_값으로_나눈다() {
        assertThat(LawArchiveLayout.category(law("법률"))).isEqualTo("법률");
        assertThat(LawArchiveLayout.category(law("대통령령"))).isEqualTo("시행령");
        for (String kind : List.of("법무부령", "총리령", "재정경제부령", "기획재정부령", "성평등가족부령", "과학기술정보통신부령", "대법원규칙")) {
            assertThat(LawArchiveLayout.category(law(kind))).as(kind).isEqualTo("시행규칙");
        }
    }

    @Test
    void 법률도_령도_아닌_값은_법률_폴더로_둔다() {
        assertThat(LawArchiveLayout.category(law("대통령긴급명령"))).isEqualTo("법률");
        assertThat(LawArchiveLayout.category(law(""))).isEqualTo("법률");
        assertThat(LawArchiveLayout.category(law(null))).isEqualTo("법률");
    }

    @Test
    void 행정규칙은_종류를_그대로_폴더로_쓰고_없으면_기타() {
        assertThat(LawArchiveLayout.category(rule("훈령"))).isEqualTo("훈령");
        assertThat(LawArchiveLayout.category(rule("예규"))).isEqualTo("예규");
        assertThat(LawArchiveLayout.category(rule(""))).isEqualTo("기타");
        assertThat(LawArchiveLayout.topFolder(rule("예규"))).isEqualTo("admrul");
        assertThat(LawArchiveLayout.topFolder(law("법률"))).isEqualTo("law");
    }

    @Test
    void 문서_경로는_최상위_유형_이름_순이고_본문은_같은_이름의_json() {
        LawDocument decree = law("대통령령");
        String dir = LawArchiveLayout.documentDir(decree, "마약류 관리에 관한 법률 시행령");

        assertThat(dir).isEqualTo("law/시행령/마약류 관리에 관한 법률 시행령");
        assertThat(LawArchiveLayout.bodyPath(dir, "마약류 관리에 관한 법률 시행령"))
            .isEqualTo("law/시행령/마약류 관리에 관한 법률 시행령/마약류 관리에 관한 법률 시행령.json");
    }

    @Test
    void 별표_파일명은_구분과_번호_가지번호로_만들고_번호_0000이면_구분만_쓴다() {
        assertThat(LawArchiveLayout.annexBaseName(annex("별표", "0001", "00"))).isEqualTo("별표1");
        assertThat(LawArchiveLayout.annexBaseName(annex("서식", "0086", "02"))).isEqualTo("서식86의2");
        assertThat(LawArchiveLayout.annexBaseName(annex("별지", "0010", "00"))).isEqualTo("별지10");
        assertThat(LawArchiveLayout.annexBaseName(annex("별표", "0000", "00"))).isEqualTo("별표");
        assertThat(LawArchiveLayout.annexBaseName(annex("", "0003", "00"))).isEqualTo("별표3");
        assertThat(LawArchiveLayout.attachmentBaseName(2)).isEqualTo("첨부2");
    }

    @Test
    void 이름의_위험_문자와_끝_공백_점을_정리한다() {
        assertThat(LawArchiveLayout.sanitize("심리치료 업무지침 ")).isEqualTo("심리치료 업무지침");
        assertThat(LawArchiveLayout.sanitize("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("a_b_c_d_e_f_g_h_i_j");
        assertThat(LawArchiveLayout.sanitize("끝점.. ")).isEqualTo("끝점");
        assertThat(LawArchiveLayout.sanitize("   ")).isEqualTo("_");
        // 쉼표·한자 괄호는 위험 문자가 아니다(실제 법령명에 있다)
        assertThat(LawArchiveLayout.sanitize("교정공무원 기관장(葬)에 관한 규정")).isEqualTo("교정공무원 기관장(葬)에 관한 규정");
        assertThat(LawArchiveLayout.sanitize("검사, 공소청 소속 직원")).isEqualTo("검사, 공소청 소속 직원");
    }

    @Test
    void NFD로_들어온_이름은_NFC로_통일한다() {
        String nfd = Normalizer.normalize("형법", Normalizer.Form.NFD);

        assertThat(nfd).isNotEqualTo("형법");
        assertThat(LawArchiveLayout.sanitize(nfd)).isEqualTo("형법");
    }

    @Test
    void 너무_긴_이름은_바이트_기준으로_자르되_글자를_깨뜨리지_않는다() {
        String sanitized = LawArchiveLayout.sanitize("가".repeat(100));

        assertThat(sanitized.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(200);
        assertThat(sanitized).matches("가+");
    }

    private static LawDocument.Annex annex(String category, String number, String branch) {
        return new LawDocument.Annex(category, number, branch, "", null, null, List.of());
    }
}
