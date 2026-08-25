package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.CsvFacilityMasterSource;
import egovframework.external.publicdata.collector.FacilityMasterCsvLoader;
import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.FacilitySidoLoader;
import egovframework.external.publicdata.collector.LivingWthrIdxAreaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 생활기상지수(자외선/대기정체지수) 공통 정제기 테스트. 서울특별시(areaNo=1100000000)엔
 * 서울동부구치소/서울남부구치소/서울남부교도소 3개소가 있음(kma-facility-locations.csv 실측).
 */
class KmaLivingWthrIdxCleanserTest {

    private final KmaLivingWthrIdxCleanser cleanser =
        new KmaLivingWthrIdxCleanser(new LivingWthrIdxAreaLoader(), new FacilitySidoLoader(new CsvFacilityMasterSource(new FacilityMasterCsvLoader())));

    @Test
    void operationKey로_두_오퍼레이션_모두_지원한다() {
        assertThat(cleanser.supports("kma-living-uv-idx")).isTrue();
        assertThat(cleanser.supports("kma-living-air-diffusion-idx")).isTrue();
        assertThat(cleanser.supports("kma-weather-warning-list")).isFalse();
    }

    @Test
    void UV_h0가_있으면_서울_3개소에_각각_매칭되고_빈값_offset은_건너뛴다() throws CleanseException {
        JSONObject item = new JSONObject()
            .put("code", "A07_2").put("areaNo", "1100000000").put("date", "2026082412")
            .put("h0", "9").put("h3", "").put("h6", JSONObject.NULL);
        String raw = new JSONArray().put(item).toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(3); // 3개소 × offset 1개(h0만 값 있음)
        Set<String> facilityIds = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            assertThat(row.getInt("offsetHours")).isZero();
            assertThat(row.getString("value")).isEqualTo("9");
            assertThat(row.getString("code")).isEqualTo("A07_2");
            facilityIds.add(row.getString("facilityId"));
        }
        assertThat(facilityIds).containsExactlyInAnyOrder("1270801", "1270800", "1270783");
    }

    @Test
    void 대기정체지수는_h0가_없고_h78까지_있다() throws CleanseException {
        JSONObject item = new JSONObject()
            .put("code", "A09").put("areaNo", "1100000000").put("date", "2026082412")
            .put("h3", "50").put("h78", "75");
        String raw = new JSONArray().put(item).toString();

        String result = cleanser.cleanse(raw);

        JSONArray rows = new JSONArray(result);
        assertThat(rows.length()).isEqualTo(6); // 3개소 × offset 2개(h3, h78)
        Set<Integer> offsets = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            offsets.add(rows.getJSONObject(i).getInt("offsetHours"));
        }
        assertThat(offsets).containsExactlyInAnyOrder(3, 78);
    }

    @Test
    void 알수없는_areaNo는_매칭없이_결과에서_빠진다() throws CleanseException {
        JSONObject item = new JSONObject()
            .put("code", "A07_2").put("areaNo", "9999999999").put("date", "2026082412").put("h0", "9");
        String raw = new JSONArray().put(item).toString();

        String result = cleanser.cleanse(raw);

        assertThat(new JSONArray(result).length()).isZero();
    }
}
