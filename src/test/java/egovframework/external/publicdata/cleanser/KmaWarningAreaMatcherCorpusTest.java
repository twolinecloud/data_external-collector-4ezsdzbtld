package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.FacilityMasterRecord;
import egovframework.external.publicdata.collector.KmaWarningStation;
import egovframework.external.publicdata.collector.KmaWarningStationLoader;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영에 저장된 실제 통보문(2026-09-23 ~ 10-26, 구역 정보가 있는 169건)으로 매처를 전수 점검한다.
 * 개별 사례 테스트는 "그 사례"만 지키지만, 이 테스트는 시설 59곳 x 통보문 전체에서 지명 문자열 포함 검사 방식이
 * 만들던 오매칭(다른 지역 이름에 우연히 들어 있는 글자에 걸림)이 다시 생기는지 본다.
 * 통보문을 새로 모아 갱신할 때는 {@code scripts} 가 아니라 운영 DB의 tb_ext_weather_warning raw_json(t1,t2,t6)에서 뽑는다.
 */
class KmaWarningAreaMatcherCorpusTest {

    /** 시설 시·도 -> 통보문에 그 시·도가 아예 없는데 매칭되면 안 되는 표기들. 테스트에서 독립적으로 정의해 매처와 같은 실수를 하지 않게 한다. */
    private static final Map<String, List<String>> PROVINCE_NAMES = Map.ofEntries(
        Map.entry("서울특별시", List.of("서울")),
        Map.entry("경기도", List.of("경기")),
        Map.entry("인천광역시", List.of("인천")),
        Map.entry("강원특별자치도", List.of("강원")),
        Map.entry("충청북도", List.of("충청북도", "충북")),
        Map.entry("충청남도", List.of("충청남도", "충남")),
        Map.entry("전북특별자치도", List.of("전북", "전라북도")),
        Map.entry("전남광주통합특별시", List.of("전라남도", "전남", "광주")),
        Map.entry("경상북도", List.of("경상북도", "경북")),
        Map.entry("경상남도", List.of("경상남도", "경남")),
        Map.entry("제주특별자치도", List.of("제주")),
        Map.entry("대구광역시", List.of("대구")),
        Map.entry("부산광역시", List.of("부산")),
        Map.entry("울산광역시", List.of("울산")),
        Map.entry("대전광역시", List.of("대전")),
        Map.entry("세종특별자치시", List.of("세종"))
    );

    private record Bulletin(String stnId, String title, String t1, String t2, String t6) {
    }

    private static List<Bulletin> bulletins() throws IOException {
        List<Bulletin> list = new ArrayList<>();
        try (InputStream in = KmaWarningAreaMatcherCorpusTest.class.getResourceAsStream("/kma-warning-corpus.jsonl")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                JSONObject o = new JSONObject(line);
                list.add(new Bulletin(o.getString("stn"), o.getString("title"),
                    o.isNull("t1") ? null : o.getString("t1"),
                    o.isNull("t2") ? null : o.getString("t2"),
                    o.isNull("t6") ? null : o.getString("t6")));
            }
        }
        return list;
    }

    private static List<FacilityMasterRecord> facilities() throws IOException {
        List<FacilityMasterRecord> list = new ArrayList<>();
        for (String l : Files.readAllLines(Path.of("src/main/resources/kma-facility-locations.csv"))) {
            String[] c = l.split(",");
            if (c.length >= 6 && c[0].matches("\\d+")) {
                list.add(new FacilityMasterRecord(c[0], c[1], c[2], c[3], c[4], c[5]));
            }
        }
        return list;
    }

    @Test
    void 통보문에_시설의_시도_이름이_없으면_어떤_시설에도_매칭되지_않는다() throws IOException {
        Map<String, KmaWarningStation> stations = new HashMap<>();
        new KmaWarningStationLoader().all().forEach(s -> stations.put(s.stnId(), s));
        List<String> violations = new ArrayList<>();

        for (Bulletin b : bulletins()) {
            KmaWarningStation station = stations.get(b.stnId());
            String areaText = (b.t2() != null && !b.t2().isBlank()) ? b.t2() : b.t6();
            for (FacilityMasterRecord f : facilities()) {
                if (station == null || !station.covers(f.sido())) {
                    continue;
                }
                boolean matched = KmaWarningAreaMatcher.matches(b.title(), b.t1(), b.t2(), b.t6(), f);
                boolean provinceMentioned = PROVINCE_NAMES.getOrDefault(f.sido(), List.of()).stream().anyMatch(areaText::contains);
                if (matched && !provinceMentioned) {
                    violations.add(f.facilityName() + " <- " + b.title() + " | " + areaText.replace('\n', '/'));
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void 실제_통보문에서_과거에_오매칭되던_시설과_놓치던_시설이_바로잡힌다() throws IOException {
        FacilityMasterRecord incheon = facilities().stream().filter(f -> f.facilityId().equals("1270802")).findFirst().orElseThrow();
        FacilityMasterRecord pohang = facilities().stream().filter(f -> f.facilityName().equals("포항교도소")).findFirst().orElseThrow();
        FacilityMasterRecord gwangju = facilities().stream().filter(f -> f.facilityName().equals("광주교도소")).findFirst().orElseThrow();

        int incheonMatches = 0;
        for (Bulletin b : bulletins()) {
            incheonMatches += KmaWarningAreaMatcher.matches(b.title(), b.t1(), b.t2(), b.t6(), incheon) ? 1 : 0;
            // 포항·광주 북구 시설은 "북"/"북구"가 들어간 다른 지역 이름 때문에 매칭되던 통보문이 많았다
            if (b.t2() != null && b.t2().contains("제주도(제주시서부, 제주시북부)") && !b.t2().contains("경상북도(") ) {
                assertThat(KmaWarningAreaMatcher.matches(b.title(), b.t1(), b.t2(), b.t6(), pohang)).as(b.title()).isFalse();
                assertThat(KmaWarningAreaMatcher.matches(b.title(), b.t1(), b.t2(), b.t6(), gwangju)).as(b.title()).isFalse();
            }
        }
        // 인천(옹진 제외), 인천(강화, 인천남부, 인천북부) 형식의 통보문이 실제로 있었다
        assertThat(incheonMatches).isGreaterThanOrEqualTo(6);
    }
}
