package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.LivingUvIdxMapper;
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
class KmaUVIdxLoaderTest {

    @Mock
    private LivingUvIdxMapper mapper;

    private KmaUVIdxLoader loader() {
        return new KmaUVIdxLoader(mapper);
    }

    @Test
    void operationKey는_자외선지수만_지원한다() {
        assertThat(loader().supports("kma-living-uv-idx")).isTrue();
        assertThat(loader().supports("kma-living-air-diffusion-idx")).isFalse();
    }

    @Test
    void date와_offsetHours를_조합해_base_dtm_fcst_dtm을_계산한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-living-uv-idx")
            .cleansedPayload("[{\"areaNo\":\"1100000000\",\"date\":\"2026082412\",\"code\":\"A07_2\","
                + "\"offsetHours\":6,\"value\":\"5\",\"facilityId\":\"1270800\"}]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("facilityId")).isEqualTo("1270800");
        assertThat(p.get("areaNo")).isEqualTo("1100000000");
        assertThat(p.get("idxCode")).isEqualTo("A07_2");
        assertThat(p.get("idxValue")).isEqualTo("5");
        assertThat(p.get("baseDtm")).isEqualTo(LocalDateTime.of(2026, 8, 24, 12, 0));
        assertThat(p.get("fcstDtm")).isEqualTo(LocalDateTime.of(2026, 8, 24, 18, 0));
        assertThat(p.get("id")).asString().hasSize(26); // ULID
    }

    @Test
    void 여러_행이면_여러번_upsert한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-living-uv-idx")
            .cleansedPayload("["
                + "{\"areaNo\":\"1100000000\",\"date\":\"2026082412\",\"code\":\"A07_2\",\"offsetHours\":0,\"value\":\"9\",\"facilityId\":\"1270800\"},"
                + "{\"areaNo\":\"1100000000\",\"date\":\"2026082412\",\"code\":\"A07_2\",\"offsetHours\":3,\"value\":\"6\",\"facilityId\":\"1270800\"}"
                + "]")
            .build();

        loader().load(dto);

        verify(mapper, times(2)).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 정제결과가_빈_배열이면_아무것도_적재하지_않는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-living-uv-idx")
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
