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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void 한_행이_실패해도_나머지_행은_적재하고_마지막에_실패를_알린다() {
        // 2026-08-31 사고 재현 - 1000자 초과 재난문자 1건 때문에 배치 전체가 죽던 문제.
        // 이제 실패한 행만 건너뛰고 나머지는 적재하되, 유실을 감추지 않도록 예외는 던진다.
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("safetydata-disaster-msg-list")
            .sourceName("재난안전데이터공유플랫폼 (행정안전부)")
            .apiName("긴급재난문자 목록조회")
            .cleansedPayload("["
                + row("1", "A") + "," + row("2", "B") + "," + row("3", "C")
                + "]")
            .build();
        doNothing()
            .doThrow(new RuntimeException("value too long for type character varying(1000)"))
            .doNothing()
            .when(mapper).upsert(any());

        assertThatThrownBy(() -> loader().load(dto))
            .isInstanceOf(LoadException.class)
            .hasMessageContaining("3행 중 1행 실패")
            .hasMessageContaining("sn=2");

        // 실패한 2번 행 뒤의 3번 행까지 시도됐는지가 이 테스트의 핵심
        verify(mapper, times(3)).upsert(any());
    }

    @Test
    void 정제결과가_JSON이_아니면_행_단위로_건질_게_없으니_배치_전체를_실패시킨다() {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("safetydata-disaster-msg-list")
            .cleansedPayload("깨진 payload")
            .build();

        assertThatThrownBy(() -> loader().load(dto))
            .isInstanceOf(LoadException.class)
            .hasMessageContaining("정제결과 파싱 불가");

        verifyNoInteractions(mapper);
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

        verify(mapper, times(2)).upsert(any());
    }

    private static String row(String sn, String facilityId) {
        return "{\"sn\":\"" + sn + "\",\"facilityId\":\"" + facilityId + "\",\"matchedRegionNm\":\"지역\","
            + "\"crtDtm\":\"2026-08-31T10:00:00\",\"msgCn\":\"m\",\"emrgStepNm\":\"e\",\"dstSeNm\":\"호우\","
            + "\"rcptnRgnNmRaw\":\"r\",\"regDe\":null,\"mdfcnDe\":null}";
    }
}
