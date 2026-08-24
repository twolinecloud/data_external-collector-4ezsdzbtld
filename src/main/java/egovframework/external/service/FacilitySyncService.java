package egovframework.external.service;

import egovframework.external.model.FacilitySyncResult;
import egovframework.external.publicdata.loader.mapper.FacilityReviewQueueMapper;
import egovframework.external.publicdata.loader.mapper.InstitutionDimMapper;
import egovframework.external.publicdata.loader.mapper.WeatherFacilityMapper;
import egovframework.external.utility.PipelineLogUtils;
import egovframework.external.utility.Ulid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 교정기관 목록 자동 동기화(Phase A, 2026-08-24) - {@code tb_dim_instt}(대시보드 관리 기관
 * 마스터)와 우리 {@code tb_ext_weather_facility}를 대조해서 변경분을
 * {@code tb_ext_facility_review_queue}에 쌓는다.
 *
 * <p><b>아직 자동으로 시설을 추가/제외하지 않는다</b> - 신규 시설은 위경도/격자좌표를 아직
 * 모르므로(지오코딩 자동화는 별도 단계, 미구현) 사람이 검토 큐를 보고 직접
 * {@code kma-facility-locations.csv}에 추가해야 한다. 제외 후보도 마찬가지로 사람이 확인 후
 * CSV에서 빼는 걸 전제로 한다 - 이 서비스는 "탐지 + 알림"까지만 담당.</p>
 */
@Service
public class FacilitySyncService {

    private static final Logger logger = LogManager.getLogger(FacilitySyncService.class);
    private static final String STAGE = "FACILITY_SYNC";
    private static final String SOURCE = "admin-db(tb_dim_instt)";
    private static final String CHANGE_NEW = "NEW";
    private static final String CHANGE_REMOVED = "REMOVED";

    private final InstitutionDimMapper institutionDimMapper;
    private final WeatherFacilityMapper weatherFacilityMapper;
    private final FacilityReviewQueueMapper reviewQueueMapper;
    private final boolean enabled;

    public FacilitySyncService(
        InstitutionDimMapper institutionDimMapper,
        WeatherFacilityMapper weatherFacilityMapper,
        FacilityReviewQueueMapper reviewQueueMapper,
        @Value("${public-data.facility-sync.enabled:false}") boolean enabled
    ) {
        this.institutionDimMapper = institutionDimMapper;
        this.weatherFacilityMapper = weatherFacilityMapper;
        this.reviewQueueMapper = reviewQueueMapper;
        this.enabled = enabled;
    }

    /**
     * @return 이번 실행에서 새로 큐에 등록한 건수(신규/제외검토) - 이미 PENDING인 항목은
     *         중복 등록하지 않으므로 집계에서 빠진다. enabled=false면 전부 0.
     */
    public FacilitySyncResult sync() {
        if (!enabled) {
            return new FacilitySyncResult(0, 0);
        }

        Map<String, String> dimNameById = toNameMap(institutionDimMapper.selectActiveCorrectionalFacilities(),
            "corrInsttCd", "corrInsttNm");
        Map<String, String> ourNameById = toNameMap(weatherFacilityMapper.selectAll(), "facilityId", "facilityNm");

        int newCount = 0;
        for (Map.Entry<String, String> entry : dimNameById.entrySet()) {
            String facilityId = entry.getKey();
            if (ourNameById.containsKey(facilityId)) {
                continue;
            }
            if (enqueueIfNotPending(facilityId, entry.getValue(), CHANGE_NEW,
                "tb_dim_instt에 새로 나타남 - 좌표 미보유, 지오코딩 후 kma-facility-locations.csv에 등록 필요")) {
                newCount++;
            }
        }

        int removedCount = 0;
        for (Map.Entry<String, String> entry : ourNameById.entrySet()) {
            String facilityId = entry.getKey();
            if (dimNameById.containsKey(facilityId)) {
                continue;
            }
            if (enqueueIfNotPending(facilityId, entry.getValue(), CHANGE_REMOVED,
                "tb_dim_instt에서 빠지거나 비활성화(use_yn=N)됨 - 목록 제외 검토 필요")) {
                removedCount++;
            }
        }

        if (newCount > 0 || removedCount > 0) {
            PipelineLogUtils.info(logger, STAGE, SOURCE, "tb_ext_weather_facility",
                "시설 목록 변경 감지: 신규 " + newCount + "건, 제외검토 " + removedCount + "건");
        }
        return new FacilitySyncResult(newCount, removedCount);
    }

    private boolean enqueueIfNotPending(String facilityId, String facilityNm, String changeType, String detail) {
        if (reviewQueueMapper.countPending(facilityId, changeType) > 0) {
            return false;
        }
        Map<String, Object> p = new HashMap<>();
        p.put("id", Ulid.generate());
        p.put("facilityId", facilityId);
        p.put("facilityNm", facilityNm);
        p.put("changeType", changeType);
        p.put("detail", detail);
        reviewQueueMapper.insert(p);
        PipelineLogUtils.info(logger, STAGE, SOURCE, facilityId,
            "[" + changeType + "] " + facilityNm + " - " + detail);
        return true;
    }

    /** 현재 PENDING 상태인 검토 큐 전체(오래된 순) - 수동 조회 API용. */
    public List<Map<String, Object>> pendingQueue() {
        return reviewQueueMapper.selectPending();
    }

    private static Map<String, String> toNameMap(List<Map<String, Object>> rows, String idKey, String nameKey) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get(idKey), (String) row.get(nameKey));
        }
        return result;
    }
}
