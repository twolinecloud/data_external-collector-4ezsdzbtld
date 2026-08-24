package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * {@code kcais.tb_dim_instt} - 대시보드 팀이 관리하는 기관 마스터(우리가 만든 테이블 아님,
 * 읽기 전용으로만 참조). {@code FacilitySyncService}가 우리 시설 목록(59개소)이 최신인지
 * 대조하는 용도로 씀.
 *
 * <p><b>"교정시설 59개소" 조건 실측 확정(2026-08-24)</b> - 이 테이블엔 법무부/교정본부/
 * 법무연수원 같은 상급·행정기관도 섞여있어(64건 전체) 단순 use_yn 필터만으로는 우리
 * 목록과 안 맞음. {@code upper_corr_instt_cd}(상급기관코드) 계층을 타고 올라가
 * <b>"교정본부(1270045) 산하 전체, 교정본부 자신은 제외"</b>로 재귀 조회하면 기존
 * kma-facility-locations.csv 59개소와 정확히 1:1 일치함(실측 대조 완료 - 법무부/교정본부/
 * 법무연수원(+용인분원)/천안지소(use_yn='N')만 자동으로 빠짐). 지방교정청 4곳(서울/대구/
 * 대전/광주)은 교정본부 바로 산하라 "시설"에 포함됨 - 우리 목록에도 원래 포함돼있던 것과 일치.</p>
 *
 * <p>{@code use_yn}은 'N'으로 명시된 것만 비활성 확정(1건 실측) - 나머지는 전부 공란/NULL이라
 * "명시적으로 Y인 것만" 필터링하면 전부 빠져버림, 그래서 {@code use_yn IS NULL OR use_yn <> 'N'}
 * 로 판단한다(PL 공유 쿼리의 {@code USE_YN != 'N' OR USE_YN IS NULL} 조건과 동일한 패턴).</p>
 */
@Mapper
public interface InstitutionDimMapper {

    @Select("""
        WITH RECURSIVE corr_facility AS (
            SELECT corr_instt_cd, corr_instt_nm, upper_corr_instt_cd, use_yn
            FROM kcais.tb_dim_instt
            WHERE corr_instt_cd = '1270045'
            UNION ALL
            SELECT t.corr_instt_cd, t.corr_instt_nm, t.upper_corr_instt_cd, t.use_yn
            FROM kcais.tb_dim_instt t
            JOIN corr_facility cf ON t.upper_corr_instt_cd = cf.corr_instt_cd
        )
        SELECT corr_instt_cd AS "corrInsttCd", corr_instt_nm AS "corrInsttNm"
        FROM corr_facility
        WHERE corr_instt_cd <> '1270045'
          AND (use_yn IS NULL OR use_yn <> 'N')
        ORDER BY corr_instt_cd
        """)
    List<Map<String, Object>> selectActiveCorrectionalFacilities();
}
