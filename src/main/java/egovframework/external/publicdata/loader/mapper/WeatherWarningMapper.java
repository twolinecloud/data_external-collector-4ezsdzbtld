package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * {@code kcais.tb_ext_weather_warning} (기상특보목록조회) - 멱등키
 * {@code (stn_id, tm_seq, facility_id)}. facility_id는 2026-08-21 추가 - stnId(시도 단위
 * 관할구역) 하나가 여러 교정기관에 매칭되므로, {@code KmaWeatherWarningListCleanser}가
 * 매칭된 기관 수만큼 행을 복제해서 넘겨준다(재난문자와 동일 패턴).
 */
@Mapper
public interface WeatherWarningMapper {

    // raw_json은 JSONB 컬럼 - MyBatis 기본 타입핸들러가 text->jsonb를 자동변환 안 해줘서
    // #{rawJson}::jsonb로 SQL 레벨에서 명시 캐스팅한다(별도 TypeHandler 등록 없이 처리).
    @Insert("""
        INSERT INTO kcais.tb_ext_weather_warning
            (weather_warning_id, stn_id, tm_fc_dtm, tm_seq, title, raw_json, facility_id,
             operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{stnId}, #{tmFcDtm}, #{tmSeq}, #{title}, #{rawJson}::jsonb, #{facilityId},
             #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT (stn_id, tm_seq, facility_id) DO UPDATE SET
            tm_fc_dtm = EXCLUDED.tm_fc_dtm,
            title = EXCLUDED.title,
            raw_json = EXCLUDED.raw_json,
            collect_dtm = EXCLUDED.collect_dtm,
            cleanse_dtm = EXCLUDED.cleanse_dtm
        """)
    void upsert(Map<String, Object> p);
}
