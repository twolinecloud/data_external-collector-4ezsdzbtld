package egovframework.external.publicdata.collector.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * {@code kcais.tb_ext_law_target} (법령 수집 대상 목록 - 관리자 콘솔에서 편집 예정, 실제
 * 수집된 법령 본문은 이 테이블과 무관하게 제논DB로 감).
 */
@Mapper
public interface LawTargetMapper {

    @Select("""
        SELECT law_id AS "lawId", law_nm AS "lawName", mst AS "mst", law_type_cd AS "lawType",
               promulgation_de AS "promulgationDate", effective_de AS "effectiveDate",
               ministry_nm AS "ministry"
        FROM kcais.tb_ext_law_target
        WHERE active_yn = 'Y'
        ORDER BY law_id
        """)
    List<Map<String, Object>> selectActiveTargets();
}
