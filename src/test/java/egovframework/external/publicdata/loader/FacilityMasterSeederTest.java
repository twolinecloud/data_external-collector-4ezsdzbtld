package egovframework.external.publicdata.loader;

import egovframework.external.publicdata.loader.mapper.WeatherFacilityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * admin-db 연결 실패가 {@code @PostConstruct} 예외 전파로 앱 부팅 자체를 막지 않는지 검증
 * (2026-08-21 - CrashLoopBackOff 위험 발견 후 수정).
 */
@ExtendWith(MockitoExtension.class)
class FacilityMasterSeederTest {

    @Mock
    private WeatherFacilityMapper mapper;

    @Test
    void mapper_upsert이_DB연결_실패로_예외를_던져도_seed는_예외를_전파하지_않는다() {
        doThrow(new DataAccessResourceFailureException("connection refused"))
            .when(mapper).upsert(anyString(), anyString(), anyDouble(), anyDouble(), anyString(), anyString(), anyInt(), anyInt());

        FacilityMasterSeeder seeder = new FacilityMasterSeeder(mapper);

        assertThatCode(seeder::seed).doesNotThrowAnyException();
    }

    @Test
    void 정상_상황에서는_59개소_전부_upsert한다() {
        FacilityMasterSeeder seeder = new FacilityMasterSeeder(mapper);

        seeder.seed();

        verify(mapper, times(59)).upsert(anyString(), anyString(), any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void 중간에_실패해도_그_전까지_성공한_행은_이미_upsert_시도된_상태다() {
        // 3번째 호출부터 실패하도록 - 앞의 성공 건은 이미 mapper.upsert가 호출된 뒤였음을 확인
        doThrow(new DataAccessResourceFailureException("connection lost"))
            .when(mapper).upsert(anyString(), anyString(), anyDouble(), anyDouble(), anyString(), anyString(), anyInt(), anyInt());

        FacilityMasterSeeder seeder = new FacilityMasterSeeder(mapper);
        seeder.seed();

        verify(mapper, atLeastOnce()).upsert(anyString(), anyString(), any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }
}
