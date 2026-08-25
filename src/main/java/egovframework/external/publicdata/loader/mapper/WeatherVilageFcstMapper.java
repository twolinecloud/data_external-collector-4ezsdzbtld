package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/** {@code kcais.tb_ext_weather_vilage_fcst} (단기예보조회) - 멱등키 {@code (facility_id, base_dtm, fcst_dtm)}. */
@Mapper
public interface WeatherVilageFcstMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_weather_vilage_fcst
            (weather_vilage_fcst_id, facility_id, nx, ny, base_dtm, fcst_dtm,
             pop, pty, pcp, reh, sno, sky, tmp, tmn, tmx, uuu, vec, vvv, wav, wsd, sens_temp,
             operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{facilityId}, #{nx}, #{ny}, #{baseDtm}, #{fcstDtm},
             #{pop}, #{pty}, #{pcp}, #{reh}, #{sno}, #{sky}, #{tmp}, #{tmn}, #{tmx}, #{uuu}, #{vec}, #{vvv}, #{wav}, #{wsd}, #{sensTemp},
             #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (facility_id, base_dtm, fcst_dtm) DO UPDATE SET
            nx = EXCLUDED.nx, ny = EXCLUDED.ny,
            pop = EXCLUDED.pop, pty = EXCLUDED.pty, pcp = EXCLUDED.pcp, reh = EXCLUDED.reh,
            sno = EXCLUDED.sno, sky = EXCLUDED.sky, tmp = EXCLUDED.tmp, tmn = EXCLUDED.tmn,
            tmx = EXCLUDED.tmx, uuu = EXCLUDED.uuu, vec = EXCLUDED.vec, vvv = EXCLUDED.vvv,
            wav = EXCLUDED.wav, wsd = EXCLUDED.wsd, sens_temp = EXCLUDED.sens_temp,
            collect_dtm = EXCLUDED.collect_dtm, cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);

    // reg_dtm(적재 시각) 기준 보존정책 삭제 - WeatherNcstMapper#deleteOlderThan 주석 참고.
    @Delete("DELETE FROM kcais.tb_ext_weather_vilage_fcst WHERE reg_dtm < now() - make_interval(days => #{retentionDays})")
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
