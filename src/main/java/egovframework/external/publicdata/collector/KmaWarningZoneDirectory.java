package egovframework.external.publicdata.collector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시설 위치(위경도)로 기상특보 세분 구역(홍성동부/홍성서부, 인천남부/인천북부, 군산 육지/어청도 등)을 정한다 (2026-10-27).
 *
 * <p>기상청은 한 시군을 동서남북·평지/산지·섬으로 나눠 특보를 내는데, 시설 마스터에는 시군까지만 있어 어느 쪽인지 알 수 없었다.
 * 시설이 늘 때마다 사람이 구역을 입력하게 하는 대신, 기상청 API허브의 "AWS가 속한 특보구역 코드"(관측소 711곳의 위경도와 소속
 * 특보구역)로 계산한다:</p>
 * <ol>
 *   <li>시군은 마스터 값으로 고정한다 - 가장 가까운 관측소만 보면 시군 경계를 넘어 엉뚱한 구역이 나온다(실측: 안양→군포, 진주→의령).</li>
 *   <li>그 시군 이름으로 시작하는 구역의 관측소 중 가장 가까운 곳의 구역을 쓴다. 광역시 자치구처럼 구역 이름에 구 이름이 안 나오면
 *       광역시 이름으로 찾는다("서울동남권", "대구중부").</li>
 *   <li>다른 구역 관측소가 {@link KmaWarningZone#BORDER_MARGIN_KM} 이내로 더 멀 뿐이면 경계 근처로 보고 그 구역 특보도 받는다
 *       (놓치는 것보다 과하게 알리는 쪽, 2026-10-27 사용자 결정). WARN으로 남긴다.</li>
 *   <li>시설 해발({@link FacilityElevationLookup})을 알면, 시설 해발이 그 구역 관측소들의 해발 범위(±{@link #ELEVATION_MARGIN_M}m)
 *       밖인 구역은 뺀다 - 강릉평지/산지처럼 고도로 크게 나뉜 구역을 가른다(강릉교도소 51m, 강릉산지 관측소 456m 이상).</li>
 * </ol>
 * <p>자료를 못 받거나 계산이 안 되면 빈 값을 준다 - 매처가 시군 단위로 매칭(예전 동작)하므로 특보가 빠지지는 않는다.</p>
 */
@Component
public class KmaWarningZoneDirectory {

    private static final Logger logger = LogManager.getLogger(KmaWarningZoneDirectory.class);

    /** API허브 typ01 계열은 CP949로 응답한다 ({@link KmaApiHubClient} 참고). */
    private static final Charset RESPONSE_CHARSET = Charset.forName("CP949");
    /** 자료를 아직 못 받았을 때 다시 시도하는 간격 - 매 정제 주기(10분)마다 호출하지 않게 한다. */
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(30);
    /** 같은 시군 관측소라도 이보다 멀면 계산 결과를 믿지 않는다. */
    private static final double MAX_DISTANCE_KM = 40.0;
    /**
     * 구역 관측소 해발 범위에 두는 여유. 고도 규칙은 평지/산지처럼 수백 m 차이 나는 구역을 거르는 용도라 넉넉히 둔다 -
     * 50m로 했더니 소망교도소(169m)가 여주동남부 관측소 최고(약 119m)+50m를 0.4m 넘어 가장 가까운 구역이 빠졌다(2026-10-27).
     */
    static final double ELEVATION_MARGIN_M = 100.0;

    private static final List<String> METRO_NAMES = List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종");
    private static final Set<String> GWANGJU_DISTRICTS = Set.of("동구", "서구", "남구", "북구", "광산구");

    record Station(String zoneCode, String zoneName, double lat, double lon, double heightM) {
    }

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String authKey;
    private final FacilityElevationLookup elevationLookup;

    private volatile List<Station> stations = List.of();
    private volatile Instant lastAttempt;
    private final Map<String, Optional<KmaWarningZone>> cache = new ConcurrentHashMap<>();

    @Autowired
    public KmaWarningZoneDirectory(
        @Qualifier("kmaApiHubRestTemplate") RestTemplate restTemplate,
        @Value("${public-data.kma.warning-zone.endpoint:https://apihub.kma.go.kr/api/typ01/url/wrn_reg_aws2.php}") String endpoint,
        @Value("${public-data.kma.asos-hourly.auth-key:}") String authKey,
        FacilityElevationLookup elevationLookup
    ) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
        this.authKey = authKey;
        this.elevationLookup = elevationLookup == null ? FacilityElevationLookup.NONE : elevationLookup;
    }

    /** 이미 받아 둔 응답 텍스트로 만든다 - 테스트용. */
    static KmaWarningZoneDirectory fromText(String responseText) {
        return fromText(responseText, FacilityElevationLookup.NONE);
    }

    static KmaWarningZoneDirectory fromText(String responseText, FacilityElevationLookup elevationLookup) {
        KmaWarningZoneDirectory directory = new KmaWarningZoneDirectory(null, null, null, elevationLookup);
        directory.stations = parse(responseText);
        directory.lastAttempt = Instant.now();
        return directory;
    }

    /** 기상청이 특보구역을 개편할 수 있으니 하루 한 번 새로 받는다. 실패하면 이전 자료를 그대로 쓴다. */
    @Scheduled(cron = "${public-data.kma.warning-zone.refresh-cron:0 40 4 * * *}")
    public void refresh() {
        lastAttempt = Instant.now();
        if (restTemplate == null || endpoint == null || endpoint.isBlank() || authKey == null || authKey.isBlank()) {
            logger.warn("[WARN-ZONE] 특보구역 자료 엔드포인트/인증키 없음 - 세분 구역 없이 시군 단위로 매칭함");
            return;
        }
        try {
            byte[] raw = restTemplate.getForObject(URI.create(endpoint + "?tm=&disp=1&help=0&authKey=" + authKey), byte[].class);
            List<Station> parsed = raw == null ? List.of() : parse(new String(raw, RESPONSE_CHARSET));
            if (parsed.isEmpty()) {
                logger.warn("[WARN-ZONE] 특보구역 자료가 비어 있음(활용신청/형식 변경 확인 필요) - 이전 자료 유지({}곳)", stations.size());
                return;
            }
            stations = parsed;
            cache.clear();
            logger.info("[WARN-ZONE] 특보구역 자료 갱신 - 육상 관측소 {}곳", parsed.size());
        } catch (Exception e) {
            // 인증키가 메시지에 섞이지 않게 예외 종류만 남긴다
            logger.warn("[WARN-ZONE] 특보구역 자료 조회 실패 - 이전 자료 유지({}곳): {}", stations.size(), e.getClass().getSimpleName());
        }
    }

    /** 시설의 특보구역. 위경도가 없거나 자료가 없거나 계산이 믿기 어려우면 빈 값. */
    public Optional<KmaWarningZone> resolve(FacilityMasterRecord facility) {
        if (facility.lat() == null || facility.lon() == null) {
            return Optional.empty();
        }
        if (stations.isEmpty() && (lastAttempt == null || lastAttempt.isBefore(Instant.now().minus(RETRY_INTERVAL)))) {
            refresh();
        }
        String key = facility.facilityId() + "|" + facility.lat() + "|" + facility.lon() + "|" + facility.sido() + "|" + facility.sigungu();
        return cache.computeIfAbsent(key, k -> {
            Optional<KmaWarningZone> zone = compute(facility);
            zone.ifPresent(z -> log(facility, z));
            return zone;
        });
    }

    private Optional<KmaWarningZone> compute(FacilityMasterRecord f) {
        List<Station> all = stations;
        String prefix = areaPrefix(f, all);
        if (prefix == null) {
            return Optional.empty();
        }
        // 같은 시군의 구역별로 가장 가까운 관측소 거리
        Map<String, List<Station>> byZone = new java.util.LinkedHashMap<>();
        for (Station s : all) {
            if (s.zoneName().startsWith(prefix)) {
                byZone.computeIfAbsent(s.zoneCode(), k -> new ArrayList<>()).add(s);
            }
        }
        if (byZone.isEmpty()) {
            return Optional.empty();
        }
        List<Map.Entry<String, Double>> zonesByDistance = byZone.entrySet().stream()
            .map(e -> Map.entry(e.getKey(), e.getValue().stream()
                .mapToDouble(s -> distanceKm(f.lat(), f.lon(), s.lat(), s.lon())).min().orElse(Double.MAX_VALUE)))
            .sorted(Map.Entry.comparingByValue())
            .toList();
        double nearestKm = zonesByDistance.get(0).getValue();
        if (nearestKm > MAX_DISTANCE_KM) {
            logger.warn("[WARN-ZONE] {}({} {}) - 같은 시군 관측소가 {}km 떨어져 있어 세분 구역을 정하지 않음",
                f.facilityName(), f.sido(), f.sigungu(), Math.round(nearestKm));
            return Optional.empty();
        }

        // 1) 거리: 가장 가까운 구역 + 그보다 BORDER_MARGIN_KM 이내로만 더 먼 구역
        List<String> included = new ArrayList<>();
        for (Map.Entry<String, Double> z : zonesByDistance) {
            if (z.getValue() <= nearestKm + KmaWarningZone.BORDER_MARGIN_KM) {
                included.add(z.getKey());
            }
        }
        // 2) 고도: 시설 해발이 구역 관측소 해발 범위 밖이면 뺀다(다 빠지면 가장 가까운 구역은 남긴다)
        Double elevation = elevationLookup.elevationM(f.lat(), f.lon()).orElse(null);
        if (elevation != null) {
            List<String> byHeight = included.stream().filter(code -> fitsElevation(byZone.get(code), elevation)).toList();
            if (!byHeight.isEmpty()) {
                included = byHeight;
            }
        }

        String primary = included.get(0);
        String primaryName = byZone.get(primary).get(0).zoneName();
        List<String> names = included.stream().map(code -> byZone.get(code).get(0).zoneName()).toList();
        Optional<Map.Entry<String, Double>> runnerUp = zonesByDistance.stream().filter(z -> !z.getKey().equals(primary)).findFirst();
        double primaryKm = zonesByDistance.stream().filter(z -> z.getKey().equals(primary)).findFirst().orElseThrow().getValue();
        return Optional.of(new KmaWarningZone(primary, primaryName, prefix, primaryKm,
            runnerUp.map(z -> byZone.get(z.getKey()).get(0).zoneName()).orElse(null),
            runnerUp.map(Map.Entry::getValue).orElse(null),
            names, elevation));
    }

    static boolean fitsElevation(List<Station> zoneStations, double elevationM) {
        double min = zoneStations.stream().mapToDouble(Station::heightM).min().orElse(Double.NaN);
        double max = zoneStations.stream().mapToDouble(Station::heightM).max().orElse(Double.NaN);
        return elevationM >= min - ELEVATION_MARGIN_M && elevationM <= max + ELEVATION_MARGIN_M;
    }

    /** 시군 이름으로 시작하는 구역이 있으면 시군 이름, 없으면(광역시 자치구) 광역시 이름. */
    static String areaPrefix(FacilityMasterRecord f, List<Station> all) {
        String core = municipalityCore(f.sigungu());
        if (core != null && core.length() >= 2 && all.stream().anyMatch(s -> s.zoneName().startsWith(core))) {
            return core;
        }
        String sido = f.sido() == null ? "" : f.sido();
        if (sido.contains("광주") && f.sigungu() != null && GWANGJU_DISTRICTS.contains(f.sigungu())) {
            return "광주";
        }
        return METRO_NAMES.stream().filter(sido::startsWith).findFirst().orElse(null);
    }

    /** "홍성군" -> "홍성", "안양시동안구" -> "안양", "달성군" -> "달성". */
    static String municipalityCore(String sigungu) {
        if (sigungu == null || sigungu.isBlank()) {
            return null;
        }
        for (char suffix : new char[] {'시', '군', '구'}) {
            int idx = sigungu.indexOf(suffix);
            if (idx > 0) {
                return sigungu.substring(0, idx);
            }
        }
        return sigungu;
    }

    /**
     * 응답 한 줄: {@code STN_ID, STN_KO, STN_SP, LON, LAT, HT, FCT_ID, WRN_ID, WRN_KO, SFC_STN_ID=}.
     * 구역 이름에 공백·괄호가 있어("군산(옥도면 제외)") 쉼표로만 나눈다. 해상 구역(S...)은 버린다.
     */
    static List<Station> parse(String text) {
        List<Station> result = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.endsWith("=")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            String[] cols = trimmed.split(",");
            if (cols.length < 9) {
                continue;
            }
            String zoneCode = cols[7].strip();
            String zoneName = cols[8].strip();
            if (!zoneCode.startsWith("L") || zoneName.isEmpty()) {
                continue;
            }
            try {
                result.add(new Station(zoneCode, zoneName, Double.parseDouble(cols[4].strip()), Double.parseDouble(cols[3].strip()),
                    Double.parseDouble(cols[5].strip())));
            } catch (NumberFormatException e) {
                // 좌표가 없는 줄은 건너뛴다
            }
        }
        return result;
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double x = Math.toRadians(lon2 - lon1) * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        double y = Math.toRadians(lat2 - lat1);
        return 6371.0 * Math.hypot(x, y);
    }

    private static void log(FacilityMasterRecord f, KmaWarningZone z) {
        String elevation = z.elevationM() == null ? "해발 모름" : "해발 " + Math.round(z.elevationM()) + "m";
        if (z.nearBorder()) {
            logger.warn("[WARN-ZONE] {}({} {}) -> {} ({}, {}km) - 경계 근처라 이웃 구역 특보도 받음, 확인 권장",
                f.facilityName(), f.sido(), f.sigungu(), z.names(), elevation, round1(z.nearestKm()));
        } else {
            logger.info("[WARN-ZONE] {}({} {}) -> {}({}) {}km, {}", f.facilityName(), f.sido(), f.sigungu(), z.name(), z.code(),
                round1(z.nearestKm()), elevation);
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
