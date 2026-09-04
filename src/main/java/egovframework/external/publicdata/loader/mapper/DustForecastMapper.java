package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * {@code kcais.tb_ext_dust_forecast} (에어코리아 대기질예보통보 - 내일 황사 표시용) - 멱등키
 * {@code (facility_id, fcst_dtm)}. <b>여기 담기는 값은 PM10 예보이지 황사 예보가 아니다</b>
 * ({@code AirKoreaDustForecastCleanser} 클래스 주석 참고).
 *
 * <p><b>{@code base_dtm}은 멱등키에 없다</b> - {@code tb_ext_weather_vilage_fcst}처럼
 * 발표 이력을 다 남기는 설계가 아니라, 같은 예보대상일(fcst_dtm)에 새 발표가 들어오면
 * 이전 발표를 덮어쓰는 "최신 예보 1건만 유지" 설계다.</p>
 */
@Mapper
public interface DustForecastMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_dust_forecast
            (dust_forecast_id, facility_id, inform_region, base_dtm, fcst_dtm, grade, inform_cause,
             raw_json, operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{facilityId}, #{informRegion}, #{baseDtm}, #{fcstDtm}, #{grade}, #{informCause},
             #{rawJson}::jsonb, #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (facility_id, fcst_dtm) DO UPDATE SET
            inform_region = EXCLUDED.inform_region, base_dtm = EXCLUDED.base_dtm,
            grade = EXCLUDED.grade, inform_cause = EXCLUDED.inform_cause,
            raw_json = EXCLUDED.raw_json,
            collect_dtm = EXCLUDED.collect_dtm, cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);

    // reg_dtm(적재 시각) 기준 보존정책 삭제 - WeatherNcstMapper#deleteOlderThan 주석 참고.
    @Delete("DELETE FROM kcais.tb_ext_dust_forecast WHERE reg_dtm < now() - make_interval(days => #{retentionDays})")
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
