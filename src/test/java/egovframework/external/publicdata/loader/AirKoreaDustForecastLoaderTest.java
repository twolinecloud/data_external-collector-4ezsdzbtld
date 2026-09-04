package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.DustForecastMapper;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AirKoreaDustForecastLoaderTest {

    @Mock
    private DustForecastMapper mapper;

    private AirKoreaDustForecastLoader loader() {
        return new AirKoreaDustForecastLoader(mapper);
    }

    private static final String ROW = """
        {"facilityId":"1270785","informRegion":"경기북부","informData":"2026-09-05",
         "dataTime":"2026-09-04 11시 발표","grade":"매우나쁨",
         "informCause":"국외 유입 영향","informOverall":"전국 대체로 나쁨"}""";

    @Test
    void operationKey는_대기질예보통보만_지원한다() {
        assertThat(loader().supports("airkorea-dust-forecast")).isTrue();
        assertThat(loader().supports("airkorea-realtime-measure")).isFalse();
    }

    @Test
    void informData는_fcstDtm으로_dataTime은_baseDtm으로_파싱한다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("airkorea-dust-forecast")
            .cleansedPayload("[" + ROW + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("facilityId")).isEqualTo("1270785");
        assertThat(p.get("informRegion")).isEqualTo("경기북부");
        assertThat(p.get("fcstDtm")).isEqualTo(LocalDateTime.of(2026, 9, 5, 0, 0));
        assertThat(p.get("baseDtm")).isEqualTo(LocalDateTime.of(2026, 9, 4, 11, 0));
        assertThat(p.get("grade")).isEqualTo("매우나쁨");
    }

    @Test
    void raw_json에는_우리가_추가한_facilityId_informRegion이_섞이지_않는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("airkorea-dust-forecast")
            .cleansedPayload("[" + ROW + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        String rawJson = (String) captor.getValue().get("rawJson");

        assertThat(rawJson).contains("\"grade\":\"매우나쁨\"").contains("\"informData\":\"2026-09-05\"");
        assertThat(rawJson).doesNotContain("facilityId").doesNotContain("informRegion");
    }

    @Test
    void 행_하나가_실패해도_나머지_행은_적재되고_마지막에_실패로_보고한다() throws LoadException {
        String row2 = ROW.replace("\"facilityId\":\"1270785\"", "\"facilityId\":\"1270254\"");
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("airkorea-dust-forecast")
            .cleansedPayload("[" + ROW + "," + row2 + "]")
            .build();
        doThrow(new RuntimeException("DB 오류")).when(mapper).upsert(any());

        assertThatThrownBy(() -> loader().load(dto))
            .isInstanceOf(LoadException.class)
            .hasMessageContaining("2행 중 2행 실패");

        verify(mapper, times(2)).upsert(any());
    }
}
