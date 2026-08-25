package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.WeatherUltraFcstMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KmaUltraSrtFcstLoaderTest {

    @Mock
    private WeatherUltraFcstMapper mapper;

    private KmaUltraSrtFcstLoader loader() {
        return new KmaUltraSrtFcstLoader(mapper);
    }

    @Test
    void operationKey는_초단기예보만_지원한다() {
        assertThat(loader().supports("kma-village-forecast-ultra-srt-fcst")).isTrue();
        assertThat(loader().supports("kma-village-forecast-ultra-srt-ncst")).isFalse();
    }

    @Test
    void base_dtm과_fcst_dtm을_각각_조합해서_upsert한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .facilityId("1270280")
            .operationKey("kma-village-forecast-ultra-srt-fcst")
            .cleansedPayload("[{\"nx\":67,\"ny\":100,\"baseDate\":\"20260821\",\"baseTime\":\"1100\","
                + "\"fcstDate\":\"20260821\",\"fcstTime\":\"1200\","
                + "\"t1h\":\"27\",\"rn1\":null,\"sky\":\"1\",\"uuu\":null,\"vvv\":null,\"reh\":\"65\","
                + "\"pty\":\"0\",\"pop\":\"20\",\"lgt\":\"0\",\"vec\":\"200\",\"wsd\":\"2.0\"}]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("baseDtm")).isEqualTo(LocalDateTime.of(2026, 8, 21, 11, 0));
        assertThat(p.get("fcstDtm")).isEqualTo(LocalDateTime.of(2026, 8, 21, 12, 0));
        assertThat(p.get("sky")).isEqualTo("1");
        assertThat(p.get("uuu")).isNull();
    }

    @Test
    void senstemp가_있으면_BigDecimal로_변환해서_적재한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .facilityId("1270280")
            .operationKey("kma-village-forecast-ultra-srt-fcst")
            .cleansedPayload("[{\"nx\":67,\"ny\":100,\"baseDate\":\"20260821\",\"baseTime\":\"1100\","
                + "\"fcstDate\":\"20260821\",\"fcstTime\":\"1200\",\"t1h\":\"27\",\"senstemp\":26.8}]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat((java.math.BigDecimal) p.get("sensTemp")).isEqualByComparingTo("26.8");
    }

    @Test
    void 여러_행이면_여러번_upsert한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .facilityId("1270280")
            .operationKey("kma-village-forecast-ultra-srt-fcst")
            .cleansedPayload("["
                + "{\"nx\":67,\"ny\":100,\"baseDate\":\"20260821\",\"baseTime\":\"1100\","
                + "\"fcstDate\":\"20260821\",\"fcstTime\":\"1200\",\"t1h\":\"27\"},"
                + "{\"nx\":67,\"ny\":100,\"baseDate\":\"20260821\",\"baseTime\":\"1100\","
                + "\"fcstDate\":\"20260821\",\"fcstTime\":\"1300\",\"t1h\":\"28\"}"
                + "]")
            .build();

        loader().load(dto);

        verify(mapper, times(2)).upsert(org.mockito.ArgumentMatchers.any());
    }
}
