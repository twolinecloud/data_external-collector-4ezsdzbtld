package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.DisasterMsgMapper;
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
class DisasterMsgLoaderTest {

    @Mock
    private DisasterMsgMapper mapper;

    private DisasterMsgLoader loader() {
        return new DisasterMsgLoader(mapper);
    }

    @Test
    void operationKey는_긴급재난문자만_지원한다() {
        assertThat(loader().supports("safetydata-disaster-msg-list")).isTrue();
        assertThat(loader().supports("kma-weather-warning-list")).isFalse();
    }

    @Test
    void 정제결과에_이미_들어있는_facilityId를_그대로_쓴다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("safetydata-disaster-msg-list")
            .cleansedPayload("[{\"sn\":\"12345\",\"facilityId\":\"1270280\",\"matchedRegionNm\":\"대전광역시\","
                + "\"crtDtm\":\"2026-08-21T10:00:00\",\"msgCn\":\"호우경보 발효\",\"emrgStepNm\":\"긴급재난\","
                + "\"dstSeNm\":\"호우\",\"rcptnRgnNmRaw\":\"대전광역시 ,\",\"regDe\":\"2026/08/21 10:00:05.000\","
                + "\"mdfcnDe\":null}]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("sn")).isEqualTo("12345");
        assertThat(p.get("facilityId")).isEqualTo("1270280");
        assertThat(p.get("crtDtm")).isEqualTo(LocalDateTime.of(2026, 8, 21, 10, 0, 0));
        assertThat(p.get("dstSeNm")).isEqualTo("호우");
        assertThat(p.get("mdfcnDe")).isNull();
    }

    @Test
    void 여러_기관에_매칭된_메시지는_행마다_따로_적재한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("safetydata-disaster-msg-list")
            .cleansedPayload("["
                + "{\"sn\":\"1\",\"facilityId\":\"A\",\"matchedRegionNm\":\"지역A\",\"crtDtm\":\"2026-08-21T10:00:00\","
                + "\"msgCn\":\"m\",\"emrgStepNm\":\"e\",\"dstSeNm\":\"d\",\"rcptnRgnNmRaw\":\"r\",\"regDe\":null,\"mdfcnDe\":null},"
                + "{\"sn\":\"1\",\"facilityId\":\"B\",\"matchedRegionNm\":\"지역B\",\"crtDtm\":\"2026-08-21T10:00:00\","
                + "\"msgCn\":\"m\",\"emrgStepNm\":\"e\",\"dstSeNm\":\"d\",\"rcptnRgnNmRaw\":\"r\",\"regDe\":null,\"mdfcnDe\":null}"
                + "]")
            .build();

        loader().load(dto);

        verify(mapper, times(2)).upsert(org.mockito.ArgumentMatchers.any());
    }
}
