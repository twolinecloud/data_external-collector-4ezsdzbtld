package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.FacilityMasterRecord;
import egovframework.external.publicdata.collector.FacilityMasterSource;
import egovframework.external.publicdata.collector.FacilitySido;
import egovframework.external.publicdata.collector.FacilitySidoLoader;
import egovframework.external.publicdata.collector.KmaWarningStation;
import egovframework.external.publicdata.collector.KmaWarningStationLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 기상특보목록(getWthrWrnList) 정제기.
 *
 * <p>상세 통보문(getWthrWrnMsg)의 발효 구역(t2, t6) 정보를 바탕으로, {@link KmaWarningAreaMatcher}를
 * 통해 실제 해당 교정기관의 행정구역(시·도 및 시·군·구)에 해당하는 특보만 선별하여 매칭 적재한다.<br>
 * 해상 전용 특보(풍랑주의보/경보 등)는 육상 교정시설에 매칭되지 않으며, stnId=108(전국) 특보라도
 * 본문 구역에 시설 지명이 없으면 매칭에서 제외된다.</p>
 */
@Component
public class KmaWeatherWarningListCleanser implements PublicDataCleanser {

    private static final Logger logger = LogManager.getLogger(KmaWeatherWarningListCleanser.class);

    /** 필수 필드 4개 (레거시 및 신규 공통) */
    private static final Set<String> REQUIRED_FIELDS = Set.of("stnId", "title", "tmFc", "tmSeq");
    /** 통보문(getWthrWrnMsg) 연동으로 추가될 수 있는 선택 필드 */
    private static final Set<String> ALL_KNOWN_FIELDS = Set.of(
        "stnId", "title", "tmFc", "tmSeq", "t1", "t2", "t3", "t4", "t5", "t6", "t7", "warFc"
    );

    private final List<KmaWarningStation> stations;
    private final FacilityMasterSource facilityMasterSource;

    public KmaWeatherWarningListCleanser(KmaWarningStationLoader stationLoader, FacilityMasterSource facilityMasterSource) {
        this.stations = stationLoader.all();
        this.facilityMasterSource = facilityMasterSource;
    }

    /** 하위 호환용 생성자 (기존 테스트 등 대응) */
    public KmaWeatherWarningListCleanser(KmaWarningStationLoader stationLoader, FacilitySidoLoader facilitySidoLoader) {
        this.stations = stationLoader.all();
        // FacilitySido 목록을 FacilityMasterRecord로 어댑팅
        this.facilityMasterSource = () -> facilitySidoLoader.all().stream()
            .map(s -> new FacilityMasterRecord(s.facilityId(), "", s.sido(), "", "", ""))
            .toList();
    }

    @Override
    public boolean supports(String operationKey) {
        return "kma-weather-warning-list".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(new StructureProbe("raw-item", ALL_KNOWN_FIELDS, REQUIRED_FIELDS, StructureProbeSupport::unionKeys));
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            JSONArray result = new JSONArray();
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                if (!item.has("title")) {
                    throw new IllegalStateException("특보 항목에 title 필드 없음: " + item);
                }
                appendMatches(item, result);
            }
            return result.toString();
        } catch (Exception e) {
            throw new CleanseException("공공데이터포털 (기상청 기상특보)", "기상특보목록조회", "정제 실패: " + e.getMessage(), e);
        }
    }

    private void appendMatches(JSONObject item, JSONArray result) {
        String stnId = item.getString("stnId");
        Optional<KmaWarningStation> station = stations.stream()
            .filter(s -> s.stnId().equals(stnId))
            .findFirst();
        if (station.isEmpty()) {
            // 확정된 10개 지점코드 밖의 값 - 기상청이 표를 바꿨거나 우리가 놓친 코드일 수 있음
            logger.warn("[CLEANSE] 알 수 없는 기상특보 지점코드(stnId={}) - 시설 매칭 없이 건너뜀: {}", stnId, item);
            return;
        }

        String title = item.optString("title", "");
        String t1 = item.optString("t1", null);
        String t2 = item.optString("t2", null);
        String t6 = item.optString("t6", null);

        for (FacilityMasterRecord facility : facilityMasterSource.current()) {
            // 1. 지점 관할 구역 필터링 (전국이 아닌 지방청인 경우 관할 시도에 속해야 함)
            if (!station.get().covers(facility.sido())) {
                continue;
            }

            // 2. 통보문 상세 구역 및 해상특보 정밀 매칭
            if (!KmaWarningAreaMatcher.matches(title, t1, t2, t6, facility)) {
                continue;
            }

            JSONObject row = new JSONObject();
            row.put("stnId", stnId);
            row.put("title", item.getString("title"));
            row.put("tmFc", item.get("tmFc"));
            row.put("tmSeq", item.get("tmSeq"));
            row.put("facilityId", facility.facilityId());

            copyIfPresent(item, row, "t1");
            copyIfPresent(item, row, "t2");
            copyIfPresent(item, row, "t3");
            copyIfPresent(item, row, "t4");
            copyIfPresent(item, row, "t5");
            copyIfPresent(item, row, "t6");
            copyIfPresent(item, row, "t7");
            copyIfPresent(item, row, "warFc");

            result.put(row);
        }
    }

    private void copyIfPresent(JSONObject src, JSONObject dest, String field) {
        if (src.has(field) && !src.isNull(field)) {
            dest.put(field, src.get(field));
        }
    }
}
