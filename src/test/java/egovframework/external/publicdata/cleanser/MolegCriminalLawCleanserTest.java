package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 법령 본문은 이미 넓은 형태(법령 1건=배열 원소 1개)라 피벗 없이 통과시키되, "법령" 키
 * 존재만 검증한다 (기상특보목록 정제기와 동일 패턴).
 */
class MolegCriminalLawCleanserTest {

    private final MolegCriminalLawCleanser cleanser = new MolegCriminalLawCleanser();

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("moleg-criminal-law")).isTrue();
        assertThat(cleanser.supports("kma-village-forecast-vilage-fcst")).isFalse();
    }

    @Test
    void 법령_키가_있으면_원문_그대로_통과시킨다() throws CleanseException {
        String raw = new JSONArray()
            .put(new JSONObject().put("법령", new JSONObject().put("법령명", "형법")))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(1);
        assertThat(rows.getJSONObject(0).getJSONObject("법령").getString("법령명")).isEqualTo("형법");
    }

    @Test
    void 법령_키가_없으면_CleanseException을_던진다() {
        String raw = new JSONArray().put(new JSONObject().put("Law", "일치하는 법령이 없습니다")).toString();

        assertThatThrownBy(() -> cleanser.cleanse(raw))
            .isInstanceOf(CleanseException.class);
    }

    @Test
    void 구조_프로브가_법령_조문단위_조문_전문_3개다() {
        assertThat(cleanser.structureProbes()).extracting(StructureProbe::label)
            .containsExactly("법령", "조문단위:조문", "조문단위:전문");
    }

    @Test
    void 법령_프로브가_기본정보_등_최상위_키를_관찰한다() {
        String raw = sampleLawPayload();
        StructureProbe probe = cleanser.structureProbes().get(0);

        java.util.Set<String> observed = probe.observer().apply(new JSONArray(raw));
        assertThat(observed).containsExactlyInAnyOrder("개정문", "조문", "제개정이유", "법령키", "기본정보", "부칙");
        assertThat(probe.knownFields()).containsExactlyInAnyOrderElementsOf(observed);
    }

    @Test
    void 조문_프로브는_전문_원소를_제외하고_조문만_관찰한다() {
        String raw = sampleLawPayload();
        StructureProbe jomunProbe = cleanser.structureProbes().get(1);

        java.util.Set<String> observed = jomunProbe.observer().apply(new JSONArray(raw));
        // 샘플의 조문(제1조)엔 조문가지번호/항이 없음 - 선택필드라 MISSING으로 안 잡혀야 함(필수집합만 비교)
        assertThat(jomunProbe.requiredFields()).allMatch(observed::contains);
        assertThat(observed).doesNotContain("조문가지번호", "항");
    }

    @Test
    void 새_필드가_섞이면_법령_프로브가_ADDED를_잡는다() {
        JSONObject law = new JSONObject()
            .put("개정문", new JSONObject())
            .put("조문", new JSONObject().put("조문단위", new JSONArray()))
            .put("제개정이유", new JSONObject())
            .put("법령키", "0")
            .put("기본정보", new JSONObject())
            .put("부칙", new JSONObject())
            .put("새로생긴필드", "미확인");
        String raw = new JSONArray().put(new JSONObject().put("법령", law)).toString();
        StructureProbe probe = cleanser.structureProbes().get(0);

        java.util.Set<String> observed = probe.observer().apply(new JSONArray(raw));
        java.util.Set<String> added = new java.util.HashSet<>(observed);
        added.removeAll(probe.knownFields());
        assertThat(added).containsExactly("새로생긴필드");
    }

    private static String sampleLawPayload() {
        JSONObject article = new JSONObject()
            .put("조문내용", "제1조(목적) 이 법은...")
            .put("조문번호", "1")
            .put("조문변경여부", "N")
            .put("조문시행일자", "20260913")
            .put("조문여부", "조문")
            .put("조문이동이전", "")
            .put("조문이동이후", "")
            .put("조문키", "0001001");
        JSONObject law = new JSONObject()
            .put("개정문", new JSONObject())
            .put("조문", new JSONObject().put("조문단위", new JSONArray().put(article)))
            .put("제개정이유", new JSONObject())
            .put("법령키", "0001692026031221450")
            .put("기본정보", new JSONObject().put("법령명_한글", "형법"))
            .put("부칙", new JSONObject());
        return new JSONArray().put(new JSONObject().put("법령", law)).toString();
    }
}
