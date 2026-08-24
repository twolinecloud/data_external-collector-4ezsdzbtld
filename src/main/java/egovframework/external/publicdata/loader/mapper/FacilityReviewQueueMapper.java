package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * {@code kcais.tb_ext_facility_review_queue} - {@code FacilitySyncService}가
 * {@code tb_dim_instt} 대조로 찾아낸 시설 목록 변경분(신규/제외)을 쌓아두는 사람 검토 큐.
 * 좌표 자동 지오코딩(B단계, 아직 미구현)이 붙기 전까지는, 신규 시설은 좌표가 없어 자동으로
 * CSV에 넣을 수 없으므로 여기 쌓아두고 사람이 직접 확인·추가한다.
 */
@Mapper
public interface FacilityReviewQueueMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_facility_review_queue
            (review_id, facility_id, facility_nm, change_type, detail, status, detected_dtm)
        VALUES
            (#{id}, #{facilityId}, #{facilityNm}, #{changeType}, #{detail}, 'PENDING', now())
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
               change_type AS "changeType", detail, status,
               detected_dtm AS "detectedDtm", resolved_dtm AS "resolvedDtm"
        FROM kcais.tb_ext_facility_review_queue
        WHERE status = 'PENDING'
        ORDER BY detected_dtm
        """)
    List<Map<String, Object>> selectPending();
}
