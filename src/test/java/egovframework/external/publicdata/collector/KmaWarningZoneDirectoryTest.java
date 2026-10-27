package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 API허브 응답(2026-10-27, wrn_reg_aws2.php - 관측소 711곳)과 실제 시설 위경도로 특보구역 계산을 확인한다.
 */
class KmaWarningZoneDirectoryTest {

    private static KmaWarningZoneDirectory directory;
    /** 해발까지 쓰는 경우 - 값은 시설 지형 분석 자료(private-doc/facility-terrain.csv, OpenTopoData SRTM 30m)와 같다. */
    private static KmaWarningZoneDirectory withElevation;
    private static final Map<String, FacilityMasterRecord> facilities = new HashMap<>();
    private static final Map<String, Double> ELEVATION_BY_ID = Map.of(
        "1270517", 210.0,
        "1270788", 51.0,
        "1270802", 25.0,
        "1270990", 169.0); // 제주교도소, 강릉교도소, 인천구치소, 소망교도소

    @BeforeAll
    static void load() throws IOException {
        String text;
        try (InputStream in = KmaWarningZoneDirectoryTest.class.getResourceAsStream("/kma-wrn-reg-aws2.txt")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (FacilityMasterRecord f : new FacilityMasterCsvLoader().all()) {
            facilities.put(f.facilityName(), f);
        }
        directory = KmaWarningZoneDirectory.fromText(text);
        Map<String, Double> byCoord = new HashMap<>();
        facilities.values().forEach(f -> {
            if (ELEVATION_BY_ID.containsKey(f.facilityId())) {
                byCoord.put(f.lat() + "," + f.lon(), ELEVATION_BY_ID.get(f.facilityId()));
            }
        });
        withElevation = KmaWarningZoneDirectory.fromText(text, (lat, lon) -> java.util.Optional.ofNullable(byCoord.get(lat + "," + lon)));
    }

    private static KmaWarningZone zoneOf(String facilityName) {
        return directory.resolve(facilities.get(facilityName)).orElseThrow(() -> new AssertionError(facilityName + " 구역 계산 안 됨"));
    }

    @Test
    void 응답에서_육상_구역만_읽는다() {
        assertThat(KmaWarningZoneDirectory.parse("""
            #STN_ID, STN_KO, STN_SP, LON, LAT, HT, FCT_ID, WRN_ID, WRN_KO, SFC_STN_ID=
             42,   군산오식도,     01211111, 126.59737000,  35.93681000,     2.05,   21F10501,   L1061610,    군산(옥도면 제외),   140,=
             99,   해상부이,     01211111, 126.0,  35.0,     0,   21F10501,   S1231300,    전남북부서해앞바다,   140,=
            """)).containsExactly(new KmaWarningZoneDirectory.Station("L1061610", "군산(옥도면 제외)", 35.93681, 126.59737, 2.05));
    }

    @Test
    void 시군이_세분된_곳은_시설_위치로_구역을_가른다() {
        assertThat(zoneOf("홍성교도소").name()).isEqualTo("홍성동부");
        assertThat(zoneOf("해남교도소").name()).isEqualTo("해남북부");
        assertThat(zoneOf("목포교도소").name()).isEqualTo("무안남부");   // 무안군 소재
        assertThat(zoneOf("안동교도소").name()).isEqualTo("안동서부");
        assertThat(zoneOf("제주교도소").name()).isEqualTo("제주시중산간");
    }

    @Test
    void 인천구치소는_운영자가_준_기상청_자료대로_인천남부다() {
        KmaWarningZone zone = zoneOf("인천구치소");
        assertThat(zone.name()).isEqualTo("인천남부");
        assertThat(zone.nearBorder()).isTrue(); // 인천북부 관측소와 2.5km 차이 - 확인 권장 로그 대상
    }

    @Test
    void 군산교도소는_섬_구역이_아니라_육지_구역이다() {
        assertThat(zoneOf("군산교도소").name()).isEqualTo("군산(옥도면 제외)");
    }

    @Test
    void 시군을_고정하므로_가장_가까운_관측소가_다른_시군이어도_넘어가지_않는다() {
        // 가장 가까운 관측소만 보면 안양->군포, 진주->의령, 순천->광양, 청송->영양평지 로 나왔다
        assertThat(zoneOf("안양교도소").name()).startsWith("안양");
        assertThat(zoneOf("진주교도소").name()).startsWith("진주");
        assertThat(zoneOf("순천교도소").name()).startsWith("순천");
        assertThat(zoneOf("경북북부제1교도소").name()).startsWith("청송");
    }

    @Test
    void 광역시_자치구는_광역시_구역으로_광역시_안의_군은_군_구역으로_찾는다() {
        assertThat(zoneOf("서울동부구치소").name()).isEqualTo("서울동남권");
        assertThat(zoneOf("광주교도소").name()).isEqualTo("광주동부");
        assertThat(zoneOf("대구교도소").name()).isEqualTo("달성북부");    // 대구 달성군
        assertThat(zoneOf("대구지방교정청").name()).startsWith("대구");   // 대구 달서구
    }

    @Test
    void 위경도가_없거나_자료가_없으면_빈_값이다() {
        FacilityMasterRecord noCoord = new FacilityMasterRecord("1", "좌표없음", "충청남도", "홍성군", "1", "1");
        assertThat(directory.resolve(noCoord)).isEmpty();

        KmaWarningZoneDirectory empty = KmaWarningZoneDirectory.fromText("");
        assertThat(empty.resolve(facilities.get("홍성교도소"))).isEqualTo(Optional.empty());
    }

    @Test
    void 같은_시군_관측소가_너무_멀면_정하지_않는다() {
        // 홍성군 이름에 서울 좌표 - 홍성 관측소가 100km 넘게 떨어져 있다
        FacilityMasterRecord wrong = new FacilityMasterRecord("2", "좌표오류", "충청남도", "홍성군", "1", "1", 37.5, 127.0);
        assertThat(directory.resolve(wrong)).isEmpty();
    }

    @Test
    void 시설_59곳_모두_구역이_계산된다() {
        assertThat(facilities.values()).allSatisfy(f -> assertThat(directory.resolve(f)).as(f.facilityName()).isPresent());
    }

    @Test
    void 경계_근처면_이웃_구역_특보도_받는다() {
        // 이웃 구역 관측소가 가장 가까운 구역보다 3km 이내로만 더 멀면 두 구역 모두(놓치지 않는 쪽)
        assertThat(zoneOf("인천구치소").names()).containsExactly("인천남부", "인천북부");
        assertThat(zoneOf("부산구치소").names()).containsExactly("부산중부", "부산서부");
        assertThat(zoneOf("소망교도소").names()).containsExactlyInAnyOrder("여주동남부", "여주서부");
        // 거리 차가 충분하면 한 구역
        assertThat(zoneOf("홍성교도소").names()).containsExactly("홍성동부");
        assertThat(zoneOf("제주교도소").names()).containsExactly("제주시중산간");
    }

    @Test
    void 시설_해발로_고도가_다른_구역을_뺀다() {
        // 제주교도소 해발 210m - 제주시북부(5.9km)는 거리로 이미 빠지고, 제주도산지(760m 이상)는 고도로도 맞지 않는다
        KmaWarningZone jeju = withElevation.resolve(facilities.get("제주교도소")).orElseThrow();
        assertThat(jeju.names()).containsExactly("제주시중산간");
        assertThat(jeju.elevationM()).isEqualTo(210.0);
        // 강릉교도소 해발 51m - 강릉산지(456m 이상)는 받지 않는다
        assertThat(withElevation.resolve(facilities.get("강릉교도소")).orElseThrow().names()).containsExactly("강릉평지");
        // 소망교도소 해발 169m - 여주동남부/여주서부 모두 고도 범위 안이라 둘 다(경계값에 좌우되지 않게)
        assertThat(withElevation.resolve(facilities.get("소망교도소")).orElseThrow().names()).containsExactlyInAnyOrder("여주동남부", "여주서부");
        // 인천은 두 구역 모두 해발이 낮아 그대로 둘 다
        assertThat(withElevation.resolve(facilities.get("인천구치소")).orElseThrow().names()).containsExactly("인천남부", "인천북부");
    }

    @Test
    void 해발_범위는_구역_관측소_해발에_여유를_둔다() {
        List<KmaWarningZoneDirectory.Station> zone = List.of(
            new KmaWarningZoneDirectory.Station("L1", "제주시북부", 33.5, 126.5, 5),
            new KmaWarningZoneDirectory.Station("L1", "제주시북부", 33.5, 126.5, 144));
        assertThat(KmaWarningZoneDirectory.fitsElevation(zone, 240)).isTrue();   // 144 + 100 이내
        assertThat(KmaWarningZoneDirectory.fitsElevation(zone, 300)).isFalse();
    }
}
