package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.FacilitySidoLoader;
import egovframework.external.publicdata.collector.KmaWarningStationLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기상특보목록은 이미 넓은 형태라 피벗 없이 통과시키되, {@code stnId}(시도 단위 관할구역)를
 * 매칭된 교정기관 수만큼 행으로 복제한다({@code DisasterMsgCleanser}와 동일 패턴, 2026-08-21
 * 추가 - admin-db 테이블에 facility_id가 없어 시설별 매칭이 안 되던 문제 해결).
 */
class KmaWeatherWarningListCleanserTest {

    private final KmaWeatherWarningListCleanser cleanser =
        new KmaWeatherWarningListCleanser(new KmaWarningStationLoader(), new FacilitySidoLoader());

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("kma-weather-warning-list")).isTrue();
        assertThat(cleanser.supports("kma-village-forecast-vilage-fcst")).isFalse();
    }

    @Test
    void stnId_108_전국은_59개소_전부에_매칭된다() throws CleanseException {
        String raw = new JSONArray()
            .put(new JSONObject().put("title", "호우주의보").put("stnId", "108").put("tmFc", 202608121000L).put("tmSeq", 35))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(59);
        Set<String> facilityIds = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            assertThat(row.getString("title")).isEqualTo("호우주의보");
            assertThat(row.getString("stnId")).isEqualTo("108");
            facilityIds.add(row.getString("facilityId"));
        }
        assertThat(facilityIds).hasSize(59); // 전부 서로 다른 기관
    }

    @Test
    void stnId_105는_강원특별자치도_소재_기관에만_매칭된다() throws CleanseException {
        String raw = new JSONArray()
            .put(new JSONObject().put("title", "대설주의보").put("stnId", "105").put("tmFc", 202608121000L).put("tmSeq", 1))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        // 강원특별자치도엔 영월교도소 외에도 다른 교정기관이 있어 여러 행이 나올 수 있음 -
        // "전국보다 적다"와 "영월교도소(1272038)가 포함된다"만 검증
        assertThat(rows.length()).isGreaterThan(0).isLessThan(59);
        boolean hasYeongwol = false;
        for (int i = 0; i < rows.length(); i++) {
            if (rows.getJSONObject(i).getString("facilityId").equals("1272038")) {
                hasYeongwol = true;
            }
        }
        assertThat(hasYeongwol).isTrue();
    }

    @Test
    void 알수없는_stnId는_매칭없이_결과에서_빠진다() throws CleanseException {
        String raw = new JSONArray()
            .put(new JSONObject().put("title", "호우주의보").put("stnId", "999").put("tmFc", 202608121000L).put("tmSeq", 1))
            .toString();

        String result = cleanser.cleanse(raw);

        assertThat(new JSONArray(result).length()).isZero();
    }

    @Test
    void title이_없으면_CleanseException을_던진다() {
        String raw = new JSONArray().put(new JSONObject().put("stnId", "108")).toString();

        assertThatThrownBy(() -> cleanser.cleanse(raw))
            .isInstanceOf(CleanseException.class);
    }

    @Test
    void 구조_프로브가_실측_필드_4개와_일치한다() {
        String raw = new JSONArray()
            .put(new JSONObject().put("title", "호우주의보").put("stnId", "108").put("tmFc", 202608121000L).put("tmSeq", 35))
            .toString();
        StructureProbe probe = cleanser.structureProbes().get(0);

        Set<String> observed = probe.observer().apply(new JSONArray(raw));
        assertThat(observed).containsExactlyInAnyOrder("title", "stnId", "tmFc", "tmSeq");
        assertThat(probe.knownFields()).containsExactlyInAnyOrderElementsOf(observed);
    }

    @Test
    void 새_필드가_섞여오면_구조_프로브가_잡아낸다() {
        String raw = new JSONArray()
            .put(new JSONObject().put("title", "호우주의보").put("stnId", "108").put("tmFc", 1L).put("tmSeq", 1)
                .put("newField", "미확인"))
            .toString();
        StructureProbe probe = cleanser.structureProbes().get(0);

        Set<String> observed = probe.observer().apply(new JSONArray(raw));
        Set<String> added = new HashSet<>(observed);
        added.removeAll(probe.knownFields());
        assertThat(added).containsExactly("newField");
    }
}
