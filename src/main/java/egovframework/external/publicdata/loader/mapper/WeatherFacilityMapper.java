package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * {@code kcais.tb_ext_weather_facility} (지점/기관 마스터) - {@code FacilityMasterSeeder}가
 * 앱 기동 시 {@code kma-facility-locations.csv} 59개소를 upsert하는 용도로만 씀
 * (수집 파이프라인이 매번 쓰는 테이블이 아니라 기준정보). PK는 자연키(facility_id, 법무부
 * 교정본부 공식 교정기관코드)라 ULID를 안 씀.
 */
@Mapper
public interface WeatherFacilityMapper {

    @Insert("""
        INSERT INTO kcais.tb_ext_weather_facility
            (facility_id, facility_nm, lat, lon, sido_nm, sigungu_nm, nx, ny)
        VALUES
            (#{facilityId}, #{facilityNm}, #{lat}, #{lon}, #{sidoNm}, #{sigunguNm}, #{nx}, #{ny})
        ON CONFLICT (facility_id) DO UPDATE SET
            facility_nm = EXCLUDED.facility_nm,
            lat = EXCLUDED.lat,
            lon = EXCLUDED.lon,
            sido_nm = EXCLUDED.sido_nm,
            sigungu_nm = EXCLUDED.sigungu_nm,
            nx = EXCLUDED.nx,
            ny = EXCLUDED.ny,
            mod_dtm = now()
        """)
    void upsert(@Param("facilityId") String facilityId, @Param("facilityNm") String facilityNm,
                @Param("lat") Double lat, @Param("lon") Double lon,
                @Param("sidoNm") String sidoNm, @Param("sigunguNm") String sigunguNm,
                @Param("nx") int nx, @Param("ny") int ny);
}
