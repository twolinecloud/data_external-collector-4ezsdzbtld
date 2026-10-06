package egovframework.external.publicdata.scheduler.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * {@code kcais.tb_batch_schedule_setting}(관리자 서비스가 관리하는 배치 스케줄 설정) 읽기 전용 조회.
 * 이 서비스는 이 테이블에 쓰지 않는다 - 값은 관리자 화면에서만 바뀐다.
 */
@Mapper
public interface ScheduleSettingMapper {

    @Select("""
        SELECT data_type_cd AS "dataTypeCd", schd_cd AS "schdCd", active_yn AS "activeYn"
        FROM kcais.tb_batch_schedule_setting
        WHERE data_type_cd IN ('EXTERNAL_PUBLIC', 'EXTERNAL_LAW')
        """)
    List<Map<String, Object>> selectExternalSettings();
}
