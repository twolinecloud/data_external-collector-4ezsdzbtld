package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.AsosStationFacility;
import egovframework.external.publicdata.collector.KmaAsosHourlyCollector;
import egovframework.external.publicdata.collector.KmaAsosStationFacilityLoader;
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
 * 지상관측(ASOS) 시간자료(kma-asos-hourly) 정제기. 안개/박무/연무 유도의 데이터 원본을 만든다.
 *
 * <p>수집 응답은 <b>전국 97개 지점을 한 번에 담은 넓은 형태</b>다({@link KmaAsosHourlyCollector} -
 * {@code stn=0}). 여기서는 카테고리 피벗이 아니라 {@link KmaWeatherWarningListCleanser}와 같은
 * "지점코드 → 시설" 매칭을 한다 - 다만 그쪽은 지점 하나가 시도 전체(최대 59개소)를 관할해서
 * 1:N으로 펼치는 반면, 여기는 {@link KmaAsosStationFacilityLoader}가 기관마다 미리 계산해둔
 * <b>최근접 지점 1개</b>로 1:1 조회다(역방향 - 시설 기준으로 지점을 찾음).</p>
 *
 * <p><b>판정 기준값은 아직 넣지 않는다(2026-09-04)</b>. 시정(VS)·습도(HM)를 원본 그대로
 * 넘기고, 안개/박무/연무 라벨링은 기획이 기준값을 확정한 뒤 조회 단(백엔드)에서 계산하도록
 * 권고했다(private-doc 가이드 참고) - 기준이 바뀔 때마다 재적재하지 않아도 되게 하기 위함.
 * 값 타입도 다른 날씨 정제기와 같은 원칙으로 VARCHAR 그대로 둔다(2.0절 근거와 동일 - 원본에
 * {@code -9}/{@code -99.0} 결측 표기가 섞여 있어 숫자로 캐스팅하면 그 행에서 터진다).</p>
 */
@Component
public class KmaAsosHourlyCleanser implements PublicDataCleanser {

    private static final Logger logger = LogManager.getLogger(KmaAsosHourlyCleanser.class);

    /** 구조 드리프트 감지가 수집기와 같은 필드 목록을 본다 - {@link KmaAsosHourlyCollector#FIELD_NAMES} 참고. */
    private static final Set<String> RAW_ITEM_FIELDS = Set.copyOf(KmaAsosHourlyCollector.FIELD_NAMES);

    private final List<AsosStationFacility> mappings;

    public KmaAsosHourlyCleanser(KmaAsosStationFacilityLoader mappingLoader) {
        this.mappings = mappingLoader.all();
    }

    @Override
    public boolean supports(String operationKey) {
        return "kma-asos-hourly".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(new StructureProbe("raw-item", RAW_ITEM_FIELDS, RAW_ITEM_FIELDS, StructureProbeSupport::unionKeys));
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            Map<String, JSONObject> byStnId = new HashMap<>();
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                byStnId.put(item.getString("STN"), item);
            }

            JSONArray result = new JSONArray();
            for (AsosStationFacility mapping : mappings) {
                JSONObject station = byStnId.get(mapping.stnId());
                if (station == null) {
                    // 그 시각 응답에 이 지점이 빠져있는 경우 - 결측(-9)과는 다르다. 관측소 자체가
                    // 안 왔다는 뜻이라 이 기관은 이번 시각을 건너뛴다(배치 전체 실패는 과함).
                    logger.warn("[CLEANSE] facilityId={} 최근접 지점(stnId={})이 이번 응답에 없음 - 건너뜀",
                        mapping.facilityId(), mapping.stnId());
                    continue;
                }
                result.put(toRow(mapping, station));
            }
            return result.toString();
        } catch (Exception e) {
            throw new CleanseException("기상청 API허브 (지상 종관기상관측)", "지상관측 시간자료",
                "정제 실패: " + e.getMessage(), e);
        }
    }

    private JSONObject toRow(AsosStationFacility mapping, JSONObject station) {
        // 46개 필드를 그대로 복사 + facility/매핑 정보를 얹는다(DisasterMsgCleanser의
        // "원본 복사 + facilityId 추가" 패턴과 동일).
        JSONObject row = new JSONObject(station.toString());
        row.put("facilityId", mapping.facilityId());
        row.put("stnDistanceKm", mapping.distanceKm());
        return row;
    }
}
