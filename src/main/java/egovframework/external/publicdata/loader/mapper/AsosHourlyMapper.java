package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * {@code kcais.tb_ext_asos_hourly} (지상 종관기상관측 시간자료 - 안개/박무/연무 유도용) -
 * 멱등키 {@code (facility_id, base_dtm)}. {@code vs}(시정)는 <b>10m 단위</b>로 그대로
 * 들어간다(580 = 5.8km) - 캐스팅/환산은 조회 단 몫.
 */
@Mapper
public interface AsosHourlyMapper {

    // raw_json은 JSONB 컬럼 - WeatherWarningMapper와 동일하게 #{rawJson}::jsonb로 명시 캐스팅.
    @Insert("""
        INSERT INTO kcais.tb_ext_asos_hourly
            (asos_hourly_id, facility_id, stn_id, stn_nm, base_dtm, vs, hm, ta, ww, wc, wp, ix,
             raw_json, operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{facilityId}, #{stnId}, #{stnNm}, #{baseDtm}, #{vs}, #{hm}, #{ta}, #{ww}, #{wc}, #{wp}, #{ix},
             #{rawJson}::jsonb, #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (facility_id, base_dtm) DO UPDATE SET
            stn_id = EXCLUDED.stn_id, stn_nm = EXCLUDED.stn_nm,
            vs = EXCLUDED.vs, hm = EXCLUDED.hm, ta = EXCLUDED.ta,
            ww = EXCLUDED.ww, wc = EXCLUDED.wc, wp = EXCLUDED.wp, ix = EXCLUDED.ix,
            raw_json = EXCLUDED.raw_json,
            collect_dtm = EXCLUDED.collect_dtm, cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);

    // reg_dtm(적재 시각) 기준 보존정책 삭제 - WeatherNcstMapper#deleteOlderThan 주석 참고.
    @Delete("DELETE FROM kcais.tb_ext_asos_hourly WHERE reg_dtm < now() - make_interval(days => #{retentionDays})")
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
