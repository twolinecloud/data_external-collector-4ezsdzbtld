package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.WeatherVilageFcstMapper;
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
class KmaVilageFcstLoaderTest {

    @Mock
    private WeatherVilageFcstMapper mapper;

    private KmaVilageFcstLoader loader() {
        return new KmaVilageFcstLoader(mapper);
    }

    @Test
    void operationKey는_단기예보만_지원한다() {
        assertThat(loader().supports("kma-village-forecast-vilage-fcst")).isTrue();
        assertThat(loader().supports("kma-village-forecast-ultra-srt-fcst")).isFalse();
    }

    @Test
    void tmn_tmx처럼_하루_1회만_있는_필드도_null로_안전하게_처리한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .facilityId("1270280")
            .operationKey("kma-village-forecast-vilage-fcst")
            .cleansedPayload("[{\"nx\":67,\"ny\":100,\"baseDate\":\"20260821\",\"baseTime\":\"0800\","
                + "\"fcstDate\":\"20260821\",\"fcstTime\":\"1200\","
                + "\"pop\":\"20\",\"pty\":\"0\",\"pcp\":\"강수없음\",\"reh\":\"65\",\"sno\":\"적설없음\","
                + "\"sky\":\"1\",\"tmp\":\"27\",\"tmn\":null,\"tmx\":\"29\",\"uuu\":\"0\",\"vec\":\"200\","
                + "\"vvv\":\"-1\",\"wav\":\"0\",\"wsd\":\"2.0\"}]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("tmn")).isNull();
        assertThat(p.get("tmx")).isEqualTo("29");
        assertThat(p.get("baseDtm")).isEqualTo(LocalDateTime.of(2026, 8, 21, 8, 0));
        assertThat(p.get("fcstDtm")).isEqualTo(LocalDateTime.of(2026, 8, 21, 12, 0));
    }
}
