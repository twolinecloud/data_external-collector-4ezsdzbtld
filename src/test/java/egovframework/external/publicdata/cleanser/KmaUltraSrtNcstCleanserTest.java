package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmaUltraSrtNcstCleanserTest {

    private final KmaUltraSrtNcstCleanser cleanser = new KmaUltraSrtNcstCleanser();

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("kma-village-forecast-ultra-srt-ncst")).isTrue();
        assertThat(cleanser.supports("kma-village-forecast-vilage-fcst")).isFalse();
    }

    @Test
    void 관측_스냅샷_1개_시점을_행_1개로_피벗한다() throws CleanseException {
        String raw = new JSONArray()
            .put(ncstItem("T1H", "23.5"))
            .put(ncstItem("REH", "60"))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(1);
        JSONObject row = rows.getJSONObject(0);
        assertThat(row.getString("t1h")).isEqualTo("23.5");
        assertThat(row.getString("reh")).isEqualTo("60");
        // 관측되지 않은 나머지 카테고리는 null로 채워져 컬럼 구성이 고정된다.
        assertThat(row.isNull("pty")).isTrue();
    }

    @Test
    void 빈_배열이면_빈_배열을_반환한다() throws CleanseException {
        String result = cleanser.cleanse("[]");

        assertThat(new JSONArray(result).length()).isEqualTo(0);
    }

    @Test
    void 필수_필드가_없으면_CleanseException을_던진다() {
        String malformed = "[{\"category\":\"T1H\"}]"; // nx/ny/baseDate/baseTime/obsrValue 없음

        assertThatThrownBy(() -> cleanser.cleanse(malformed))
            .isInstanceOf(CleanseException.class);
    }

    @Test
    void 구조_프로브가_실제_필드셋과_일치해서_정상_샘플에선_드리프트가_안_잡힌다() {
        String raw = new JSONArray().put(ncstItem("T1H", "23.5")).toString();
        StructureProbe probe = cleanser.structureProbes().get(0);

        assertThat(probe.label()).isEqualTo("raw-item");
        java.util.Set<String> observed = probe.observer().apply(new JSONArray(raw));
        assertThat(probe.knownFields()).containsExactlyInAnyOrderElementsOf(observed);
        assertThat(probe.requiredFields()).containsExactlyInAnyOrderElementsOf(observed);
    }

    private static JSONObject ncstItem(String category, String value) {
        return new JSONObject()
            .put("nx", 60)
            .put("ny", 124)
            .put("baseDate", "20260805")
            .put("baseTime", "0600")
            .put("category", category)
            .put("obsrValue", value);
    }
}
