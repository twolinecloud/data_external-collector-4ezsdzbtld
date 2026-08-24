package egovframework.external.service;

import egovframework.external.model.FacilitySyncResult;
import egovframework.external.publicdata.collector.GeocodeResult;
import egovframework.external.publicdata.collector.KmaGridConverter;
import egovframework.external.publicdata.collector.VWorldGeocoder;
import egovframework.external.publicdata.loader.mapper.FacilityReviewQueueMapper;
import egovframework.external.publicdata.loader.mapper.InstitutionDimMapper;
import egovframework.external.publicdata.loader.mapper.WeatherFacilityMapper;
import egovframework.external.utility.PipelineLogUtils;
import egovframework.external.utility.Ulid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 교정기관 목록 자동 동기화(2026-08-24) - {@code tb_dim_instt}(대시보드 관리 기관 마스터)와
 * 우리 {@code tb_ext_weather_facility}를 대조해서 변경분을 {@code tb_ext_facility_review_queue}
 * 에 쌓는다(Phase A). 신규 시설은 등록 즉시 {@link VWorldGeocoder}로 자동 지오코딩까지
 * 시도한다(Phase B) - 성공하면 좌표/격자까지 채운 채로, 실패하면 상태만 남긴 채로 큐에 쌓여
 * 사람이 {@link #approve}로 확정하거나 {@link #reject}로 무시한다.
 *
 * <p>{@link #approve}는 admin-db {@code tb_ext_weather_facility}에 즉시 쓴다 -
 * {@code public-data.facility.master-source=db}면(Phase C, 2026-08-24 완료) 이걸로 충분히
 * 실제 수집이 다음 스케줄 틱부터 시작된다. {@code csv}(기본값)면 여전히
 * {@code kma-facility-locations.csv}도 사람이 반영해야 한다.</p>
 *
 * <p><b>{@code public-data.facility-sync.enabled=true}일 때만 빈으로 등록됨(2026-08-24 수정)</b> -
 * 생성자에서 admin-db 매퍼 3개를 직접 요구하는데, admin-db 관련 플래그가 전부 기본값(꺼짐)이면
 * 그 매퍼들이 존재하지 않아 부팅이 실패하는 잠재 버그가 있었음(실측 - {@code PublicDataPurgeService}
 * 클래스 주석에 상세 경위 기록) - 조건부 빈으로 바꿔 해결. {@code FacilitySyncScheduler}/
 * {@code FacilitySyncController}도 동일 조건 필요(같이 수정).</p>
 */
@Service
@ConditionalOnProperty(prefix = "public-data.facility-sync", name = "enabled", havingValue = "true")
public class FacilitySyncService {

    private static final Logger logger = LogManager.getLogger(FacilitySyncService.class);
    private static final String STAGE = "FACILITY_SYNC";
    private static final String SOURCE = "admin-db(tb_dim_instt)";
    private static final String CHANGE_NEW = "NEW";
    private static final String CHANGE_REMOVED = "REMOVED";
    private static final String GEOCODE_SOURCE_VWORLD = "vworld_addr";

    private final InstitutionDimMapper institutionDimMapper;
    private final WeatherFacilityMapper weatherFacilityMapper;
    private final FacilityReviewQueueMapper reviewQueueMapper;
    private final VWorldGeocoder vWorldGeocoder;
    private final boolean enabled;

    public FacilitySyncService(
        InstitutionDimMapper institutionDimMapper,
        WeatherFacilityMapper weatherFacilityMapper,
        FacilityReviewQueueMapper reviewQueueMapper,
        VWorldGeocoder vWorldGeocoder,
        @Value("${public-data.facility-sync.enabled:false}") boolean enabled
    ) {
        this.institutionDimMapper = institutionDimMapper;
        this.weatherFacilityMapper = weatherFacilityMapper;
        this.reviewQueueMapper = reviewQueueMapper;
        this.vWorldGeocoder = vWorldGeocoder;
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

        Map<String, Map<String, Object>> dimRowById = toRowMap(
            institutionDimMapper.selectActiveCorrectionalFacilities(), "corrInsttCd");
        Map<String, String> ourNameById = toNameMap(weatherFacilityMapper.selectAll(), "facilityId", "facilityNm");

        int newCount = 0;
        for (Map.Entry<String, Map<String, Object>> entry : dimRowById.entrySet()) {
            String facilityId = entry.getKey();
            if (ourNameById.containsKey(facilityId)) {
                continue;
            }
            if (reviewQueueMapper.countPending(facilityId, CHANGE_NEW) > 0) {
                continue;
            }
            enqueueNew(entry.getValue());
            newCount++;
        }

        int removedCount = 0;
        for (Map.Entry<String, String> entry : ourNameById.entrySet()) {
            String facilityId = entry.getKey();
            if (dimRowById.containsKey(facilityId)) {
                continue;
            }
            if (reviewQueueMapper.countPending(facilityId, CHANGE_REMOVED) > 0) {
                continue;
            }
            enqueueRemoved(facilityId, entry.getValue());
            removedCount++;
        }

        if (newCount > 0 || removedCount > 0) {
            PipelineLogUtils.info(logger, STAGE, SOURCE, "tb_ext_weather_facility",
                "시설 목록 변경 감지: 신규 " + newCount + "건, 제외검토 " + removedCount + "건");
        }
        return new FacilitySyncResult(newCount, removedCount);
    }

    private void enqueueNew(Map<String, Object> dimRow) {
        String facilityId = (String) dimRow.get("corrInsttCd");
        String facilityNm = (String) dimRow.get("corrInsttNm");
        String address = (String) dimRow.get("dtladr");

        GeocodeResult geocode = address == null || address.isBlank()
            ? GeocodeResult.failed()
            : vWorldGeocoder.geocode(address);

        Map<String, Object> p = new HashMap<>();
        p.put("id", Ulid.generate());
        p.put("facilityId", facilityId);
        p.put("facilityNm", facilityNm);
        p.put("changeType", CHANGE_NEW);
        p.put("address", address);
        p.put("geocodeStatus", geocode.status());
        p.put("geocodeSource", geocode.isSuccess() ? GEOCODE_SOURCE_VWORLD : null);

        if (geocode.isSuccess()) {
            KmaGridConverter.Grid grid = KmaGridConverter.toGrid(geocode.lat(), geocode.lon());
            p.put("proposedLat", geocode.lat());
            p.put("proposedLon", geocode.lon());
            p.put("proposedNx", grid.nx());
            p.put("proposedNy", grid.ny());
            p.put("proposedSidoNm", geocode.sidoNm());
            p.put("proposedSigunguNm", geocode.sigunguNm());
            p.put("detail", "tb_dim_instt에 새로 나타남 - VWorld 자동 지오코딩 성공(" + address
                + "), 좌표 확인 후 approve 필요");
        } else {
            p.put("proposedLat", null);
            p.put("proposedLon", null);
            p.put("proposedNx", null);
            p.put("proposedNy", null);
            p.put("proposedSidoNm", null);
            p.put("proposedSigunguNm", null);
            p.put("detail", "tb_dim_instt에 새로 나타남 - 자동 지오코딩 실패(" + geocode.status()
                + ", 주소=" + address + ") - 좌표를 직접 조사해서 approve 시 넘겨야 함");
        }

        reviewQueueMapper.insert(p);
        PipelineLogUtils.info(logger, STAGE, SOURCE, facilityId,
            "[NEW] " + facilityNm + " - geocode=" + geocode.status());
    }

    private void enqueueRemoved(String facilityId, String facilityNm) {
        Map<String, Object> p = new HashMap<>();
        p.put("id", Ulid.generate());
        p.put("facilityId", facilityId);
        p.put("facilityNm", facilityNm);
        p.put("changeType", CHANGE_REMOVED);
        p.put("address", null);
        p.put("geocodeStatus", null);
        p.put("geocodeSource", null);
        p.put("proposedLat", null);
        p.put("proposedLon", null);
        p.put("proposedNx", null);
        p.put("proposedNy", null);
        p.put("proposedSidoNm", null);
        p.put("proposedSigunguNm", null);
        p.put("detail", "tb_dim_instt에서 빠지거나 비활성화(use_yn=N)됨 - 목록 제외 검토 필요");
        reviewQueueMapper.insert(p);
        PipelineLogUtils.info(logger, STAGE, SOURCE, facilityId, "[REMOVED] " + facilityNm);
    }

    /**
     * 검토 큐 항목을 확정 - {@code tb_ext_weather_facility}에 즉시 upsert한다(CSV/컬렉터
     * 반영은 별도, 클래스 주석 참고). 자동 지오코딩이 실패했던 항목은 {@code latOverride}/
     * {@code lonOverride}를 사람이 직접 넘겨야 한다(둘 다 null이면서 자동 제안값도 없으면 예외).
     *
     * @param latOverride/lonOverride 사람이 직접 조사한 좌표 - null이면 자동 지오코딩 제안값을 씀
     * @throws IllegalArgumentException 큐 항목이 없거나 좌표를 확정할 수 없는 경우
     */
    public void approve(String reviewId, Double latOverride, Double lonOverride) {
        Map<String, Object> row = reviewQueueMapper.selectById(reviewId);
        if (row == null) {
            throw new IllegalArgumentException("검토 큐 항목 없음: " + reviewId);
        }

        Double lat = latOverride != null ? latOverride : toDouble(row.get("proposedLat"));
        Double lon = lonOverride != null ? lonOverride : toDouble(row.get("proposedLon"));
        if (lat == null || lon == null) {
            throw new IllegalArgumentException(
                "좌표 확정 불가(reviewId=" + reviewId + ") - 자동 지오코딩 실패한 건이라 lat/lon을 직접 넘겨야 함");
        }

        KmaGridConverter.Grid grid = KmaGridConverter.toGrid(lat, lon);
        String facilityId = (String) row.get("facilityId");
        String facilityNm = (String) row.get("facilityNm");
        String sidoNm = latOverride != null ? null : (String) row.get("proposedSidoNm");
        String sigunguNm = latOverride != null ? null : (String) row.get("proposedSigunguNm");

        weatherFacilityMapper.upsert(facilityId, facilityNm, lat, lon, sidoNm, sigunguNm, grid.nx(), grid.ny());
        reviewQueueMapper.updateStatus(reviewId, "RESOLVED");
        PipelineLogUtils.info(logger, STAGE, SOURCE, facilityId,
            "승인 - tb_ext_weather_facility 반영 완료(nx=" + grid.nx() + ", ny=" + grid.ny()
                + ") - CSV/컬렉터 반영은 별도 수동 작업 필요");
    }

    /** 검토 큐 항목을 무시 처리(오탐/조치 불필요 등) - DB 반영 없음. */
    public void reject(String reviewId) {
        reviewQueueMapper.updateStatus(reviewId, "IGNORED");
    }

    /** 현재 PENDING 상태인 검토 큐 전체(오래된 순) - 수동 조회 API용. */
    public List<Map<String, Object>> pendingQueue() {
        return reviewQueueMapper.selectPending();
    }

    private static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private static Map<String, String> toNameMap(List<Map<String, Object>> rows, String idKey, String nameKey) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get(idKey), (String) row.get(nameKey));
        }
        return result;
    }

    private static Map<String, Map<String, Object>> toRowMap(List<Map<String, Object>> rows, String idKey) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get(idKey), row);
        }
        return result;
    }
}
