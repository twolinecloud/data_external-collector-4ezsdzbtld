package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * {@code kcais.tb_ext_facility_review_queue} - {@code FacilitySyncService}가
 * {@code tb_dim_instt} 대조로 찾아낸 시설 목록 변경분(신규/제외)을 쌓아두는 사람 검토 큐.
 *
 * <p>NEW 항목은 등록 시점에 {@code VWorldGeocoder}로 자동 지오코딩을 시도해서
 * {@code proposed_*} 컬럼을 채운다(2026-08-24, Phase B) - 성공하면 좌표/시도/시군구/격자까지
 * 채워지고, 실패(주소 미매칭 등)하면 {@code geocode_status}만 NOT_FOUND/FAILED로 남고
 * {@code proposed_*}는 비어있다. 그 경우 사람이 직접 좌표를 조사해서
 * {@code approve(reviewId, lat, lon)}에 수동으로 넘겨야 한다.</p>
 */
@Mapper
public interface FacilityReviewQueueMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_facility_review_queue
            (review_id, facility_id, facility_nm, change_type, detail, address,
             proposed_lat, proposed_lon, proposed_nx, proposed_ny,
             proposed_sido_nm, proposed_sigungu_nm, geocode_status, geocode_source,
             status, detected_dtm)
        VALUES
            (#{id}, #{facilityId}, #{facilityNm}, #{changeType}, #{detail}, #{address},
             #{proposedLat}, #{proposedLon}, #{proposedNx}, #{proposedNy},
             #{proposedSidoNm}, #{proposedSigunguNm}, #{geocodeStatus}, #{geocodeSource},
             'PENDING', now())
        """)
    void insert(Map<String, Object> p);

    /** 같은 시설·같은 변경유형이 이미 PENDING이면 중복 등록 안 함(매일 도는 sync라 필요). */
    @Select("""
        SELECT count(*) FROM kcais.tb_ext_facility_review_queue
        WHERE facility_id = #{facilityId} AND change_type = #{changeType} AND status = 'PENDING'
        """)
    int countPending(@Param("facilityId") String facilityId, @Param("changeType") String changeType);

    @Select("""
        SELECT review_id AS "reviewId", facility_id AS "facilityId", facility_nm AS "facilityNm",
               change_type AS "changeType", detail, address,
               proposed_lat AS "proposedLat", proposed_lon AS "proposedLon",
               proposed_nx AS "proposedNx", proposed_ny AS "proposedNy",
               proposed_sido_nm AS "proposedSidoNm", proposed_sigungu_nm AS "proposedSigunguNm",
               geocode_status AS "geocodeStatus", geocode_source AS "geocodeSource",
               status, detected_dtm AS "detectedDtm", resolved_dtm AS "resolvedDtm"
        FROM kcais.tb_ext_facility_review_queue
        WHERE status = 'PENDING'
        ORDER BY detected_dtm
        """)
    List<Map<String, Object>> selectPending();

    @Select("""
        SELECT review_id AS "reviewId", facility_id AS "facilityId", facility_nm AS "facilityNm",
               change_type AS "changeType", detail, address,
               proposed_lat AS "proposedLat", proposed_lon AS "proposedLon",
               proposed_nx AS "proposedNx", proposed_ny AS "proposedNy",
               proposed_sido_nm AS "proposedSidoNm", proposed_sigungu_nm AS "proposedSigunguNm",
               geocode_status AS "geocodeStatus", geocode_source AS "geocodeSource",
               status, detected_dtm AS "detectedDtm", resolved_dtm AS "resolvedDtm"
        FROM kcais.tb_ext_facility_review_queue
        WHERE review_id = #{reviewId}
        """)
    Map<String, Object> selectById(@Param("reviewId") String reviewId);

    @Update("""
        UPDATE kcais.tb_ext_facility_review_queue
        SET status = #{status}, resolved_dtm = now()
        WHERE review_id = #{reviewId}
        """)
    void updateStatus(@Param("reviewId") String reviewId, @Param("status") String status);
}
