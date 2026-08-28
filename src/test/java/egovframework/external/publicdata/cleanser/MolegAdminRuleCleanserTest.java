package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 행정규칙 본문은 법령과 봉투 키만 다를 뿐 처리 패턴은 동일하다 - {@code MolegCriminalLawCleanserTest}
 * 대응 버전.
 */
class MolegAdminRuleCleanserTest {

    private final MolegAdminRuleCleanser cleanser = new MolegAdminRuleCleanser();

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("moleg-admin-rule")).isTrue();
        assertThat(cleanser.supports("moleg-criminal-law")).isFalse();
    }

    @Test
    void AdmRulService_키가_있으면_원문_그대로_통과시킨다() throws CleanseException {
        String raw = new JSONArray()
            .put(new JSONObject().put("AdmRulService",
                new JSONObject().put("행정규칙기본정보", new JSONObject().put("행정규칙명", "가석방 업무지침"))))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(1);
        assertThat(rows.getJSONObject(0).getJSONObject("AdmRulService")
            .getJSONObject("행정규칙기본정보").getString("행정규칙명")).isEqualTo("가석방 업무지침");
    }

    @Test
    void AdmRulService_키가_없으면_CleanseException을_던진다() {
        String raw = new JSONArray().put(new JSONObject().put("법령", new JSONObject())).toString();

        assertThatThrownBy(() -> cleanser.cleanse(raw))
            .isInstanceOf(CleanseException.class);
    }

    @Test
    void 구조_프로브가_AdmRulService_1개다() {
        assertThat(cleanser.structureProbes()).extracting(StructureProbe::label)
            .containsExactly("AdmRulService");
    }

    @Test
    void 프로브가_최상위_키를_관찰한다() {
        JSONObject rule = new JSONObject()
            .put("개정문", new JSONObject())
            .put("별표", new JSONArray())
            .put("행정규칙기본정보", new JSONObject())
            .put("조문내용", new JSONObject())
            .put("첨부파일", new JSONArray())
            .put("부칙", new JSONObject())
            .put("제개정이유", new JSONObject());
        String raw = new JSONArray().put(new JSONObject().put("AdmRulService", rule)).toString();
        StructureProbe probe = cleanser.structureProbes().get(0);

        Set<String> observed = probe.observer().apply(new JSONArray(raw));
        assertThat(observed).containsExactlyInAnyOrder(
            "개정문", "별표", "행정규칙기본정보", "조문내용", "첨부파일", "부칙", "제개정이유");
        assertThat(probe.knownFields()).containsExactlyInAnyOrderElementsOf(observed);
    }
}
