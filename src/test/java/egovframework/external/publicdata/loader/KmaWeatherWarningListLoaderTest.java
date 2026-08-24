package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.WeatherWarningMapper;
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
class KmaWeatherWarningListLoaderTest {

    @Mock
    private WeatherWarningMapper mapper;

    private KmaWeatherWarningListLoader loader() {
        return new KmaWeatherWarningListLoader(mapper);
    }

    @Test
    void operationKey는_기상특보목록만_지원한다() {
        assertThat(loader().supports("kma-weather-warning-list")).isTrue();
        assertThat(loader().supports("kma-village-forecast-vilage-fcst")).isFalse();
    }

    @Test
    void tmFc_숫자를_TIMESTAMP로_파싱하고_원본_항목을_raw_json으로_보존한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-weather-warning-list")
            .cleansedPayload("[{\"stnId\":\"108\",\"title\":\"[특보] 제08-234호 : 2026.08.21.10:20 / 호우경보 발표 (*)\","
                + "\"tmFc\":202608211020,\"tmSeq\":234,\"facilityId\":\"1270280\"}]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("stnId")).isEqualTo("108");
        assertThat(p.get("tmFcDtm")).isEqualTo(LocalDateTime.of(2026, 8, 21, 10, 20));
        assertThat(p.get("tmSeq")).isEqualTo(234);
        assertThat(p.get("title")).asString().contains("호우경보");
        assertThat(p.get("facilityId")).isEqualTo("1270280");
        assertThat(p.get("rawJson")).asString().contains("\"stnId\":\"108\"").contains("\"tmSeq\":234");
    }

    @Test
    void raw_json에는_우리가_추가한_facilityId가_섞이지_않는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-weather-warning-list")
            .cleansedPayload("[{\"stnId\":\"108\",\"title\":\"호우주의보\",\"tmFc\":202608211020,\"tmSeq\":234,"
                + "\"facilityId\":\"1270280\"}]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());

        assertThat(captor.getValue().get("rawJson")).asString().doesNotContain("facilityId");
    }

    @Test
    void 매칭된_기관_수만큼_여러_행을_각각_upsert한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-weather-warning-list")
            .cleansedPayload("["
                + "{\"stnId\":\"108\",\"title\":\"호우주의보\",\"tmFc\":202608211020,\"tmSeq\":234,\"facilityId\":\"A\"},"
                + "{\"stnId\":\"108\",\"title\":\"호우주의보\",\"tmFc\":202608211020,\"tmSeq\":234,\"facilityId\":\"B\"}"
                + "]")
            .build();

        loader().load(dto);

        verify(mapper, times(2)).upsert(org.mockito.ArgumentMatchers.any());
    }
}
