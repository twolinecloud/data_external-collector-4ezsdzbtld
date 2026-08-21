package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/** {@code kcais.tb_ext_weather_ncst} (초단기실황조회) - 멱등키 {@code (facility_id, base_dtm)}. */
@Mapper
public interface WeatherNcstMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_weather_ncst
            (weather_ncst_id, facility_id, nx, ny, base_dtm, t1h, rn1, reh, pty, vec, wsd, uuu, vvv,
             operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{facilityId}, #{nx}, #{ny}, #{baseDtm}, #{t1h}, #{rn1}, #{reh}, #{pty}, #{vec}, #{wsd}, #{uuu}, #{vvv},
             #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (facility_id, base_dtm) DO UPDATE SET
            nx = EXCLUDED.nx, ny = EXCLUDED.ny,
            t1h = EXCLUDED.t1h, rn1 = EXCLUDED.rn1, reh = EXCLUDED.reh, pty = EXCLUDED.pty,
            vec = EXCLUDED.vec, wsd = EXCLUDED.wsd, uuu = EXCLUDED.uuu, vvv = EXCLUDED.vvv,
            collect_dtm = EXCLUDED.collect_dtm, cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);
}
