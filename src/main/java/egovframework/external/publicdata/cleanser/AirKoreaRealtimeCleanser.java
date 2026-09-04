package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.AirKoreaStationFacility;
import egovframework.external.publicdata.collector.AirKoreaStationFacilityLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 에어코리아 시도별 실시간 대기오염정보(airkorea-realtime-measure) 정제기. 황사 유도의 데이터
 * 원본을 만든다 - 여기서 받는 값은 PM10(미세먼지)이지 황사 자체가 아니다({@link
 * egovframework.external.publicdata.collector.AirKoreaRealtimeCollector} 클래스 주석 참고).
 *
 * <p>{@link KmaAsosHourlyCleanser}와 정확히 같은 방향으로 매칭한다 - <b>기관 목록을 순회</b>하며
 * 각 기관의 최근접 측정소 행을 원본에서 찾는다(원본 → 기관을 찾는 게 아니라 기관 → 원본을 찾는
 * 역방향). 이게 중요한 이유: 서울지방교정청과 서울구치소처럼 <b>서로 다른 두 기관이 같은 최근접
 * 측정소를 공유</b>할 수 있다(실측 - 둘 다 "별양동"). {@code Map&lt;stationName, facility&gt;}로
 * 반대 방향 매핑을 만들면 나중 기관이 앞 기관을 덮어써 하나가 조용히 사라진다 - 처음엔 이렇게
 * 짰다가 테스트로 잡았다(2026-09-04). 기관 목록을 순회하면 이 문제가 원천적으로 없다 - 같은
 * 측정소를 여러 기관이 참조해도 각자 자기 행을 만든다.</p>
 *
 * <p>매칭 키는 ASOS의 지점번호(stnId)와 달리 <b>측정소명(stationName)</b>이다. 원본 응답에
 * 측정소 고유번호가 없기 때문이다({@link AirKoreaStationFacility} 클래스 주석 참고, 전국
 * 673개 이름 중복 없음을 실측 확인).</p>
 *
 * <p>ASOS와 마찬가지로 <b>판정 기준값은 아직 넣지 않는다(2026-09-04)</b>. PM10 농도를 원본
 * 그대로 넘기고, 몇 ㎍/㎥부터 황사로 볼지는 기획 확정 후 조회 단(백엔드)에서 계산한다.</p>
 */
@Component
public class AirKoreaRealtimeCleanser implements PublicDataCleanser {

    private static final Logger logger = LogManager.getLogger(AirKoreaRealtimeCleanser.class);

    /** 실측(2026-09-03) 확인된 필드 전체. */
    private static final Set<String> RAW_ITEM_FIELDS = Set.of(
        "stationName", "sidoName", "dataTime",
        "pm10Value", "pm10Grade", "pm10Flag", "pm25Value", "pm25Grade", "pm25Flag",
        "khaiValue", "khaiGrade",
        "so2Value", "so2Grade", "so2Flag", "coValue", "coGrade", "coFlag",
        "o3Value", "o3Grade", "o3Flag", "no2Value", "no2Grade", "no2Flag");

    private final List<AirKoreaStationFacility> mappings;

    public AirKoreaRealtimeCleanser(AirKoreaStationFacilityLoader mappingLoader) {
        this.mappings = mappingLoader.all();
    }

    @Override
    public boolean supports(String operationKey) {
        return "airkorea-realtime-measure".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(new StructureProbe("raw-item", RAW_ITEM_FIELDS, RAW_ITEM_FIELDS, StructureProbeSupport::unionKeys));
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            Map<String, JSONObject> byStationName = new HashMap<>();
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                byStationName.put(item.getString("stationName"), item);
            }

            JSONArray result = new JSONArray();
            for (AirKoreaStationFacility mapping : mappings) {
                JSONObject station = byStationName.get(mapping.stationName());
                if (station == null) {
                    // 매핑된 측정소가 이번 응답에 없음 - 결측(pm10Value="-")과는 다르다. 측정소
                    // 자체가 안 왔다는 뜻이라 이 기관은 이번 시각을 건너뛴다(배치 전체 실패는 과함).
                    logger.warn("[CLEANSE] facilityId={} 최근접 측정소({})가 이번 응답에 없음 - 건너뜀",
                        mapping.facilityId(), mapping.stationName());
                    continue;
                }
                result.put(toRow(mapping, station));
            }
            return result.toString();
        } catch (Exception e) {
            throw new CleanseException("공공데이터포털 (한국환경공단 에어코리아)", "시도별 실시간 대기오염정보",
                "정제 실패: " + e.getMessage(), e);
        }
    }

    private JSONObject toRow(AirKoreaStationFacility mapping, JSONObject station) {
        JSONObject row = new JSONObject(station.toString());
        row.put("facilityId", mapping.facilityId());
        row.put("stationDistanceKm", mapping.distanceKm());
        return row;
    }
}
