package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.WeatherNcstMapper;
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
class KmaUltraSrtNcstLoaderTest {

    @Mock
    private WeatherNcstMapper mapper;

    private KmaUltraSrtNcstLoader loader() {
        return new KmaUltraSrtNcstLoader(mapper);
    }

    @Test
    void operationKey는_초단기실황만_지원한다() {
        assertThat(loader().supports("kma-village-forecast-ultra-srt-ncst")).isTrue();
        assertThat(loader().supports("kma-village-forecast-vilage-fcst")).isFalse();
    }

    @Test
    void 정제결과_1행을_ULID_PK와_함께_upsert한다() throws LoadException {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 21, 11, 0);
        LocalDateTime cleansedAt = LocalDateTime.of(2026, 8, 21, 11, 0, 5);
        RawStagingDto dto = RawStagingDto.builder()
            .facilityId("1270280")
            .operationKey("kma-village-forecast-ultra-srt-ncst")
            .cleansedPayload("[{\"nx\":67,\"ny\":100,\"baseDate\":\"20260821\",\"baseTime\":\"1100\","
                + "\"t1h\":\"26\",\"rn1\":null,\"reh\":\"70\",\"pty\":\"0\",\"vec\":\"180\",\"wsd\":\"1.5\","
                + "\"uuu\":\"0\",\"vvv\":\"-1.5\"}]")
            .collectedAt(collectedAt)
            .cleansedAt(cleansedAt)
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("id")).asString().hasSize(26); // ULID
        assertThat(p.get("facilityId")).isEqualTo("1270280");
        assertThat(p.get("nx")).isEqualTo(67);
        assertThat(p.get("ny")).isEqualTo(100);
        assertThat(p.get("baseDtm")).isEqualTo(LocalDateTime.of(2026, 8, 21, 11, 0));
        assertThat(p.get("t1h")).isEqualTo("26");
        assertThat(p.get("rn1")).isNull();
        assertThat(p.get("reh")).isEqualTo("70");
        assertThat(p.get("operationKey")).isEqualTo("kma-village-forecast-ultra-srt-ncst");
        assertThat(p.get("collectDtm")).isEqualTo(collectedAt);
        assertThat(p.get("cleanseDtm")).isEqualTo(cleansedAt);
    }

    @Test
    void 정제결과가_빈_배열이면_아무것도_적재하지_않는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .facilityId("1270280")
            .operationKey("kma-village-forecast-ultra-srt-ncst")
            .cleansedPayload("[]")
            .build();

        loader().load(dto);

        verify(mapper, org.mockito.Mockito.never()).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void JSON_파싱_실패하면_LoadException으로_감싼다() {
        RawStagingDto dto = RawStagingDto.builder()
            .sourceName("소스").apiName("API")
            .cleansedPayload("이건 JSON이 아님")
            .build();

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> loader().load(dto)))
            .isInstanceOf(LoadException.class);
    }
}
