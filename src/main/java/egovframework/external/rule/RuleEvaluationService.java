package egovframework.external.rule;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.publicdata.cleanser.CleansedJsonDropWriter;
import egovframework.external.staging.RawStagingStore;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 지형 기반 rule-base 재해 알림 평가 (private-doc/terrain-rule-base-spec.md 전체 참고).
 *
 * <p>raw_staging의 CLEANSED 행 중 날씨(초단기실황/초단기예보/단기예보), 재난문자
 * (safetydata-disaster-msg-list), 기상특보(kma-weather-warning-list) 정제 결과를 시설별로
 * 모아 {@link WeatherTriggerCalculator}/{@link HazardAlertMatrix}에 넣어 최종 등급을 낸다.</p>
 *
 * <p><b>지역신호(regionTriggered) 출처가 2가지다</b>:</p>
 * <ul>
 *   <li>재난문자 - 시군구 단위, {@code dstSeNm}으로 산사태/호우 모두 판단 가능 (§3-2)</li>
 *   <li>기상특보 - <b>시도 단위</b>(재난문자보다 거침), title 자유텍스트에서 "호우"만 뽑아낼 수
 *       있어 FLOOD만 판단 가능(산사태는 KMA 특보 현상이 아니라 산림청 소관이라 여기 안 옴).
 *       {@code getWthrWrnList}는 발표/해제 이력을 함께 반환하므로 stnId·현상 조합별로 가장
 *       최근 항목이 "발표"일 때만 활성으로 본다 (§7-1 stnId 매핑, KmaWarningStation 참고)</li>
 * </ul>
 * <p>둘 중 하나라도 감지되면 그 재해에 대해 regionTriggered=true (OR 결합).</p>
 *
 * <p>Load 단계(admin-db 적재)가 아직 없어 raw_staging은 CLEANSED에서 더 전이되지 않으므로,
 * 이 평가는 "그 시점에 CLEANSED 상태로 남아있는 것 전부"를 매번 다시 읽는 방식이다(멱등,
 * 누적 아님) - cleanse 스케줄이 도는 한 최신 데이터가 계속 쌓여 있다.</p>
 *
 * <p>결과는 DB가 없어 {@link CleansedJsonDropWriter}(정제 결과 디버그 덤프와 동일한 장치)를
 * 재사용해 {@code rule-alert-result.json}으로 떨어뜨린다 - 이름은 "cleanse" 전용처럼 보이지만
 * 실제로는 "로컬 파일로 즉시 확인 가능하게" 하는 범용 디버그 라이터라 그대로 재사용했다.</p>
 */
@Service
@RequiredArgsConstructor
public class RuleEvaluationService {

    private static final Logger logger = LogManager.getLogger(RuleEvaluationService.class);

    private static final Map<String, String> WEATHER_PRECIP_FIELD = Map.of(
        "kma-village-forecast-ultra-srt-ncst", "rn1",
        "kma-village-forecast-ultra-srt-fcst", "rn1",
        "kma-village-forecast-vilage-fcst", "pcp");
    private static final String DISASTER_MSG_OPERATION_KEY = "safetydata-disaster-msg-list";
    private static final String WEATHER_WARNING_OPERATION_KEY = "kma-weather-warning-list";
    private static final String SOURCE_DISASTER_MSG = "disasterMsg";
    private static final String SOURCE_WEATHER_WARNING = "weatherWarning";
    private static final String DROP_KEY = "rule-alert-result";
    private static final int BATCH_SIZE = 2000;
    private static final DateTimeFormatter TM_FC = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final RawStagingStore rawStagingStore;
    private final FacilityVulnerabilityLoader vulnerabilityLoader;
    private final FacilitySidoLoader facilitySidoLoader;
    private final KmaWarningStationLoader warningStationLoader;
    private final CleansedJsonDropWriter jsonDropWriter;

    public List<AlertResult> evaluateAll() {
        List<RawStagingDto> cleansed = rawStagingStore.findByStatus("CLEANSED", BATCH_SIZE);

        Map<String, List<HourlyPrecipitation>> precipByFacility = new HashMap<>();
        Map<String, Map<HazardType, Set<String>>> regionSourcesByFacility = new HashMap<>();
        // stnId별 "현상 -> 가장 최근(tmFc 최대) 특보의 활성여부" - 발표/해제 이력이 섞여 오므로 최신 것만 신뢰
        Map<String, Map<String, LatestWarning>> latestWarningByStation = new HashMap<>();

        for (RawStagingDto dto : cleansed) {
            String operationKey = dto.getOperationKey();
            String precipField = WEATHER_PRECIP_FIELD.get(operationKey);
            if (precipField != null && dto.getFacilityId() != null) {
                collectPrecipitation(dto, precipField, precipByFacility);
            } else if (DISASTER_MSG_OPERATION_KEY.equals(operationKey)) {
                collectDisasterMsgTriggers(dto, regionSourcesByFacility);
            } else if (WEATHER_WARNING_OPERATION_KEY.equals(operationKey)) {
                collectLatestWarnings(dto, latestWarningByStation);
            }
        }
        applyWeatherWarnings(latestWarningByStation, regionSourcesByFacility);

        LocalDateTime now = LocalDateTime.now();
        List<AlertResult> results = new ArrayList<>();
        for (FacilityVulnerability fv : vulnerabilityLoader.all()) {
            WeatherTrigger trigger = WeatherTriggerCalculator.calculate(
                precipByFacility.getOrDefault(fv.facilityId(), List.of()));
            Map<HazardType, Set<String>> sources = regionSourcesByFacility.getOrDefault(fv.facilityId(), Map.of());

            results.add(build(fv.facilityId(), HazardType.LANDSLIDE, fv.landslideVuln(), trigger,
                sources.getOrDefault(HazardType.LANDSLIDE, Set.of()), now));
            results.add(build(fv.facilityId(), HazardType.FLOOD, fv.floodVuln(), trigger,
                sources.getOrDefault(HazardType.FLOOD, Set.of()), now));
        }

        jsonDropWriter.write(DROP_KEY, toJson(results));
        return results;
    }

    private void collectPrecipitation(RawStagingDto dto, String precipField,
            Map<String, List<HourlyPrecipitation>> precipByFacility) {
        try {
            List<HourlyPrecipitation> readings = WeatherReadingExtractor.extract(dto.getCleansedPayload(), precipField);
            precipByFacility.computeIfAbsent(dto.getFacilityId(), k -> new ArrayList<>()).addAll(readings);
        } catch (Exception e) {
            logger.warn("[RULE] 강수 데이터 추출 실패: collectorKey={}, error={}", dto.getCollectorKey(), e.getMessage());
        }
    }

    private void collectDisasterMsgTriggers(RawStagingDto dto, Map<String, Map<HazardType, Set<String>>> byFacility) {
        try {
            JSONArray rows = new JSONArray(dto.getCleansedPayload());
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String facilityId = row.optString("facilityId", null);
                HazardType hazard = mapDisasterType(row.optString("dstSeNm", null));
                if (facilityId != null && hazard != null) {
                    addSource(byFacility, facilityId, hazard, SOURCE_DISASTER_MSG);
                }
            }
        } catch (Exception e) {
            logger.warn("[RULE] 재난문자 지역신호 추출 실패: collectorKey={}, error={}", dto.getCollectorKey(), e.getMessage());
        }
    }

    private record LatestWarning(LocalDateTime tmFc, boolean active) {
    }

    /** stnId·현상별로 발표시각이 가장 늦은 항목만 남긴다 (발표/해제 이력이 섞여 있어 최신 것만 유효). */
    private void collectLatestWarnings(RawStagingDto dto, Map<String, Map<String, LatestWarning>> byStation) {
        try {
            JSONArray rows = new JSONArray(dto.getCleansedPayload());
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String stnId = row.optString("stnId", null);
                WarningTitleParser.ParsedWarning parsed = WarningTitleParser.parse(row.optString("title", null))
                    .orElse(null);
                if (stnId == null || parsed == null) {
                    continue;
                }
                LocalDateTime tmFc = parseTmFc(row);
                if (tmFc == null) {
                    continue;
                }
                Map<String, LatestWarning> byPhenomenon = byStation.computeIfAbsent(stnId, k -> new HashMap<>());
                LatestWarning existing = byPhenomenon.get(parsed.phenomenon());
                if (existing == null || tmFc.isAfter(existing.tmFc())) {
                    byPhenomenon.put(parsed.phenomenon(), new LatestWarning(tmFc, parsed.isActive()));
                }
            }
        } catch (Exception e) {
            logger.warn("[RULE] 기상특보 지역신호 추출 실패: collectorKey={}, error={}", dto.getCollectorKey(), e.getMessage());
        }
    }

    /** 활성 상태인 stnId·현상만 걸러, 그 stnId가 관할하는 시도에 속한 시설들에 지역신호를 붙인다. */
    private void applyWeatherWarnings(Map<String, Map<String, LatestWarning>> byStation,
            Map<String, Map<HazardType, Set<String>>> byFacility) {
        if (byStation.isEmpty()) {
            return;
        }
        List<FacilitySido> facilities = facilitySidoLoader.all();
        for (KmaWarningStation station : warningStationLoader.all()) {
            Map<String, LatestWarning> byPhenomenon = byStation.get(station.stnId());
            if (byPhenomenon == null) {
                continue;
            }
            for (Map.Entry<String, LatestWarning> e : byPhenomenon.entrySet()) {
                HazardType hazard = mapWarningPhenomenon(e.getKey());
                if (hazard == null || !e.getValue().active()) {
                    continue;
                }
                for (FacilitySido fs : facilities) {
                    if (station.covers(fs.sido())) {
                        addSource(byFacility, fs.facilityId(), hazard, SOURCE_WEATHER_WARNING);
                    }
                }
            }
        }
    }

    private void addSource(Map<String, Map<HazardType, Set<String>>> byFacility, String facilityId,
            HazardType hazard, String source) {
        byFacility.computeIfAbsent(facilityId, k -> new EnumMap<>(HazardType.class))
            .computeIfAbsent(hazard, k -> new LinkedHashSet<>())
            .add(source);
    }

    private LocalDateTime parseTmFc(JSONObject row) {
        try {
            // tmFc는 실측상 JSON 숫자로 오는 경우가 있어(예: 202608141100) 문자열로 정규화 후 파싱
            String raw = String.valueOf(row.get("tmFc"));
            return LocalDateTime.parse(raw, TM_FC);
        } catch (Exception e) {
            return null;
        }
    }

    /** dstSeNm(재해구분명) → HazardType 매핑 (terrain-rule-base-spec.md §3-2). */
    private HazardType mapDisasterType(String dstSeNm) {
        if (dstSeNm == null) {
            return null;
        }
        return switch (dstSeNm) {
            case "산사태" -> HazardType.LANDSLIDE;
            case "호우", "홍수" -> HazardType.FLOOD;
            default -> null;
        };
    }

    /**
     * 기상특보 현상명 → HazardType. 산사태는 KMA 특보 현상이 아니라(산림청 소관) 여기 없음 -
     * 재난문자만이 LANDSLIDE의 지역신호 출처다.
     */
    private HazardType mapWarningPhenomenon(String phenomenon) {
        return "호우".equals(phenomenon) ? HazardType.FLOOD : null;
    }

    private AlertResult build(String facilityId, HazardType hazard, VulnerabilityGrade vuln,
            WeatherTrigger trigger, Set<String> regionSources, LocalDateTime now) {
        AlertLevel level = HazardAlertMatrix.evaluate(vuln, trigger, !regionSources.isEmpty());
        return new AlertResult(facilityId, hazard, vuln, trigger, !regionSources.isEmpty(), regionSources, level, now);
    }

    private String toJson(List<AlertResult> results) {
        JSONArray arr = new JSONArray();
        for (AlertResult r : results) {
            JSONObject o = new JSONObject();
            o.put("facilityId", r.facilityId());
            o.put("hazardType", r.hazardType().name());
            o.put("vulnerability", r.vulnerability().name());
            o.put("weatherTrigger", r.weatherTrigger().name());
            o.put("regionTriggered", r.regionTriggered());
            o.put("regionSources", new JSONArray(r.regionSources()));
            o.put("level", r.level().name());
            o.put("levelLabel", r.level().label());
            o.put("evaluatedAt", r.evaluatedAt().toString());
            arr.put(o);
        }
        return arr.toString();
    }
}
