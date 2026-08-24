package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/** {@code kcais.tb_ext_weather_ultra_fcst} (초단기예보조회) - 멱등키 {@code (facility_id, base_dtm, fcst_dtm)}. */
@Mapper
public interface WeatherUltraFcstMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_weather_ultra_fcst
            (weather_ultra_fcst_id, facility_id, nx, ny, base_dtm, fcst_dtm,
             t1h, rn1, sky, uuu, vvv, reh, pty, pop, lgt, vec, wsd,
             operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{facilityId}, #{nx}, #{ny}, #{baseDtm}, #{fcstDtm},
             #{t1h}, #{rn1}, #{sky}, #{uuu}, #{vvv}, #{reh}, #{pty}, #{pop}, #{lgt}, #{vec}, #{wsd},
             #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (facility_id, base_dtm, fcst_dtm) DO UPDATE SET
            nx = EXCLUDED.nx, ny = EXCLUDED.ny,
            t1h = EXCLUDED.t1h, rn1 = EXCLUDED.rn1, sky = EXCLUDED.sky, uuu = EXCLUDED.uuu,
            vvv = EXCLUDED.vvv, reh = EXCLUDED.reh, pty = EXCLUDED.pty, pop = EXCLUDED.pop,
            lgt = EXCLUDED.lgt, vec = EXCLUDED.vec, wsd = EXCLUDED.wsd,
            collect_dtm = EXCLUDED.collect_dtm, cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);

    // reg_dtm(적재 시각) 기준 보존정책 삭제 - WeatherNcstMapper#deleteOlderThan 주석 참고.
    @Delete("DELETE FROM kcais.tb_ext_weather_ultra_fcst WHERE reg_dtm < now() - make_interval(days => #{retentionDays})")
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
