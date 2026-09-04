package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * {@code kcais.tb_ext_air_quality} (에어코리아 실시간 대기오염정보 - 황사 유도용) - 멱등키
 * {@code (facility_id, base_dtm)}. <b>여기 담기는 값은 PM10(미세먼지)이지 황사가 아니다</b>
 * ({@code AirKoreaRealtimeCleanser} 클래스 주석 참고) - 황사 신호는 {@code tb_ext_weather_warning}의
 * 황사주의보/경보가 더 직접적이고, 이 값은 보조 지표로 쓰인다.
 */
@Mapper
public interface AirQualityMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_air_quality
            (air_quality_id, facility_id, station_nm, sido_nm, base_dtm,
             pm10_value, pm10_grade, pm10_flag, pm25_value, pm25_grade, khai_value, khai_grade,
             raw_json, operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{facilityId}, #{stationNm}, #{sidoNm}, #{baseDtm},
             #{pm10Value}, #{pm10Grade}, #{pm10Flag}, #{pm25Value}, #{pm25Grade}, #{khaiValue}, #{khaiGrade},
             #{rawJson}::jsonb, #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (facility_id, base_dtm) DO UPDATE SET
            station_nm = EXCLUDED.station_nm, sido_nm = EXCLUDED.sido_nm,
            pm10_value = EXCLUDED.pm10_value, pm10_grade = EXCLUDED.pm10_grade, pm10_flag = EXCLUDED.pm10_flag,
            pm25_value = EXCLUDED.pm25_value, pm25_grade = EXCLUDED.pm25_grade,
            khai_value = EXCLUDED.khai_value, khai_grade = EXCLUDED.khai_grade,
            raw_json = EXCLUDED.raw_json,
            collect_dtm = EXCLUDED.collect_dtm, cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);

    // reg_dtm(적재 시각) 기준 보존정책 삭제 - WeatherNcstMapper#deleteOlderThan 주석 참고.
    @Delete("DELETE FROM kcais.tb_ext_air_quality WHERE reg_dtm < now() - make_interval(days => #{retentionDays})")
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
