package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.LivingAirDiffusionIdxMapper;
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
class KmaAirDiffusionIdxLoaderTest {

    @Mock
    private LivingAirDiffusionIdxMapper mapper;

    private KmaAirDiffusionIdxLoader loader() {
        return new KmaAirDiffusionIdxLoader(mapper);
    }

    @Test
    void operationKey는_대기정체지수만_지원한다() {
        assertThat(loader().supports("kma-living-air-diffusion-idx")).isTrue();
        assertThat(loader().supports("kma-living-uv-idx")).isFalse();
    }

    @Test
    void date와_offsetHours를_조합해_base_dtm_fcst_dtm을_계산한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-living-air-diffusion-idx")
            .cleansedPayload("[{\"areaNo\":\"1100000000\",\"date\":\"2026082412\",\"code\":\"A09\","
                + "\"offsetHours\":78,\"value\":\"75\",\"facilityId\":\"1270800\"}]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("baseDtm")).isEqualTo(LocalDateTime.of(2026, 8, 24, 12, 0));
        assertThat(p.get("fcstDtm")).isEqualTo(LocalDateTime.of(2026, 8, 27, 18, 0));
        assertThat(p.get("idxValue")).isEqualTo("75");
    }

    @Test
    void 정제결과가_빈_배열이면_아무것도_적재하지_않는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-living-air-diffusion-idx")
            .cleansedPayload("[]")
            .build();

        loader().load(dto);

        verify(mapper, org.mockito.Mockito.never()).upsert(org.mockito.ArgumentMatchers.any());
    }
}
