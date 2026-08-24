package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/** {@code kcais.tb_ext_weather_ncst} (초단기실황조회) - 멱등키 {@code (facility_id, base_dtm)}. */
@Mapper
public interface WeatherNcstMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_weather_ncst
            (weather_ncst_id, facility_id, nx, ny, base_dtm, t1h, rn1, reh, pty, vec, wsd, uuu, vvv, sens_temp,
             operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{facilityId}, #{nx}, #{ny}, #{baseDtm}, #{t1h}, #{rn1}, #{reh}, #{pty}, #{vec}, #{wsd}, #{uuu}, #{vvv}, #{sensTemp},
             #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (facility_id, base_dtm) DO UPDATE SET
            nx = EXCLUDED.nx, ny = EXCLUDED.ny,
            t1h = EXCLUDED.t1h, rn1 = EXCLUDED.rn1, reh = EXCLUDED.reh, pty = EXCLUDED.pty,
            vec = EXCLUDED.vec, wsd = EXCLUDED.wsd, uuu = EXCLUDED.uuu, vvv = EXCLUDED.vvv, sens_temp = EXCLUDED.sens_temp,
            collect_dtm = EXCLUDED.collect_dtm, cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);

    // reg_dtm(적재 시각) 기준 - "실행일시로부터 N일" 보존정책(task-spec 11번 항목)이라
    // 관측/예보 대상 시각(base_dtm)이 아니라 우리가 이 행을 적재한 시각을 기준으로 삼는다.
    // now()를 애플리케이션(Java) 쪽에서 계산하지 않고 SQL에서 직접 쓰는 이유 - Main.java의
    // 전역 UTC 기본값 때문에 LocalDateTime.now()가 KST와 어긋났던 사고(2026-08-21)가 있어서,
    // reg_dtm을 채운 것과 동일한 now()를 그대로 재사용해 타임존 불일치 여지 자체를 없앤다.
    @Delete("DELETE FROM kcais.tb_ext_weather_ncst WHERE reg_dtm < now() - make_interval(days => #{retentionDays})")
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
