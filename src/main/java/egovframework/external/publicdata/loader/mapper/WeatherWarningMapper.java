package egovframework.external.publicdata.loader.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
    //
    // 같은 통보문은 수집이 10분마다 같은 날 목록을 다시 받아도 한 번만 넣는다(2026-10-26). 통보문은 발표되면 내용이 바뀌지
    // 않고, 상태가 바뀌면 새 tm_seq로 새 통보문이 나온다. 예전처럼 덮어쓰면 collect_dtm이 그날 마지막 수집 시각(23:50)으로
    // 바뀌고, tm_seq가 월을 넘어 재사용될 때 옛 통보문이 새 통보문으로 덮인다(운영 1,483행).
    // 같은 행인지는 (stn_id, tm_seq, tm_fc_dtm, facility_id, md5(raw_json))인 유니크 인덱스 uq_weather_warning_v2로 정한다
    // (scripts/weather-warning-unique-key-1-create.sql). 충돌 대상을 적지 않은 ON CONFLICT DO NOTHING이라 옛 제약
    // uq_weather_warning이 남아 있는 동안에도 오류 없이 건너뛴다 - 배포 후 2단계 스크립트로 옛 제약을 지운다.
    @Insert("""
        INSERT INTO kcais.tb_ext_weather_warning
            (weather_warning_id, stn_id, tm_fc_dtm, tm_seq, title, raw_json, facility_id,
             operation_key, collect_dtm, cleanse_dtm)
        VALUES
            (#{id}, #{stnId}, #{tmFcDtm}, #{tmSeq}, #{title}, #{rawJson}::jsonb, #{facilityId},
             #{operationKey}, #{collectDtm}, #{cleanseDtm})
        ON CONFLICT DO NOTHING
        """)
    void insertIfAbsent(Map<String, Object> p);

    // reg_dtm(적재 시각) 기준 보존정책 삭제 - WeatherNcstMapper#deleteOlderThan 주석 참고.
    @Delete("DELETE FROM kcais.tb_ext_weather_warning WHERE reg_dtm < now() - make_interval(days => #{retentionDays})")
    int deleteOlderThan(@Param("retentionDays") int retentionDays);
}
