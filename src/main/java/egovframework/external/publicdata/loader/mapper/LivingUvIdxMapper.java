package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/** {@code kcais.tb_ext_living_uv_idx}(자외선지수) - 멱등키 {@code (facility_id, base_dtm, fcst_dtm)}. */
@Mapper
public interface LivingUvIdxMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_living_uv_idx
            (living_uv_idx_id, facility_id, area_no, idx_code, base_dtm, fcst_dtm, idx_value,
             operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{facilityId}, #{areaNo}, #{idxCode}, #{baseDtm}, #{fcstDtm}, #{idxValue},
             #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (facility_id, base_dtm, fcst_dtm) DO UPDATE SET
            area_no = EXCLUDED.area_no, idx_code = EXCLUDED.idx_code, idx_value = EXCLUDED.idx_value,
            collect_dtm = EXCLUDED.collect_dtm, cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);

    // reg_dtm(적재 시각) 기준 보존정책 삭제 - WeatherNcstMapper#deleteOlderThan 주석 참고.
    @Delete("DELETE FROM kcais.tb_ext_living_uv_idx WHERE reg_dtm < now() - make_interval(days => #{retentionDays})")
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
