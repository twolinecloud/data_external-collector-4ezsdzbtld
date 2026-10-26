package egovframework.external.publicdata.collector.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * {@code kcais.tb_ext_law_target} (법령·행정규칙 수집 대상 목록 - 운영 기준 출처, 2026-10-23부터.
 * 컬럼 {@code doc_type_cd}는 LAW | ADMIN_RULE이고 기본키는 {@code (law_id, doc_type_cd)}.
 * 실제 수집된 법령 본문은 이 테이블과 무관하다). 스키마/이관: {@code scripts/migrate-law-target-to-db.sql}.
 */
@Mapper
public interface LawTargetMapper {

    @Select("""
        SELECT law_id AS "lawId", law_nm AS "lawName", mst AS "mst", law_type_cd AS "lawType",
               promulgation_de AS "promulgationDate", effective_de AS "effectiveDate",
               ministry_nm AS "ministry", doc_type_cd AS "docType"
        FROM kcais.tb_ext_law_target
        WHERE active_yn = 'Y'
        ORDER BY law_id
        """)
    List<Map<String, Object>> selectActiveTargets();
}
