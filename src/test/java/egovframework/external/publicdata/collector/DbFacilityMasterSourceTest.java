package egovframework.external.publicdata.collector;

import egovframework.external.publicdata.loader.mapper.WeatherFacilityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * admin-db 조회 실패가 절대 예외로 전파되지 않는지 검증(2026-08-24, Phase C) - 컬렉터가
 * 스케줄 틱마다 이 소스를 다시 조회하므로, DB 장애 시 그 틱만 0건으로 건너뛰어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class DbFacilityMasterSourceTest {

    @Mock
    private WeatherFacilityMapper mapper;

    @Test
    void 정상_조회시_FacilityMasterRecord_목록으로_변환한다() {
        when(mapper.selectAll()).thenReturn(List.of(Map.of(
            "facilityId", "1270254", "facilityNm", "서울지방교정청",
            "sidoNm", "경기도", "sigunguNm", "과천시", "nx", 60, "ny", 124
        )));

        List<FacilityMasterRecord> records = new DbFacilityMasterSource(mapper).current();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).facilityId()).isEqualTo("1270254");
        assertThat(records.get(0).nx()).isEqualTo("60");
        assertThat(records.get(0).ny()).isEqualTo("124");
    }

    @Test
    void DB_조회가_예외를_던지면_빈_리스트를_반환하고_예외를_전파하지_않는다() {
        when(mapper.selectAll()).thenThrow(new DataAccessResourceFailureException("connection refused"));

        List<FacilityMasterRecord> records = new DbFacilityMasterSource(mapper).current();

        assertThat(records).isEmpty();
    }
}
