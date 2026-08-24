package egovframework.external.service;

import egovframework.external.model.PurgeResult;
import egovframework.external.publicdata.loader.mapper.DisasterMsgMapper;
import egovframework.external.publicdata.loader.mapper.WeatherNcstMapper;
import egovframework.external.publicdata.loader.mapper.WeatherUltraFcstMapper;
import egovframework.external.publicdata.loader.mapper.WeatherVilageFcstMapper;
import egovframework.external.publicdata.loader.mapper.WeatherWarningMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PublicDataPurgeService}의 오케스트레이션 단위 테스트.
 * {@code PublicDataLoadServiceTest}와 대칭 - enabled=false 전면 no-op 가드,
 * 테이블별 fail-isolation(하나 실패해도 나머지는 계속 처리)을 검증.
 */
@ExtendWith(MockitoExtension.class)
class PublicDataPurgeServiceTest {

    private static final int RETENTION_DAYS = 30;

    @Mock
    private WeatherNcstMapper weatherNcstMapper;

    @Mock
    private WeatherUltraFcstMapper weatherUltraFcstMapper;

    @Mock
    private WeatherVilageFcstMapper weatherVilageFcstMapper;

    @Mock
    private WeatherWarningMapper weatherWarningMapper;

    @Mock
    private DisasterMsgMapper disasterMsgMapper;

    private PublicDataPurgeService service(boolean enabled) {
        return new PublicDataPurgeService(
            weatherNcstMapper, weatherUltraFcstMapper, weatherVilageFcstMapper,
            weatherWarningMapper, disasterMsgMapper, enabled, RETENTION_DAYS);
    }

    @Test
    void enabled가_false면_아무_매퍼도_호출하지_않고_빈_결과를_반환한다() {
        PurgeResult result = service(false).purgeExpired();

        assertThat(result).isEqualTo(new PurgeResult(0, 0, 0));
        verify(weatherNcstMapper, never()).deleteOlderThan(anyInt());
    }

    @Test
    void enabled가_true면_5개_테이블_모두_삭제하고_건수를_합산한다() {
        when(weatherNcstMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(1);
        when(weatherUltraFcstMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(6);
        when(weatherVilageFcstMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(66);
        when(weatherWarningMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(921);
        when(disasterMsgMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(30);

        PurgeResult result = service(true).purgeExpired();

        assertThat(result).isEqualTo(new PurgeResult(1 + 6 + 66 + 921 + 30, 5, 0));
    }

    @Test
    void 한_테이블에서_예외가_나도_나머지_테이블은_계속_처리하고_실패로만_집계한다() {
        when(weatherNcstMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(1);
        when(weatherUltraFcstMapper.deleteOlderThan(RETENTION_DAYS))
            .thenThrow(new RuntimeException("connection refused"));
        when(weatherVilageFcstMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(66);
        when(weatherWarningMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(921);
        when(disasterMsgMapper.deleteOlderThan(RETENTION_DAYS)).thenReturn(30);

        PurgeResult result = service(true).purgeExpired();

        assertThat(result).isEqualTo(new PurgeResult(1 + 66 + 921 + 30, 4, 1));
    }
}
