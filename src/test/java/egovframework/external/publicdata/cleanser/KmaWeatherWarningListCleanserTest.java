package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.CsvFacilityMasterSource;
import egovframework.external.publicdata.collector.FacilityMasterCsvLoader;
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
        new KmaWeatherWarningListCleanser(new KmaWarningStationLoader(), new CsvFacilityMasterSource(new FacilityMasterCsvLoader()));

    @Test
    void operationKey로만_지원여부를_판단한다() {
        assertThat(cleanser.supports("kma-weather-warning-list")).isTrue();
        assertThat(cleanser.supports("kma-village-forecast-vilage-fcst")).isFalse();
    }

    @Test
    void stnId_108_상세구역_없는_육상특보는_59개소_전부에_매칭된다() throws CleanseException {
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
    void 통보문_조회_실패로_구역텍스트가_없는_복합_해상특보는_서울동부구치소에_매칭되지_않는다() throws CleanseException {
        // 2026-09-22 실측: getWthrWrnMsg 조회 실패로 t2/t6 없이 목록 원본만 온 경우 - 서울동부구치소
        // 기준 풍랑·강풍주의보가 떴다는 고객 문의의 원인. t2/t6가 없어도(=구역 확인 불가) 해상
        // 키워드가 섞인 특보는 매칭 보류돼야 한다.
        String raw = new JSONArray()
            .put(new JSONObject()
                .put("title", "[특보] 제09-100호 : 2026.09.21.17:00 / 강풍주의보·풍랑주의보 발표 (*)")
                .put("stnId", "108")
                .put("tmFc", 202609211700L)
                .put("tmSeq", 100))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        for (int i = 0; i < rows.length(); i++) {
            assertThat(rows.getJSONObject(i).getString("facilityId")).isNotEqualTo("1270801");
        }
    }

    @Test
    void stnId_108_순수_풍랑주의보는_전국_어느_교정기관에도_매칭되지_않는다() throws CleanseException {
        // 실제 2026-09-22 06:00 제09-102호 실사례
        String raw = new JSONArray()
            .put(new JSONObject()
                .put("title", "[특보] 제09-102호 : 2026.09.22.06:00 / 풍랑주의보 발표 (*)")
                .put("stnId", "108")
                .put("tmFc", 202609220600L)
                .put("tmSeq", 102)
                .put("t1", "풍랑주의보 발표")
                .put("t2", "(1) 풍랑주의보 발표 : 제주도남쪽바깥먼바다, 제주도남동쪽안쪽먼바다"))
            .toString();

        String result = cleanser.cleanse(raw);

        // 풍랑주의보는 해상 특보이므로 육상 교정기관에 매칭되지 않아야 함
        assertThat(new JSONArray(result).length()).isZero();
    }

    @Test
    void 복합특보_강풍과_풍랑_발표시_울릉도_구역만_있으면_서울동부구치소_서울구치소에는_매칭되지_않는다() throws CleanseException {
        // 실제 2026-09-21 17:00 제09-100호 실사례
        String raw = new JSONArray()
            .put(new JSONObject()
                .put("title", "[특보] 제09-100호 : 2026.09.21.17:00 / 강풍주의보·풍랑주의보 발표 (*)")
                .put("stnId", "108")
                .put("tmFc", 202609211700L)
                .put("tmSeq", 100)
                .put("t1", "강풍주의보·풍랑주의보 발표")
                .put("t2", "(1) 풍랑주의보 발표 : 동해남부북쪽먼바다\n(2) 강풍주의보 발표 : 울릉도.독도"))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            // 서울동부구치소(1270801), 서울구치소(1270552)는 없어야 함
            assertThat(row.getString("facilityId")).isNotEqualTo("1270801");
            assertThat(row.getString("facilityId")).isNotEqualTo("1270552");
        }
    }

    @Test
    void 서울_및_경기_의왕_호우주의보_통보문은_해당_시설에만_정확히_매칭된다() throws CleanseException {
        String raw = new JSONArray()
            .put(new JSONObject()
                .put("title", "[특보] 호우주의보 발표")
                .put("stnId", "108")
                .put("tmFc", 202609221000L)
                .put("tmSeq", 1)
                .put("t1", "호우주의보 발표")
                .put("t2", "(1) 호우주의보 발표 : 서울특별시, 경기도(의왕, 성남)"))
            .toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        Set<String> matchedFacilityIds = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            matchedFacilityIds.add(rows.getJSONObject(i).getString("facilityId"));
        }

        // 서울동부구치소(1270801, 서울 송파구), 서울구치소(1270552, 경기 의왕시) 포함 확인
        assertThat(matchedFacilityIds).contains("1270801", "1270552");
        // 강원도 영월교도소(1272038) 미포함 확인
        assertThat(matchedFacilityIds).doesNotContain("1272038");
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
    void 구조_프로브가_필수_필드_4개_및_통보문_상세_필드를_지원한다() {
        String raw = new JSONArray()
            .put(new JSONObject().put("title", "호우주의보").put("stnId", "108").put("tmFc", 202608121000L).put("tmSeq", 35))
            .toString();
        StructureProbe probe = cleanser.structureProbes().get(0);

        Set<String> observed = probe.observer().apply(new JSONArray(raw));
        assertThat(observed).containsExactlyInAnyOrder("title", "stnId", "tmFc", "tmSeq");
        assertThat(probe.requiredFields()).containsExactlyInAnyOrderElementsOf(observed);
        assertThat(probe.knownFields()).containsAll(observed);
    }

    @Test
    void 새_필드가_섞여오면_구조_프로브가_잡아낸다() {
        String raw = new JSONArray()
            .put(new JSONObject().put("title", "호우주의보").put("stnId", "108").put("tmFc", 1L).put("tmSeq", 1)
                .put("completelyUnknownField", "미확인"))
            .toString();
        StructureProbe probe = cleanser.structureProbes().get(0);

        Set<String> observed = probe.observer().apply(new JSONArray(raw));
        Set<String> added = new HashSet<>(observed);
        added.removeAll(probe.knownFields());
        assertThat(added).containsExactly("completelyUnknownField");
    }
}
