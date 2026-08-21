package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
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
 * 기상특보목록(getWthrWrnList) 정제기. 응답 자체가 이미 "특보 발표문 1건 = 1행"인
 * 넓은 형태라서 카테고리 피벗은 필요 없지만, {@code stnId}(지점코드)는 시도 단위
 * 관할구역이라 그 자체로는 어느 교정기관 얘기인지 알 수 없다.
 *
 * <p>{@link DisasterMsgCleanser}와 동일한 패턴 - {@code stnId}가 관할하는 시도에 속한
 * 교정기관마다 행을 하나씩 복제해서 {@code facilityId}를 채운다({@code KmaWarningStation.covers()},
 * 2026-08-21 추가 - admin-db 테이블에 facility_id가 없어 시설별 매칭이 안 되던 문제 해결).
 * {@code stnId=108}(전국)이면 59개소 전부에 매칭되므로 특보 1건이 최대 59행으로 늘어날 수
 * 있음 - 재난문자와 마찬가지로 조인 없이 "이 시설에 해당하는 특보 목록"을 바로 조회하기
 * 위한 의도적 비정규화.</p>
 */
@Component
public class KmaWeatherWarningListCleanser implements PublicDataCleanser {

    private static final Logger logger = LogManager.getLogger(KmaWeatherWarningListCleanser.class);

    /** 실 서비스키 실측(2026-08-12, 24건 전량) 확인된 필드 전체 - 그 밖의 필드는 없었음. */
    private static final Set<String> RAW_ITEM_FIELDS = Set.of("stnId", "title", "tmFc", "tmSeq");

    private final List<KmaWarningStation> stations;
    private final List<FacilitySido> facilities;

    public KmaWeatherWarningListCleanser(KmaWarningStationLoader stationLoader, FacilitySidoLoader facilitySidoLoader) {
        this.stations = stationLoader.all();
        this.facilities = facilitySidoLoader.all();
    }

    @Override
    public boolean supports(String operationKey) {
        return "kma-weather-warning-list".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(new StructureProbe("raw-item", RAW_ITEM_FIELDS, RAW_ITEM_FIELDS, StructureProbeSupport::unionKeys));
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

        for (FacilitySido facility : facilities) {
            if (!station.get().covers(facility.sido())) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("stnId", stnId);
            row.put("title", item.getString("title"));
            row.put("tmFc", item.get("tmFc"));
            row.put("tmSeq", item.get("tmSeq"));
            row.put("facilityId", facility.facilityId());
            result.put(row);
        }
    }
}
