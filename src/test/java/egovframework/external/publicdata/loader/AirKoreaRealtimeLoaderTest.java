package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.AirQualityMapper;
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
class AirKoreaRealtimeLoaderTest {

    @Mock
    private AirQualityMapper mapper;

    private AirKoreaRealtimeLoader loader() {
        return new AirKoreaRealtimeLoader(mapper);
    }

    private static final String ROW = """
        {"stationName":"별양동","sidoName":"경기","dataTime":"2026-09-04 10:00",
         "pm10Value":"28","pm10Grade":"1","pm10Flag":null,
         "pm25Value":"13","pm25Grade":"1","pm25Flag":null,
         "khaiValue":"58","khaiGrade":"2",
         "so2Value":"0.002","so2Grade":"1","so2Flag":null,
         "coValue":"0.3","coGrade":"1","coFlag":null,
         "o3Value":"0.039","o3Grade":"2","o3Flag":null,
         "no2Value":"0.005","no2Grade":"1","no2Flag":null,
         "facilityId":"1270254","stationDistanceKm":0.9}""";

    @Test
    void operationKey는_에어코리아_실시간측정만_지원한다() {
        assertThat(loader().supports("airkorea-realtime-measure")).isTrue();
        assertThat(loader().supports("kma-asos-hourly")).isFalse();
    }

    @Test
    void dataTime을_TIMESTAMP로_파싱하고_PM10을_원본_그대로_담는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("airkorea-realtime-measure")
            .cleansedPayload("[" + ROW + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("facilityId")).isEqualTo("1270254");
        assertThat(p.get("stationNm")).isEqualTo("별양동");
        assertThat(p.get("sidoNm")).isEqualTo("경기");
        assertThat(p.get("baseDtm")).isEqualTo(LocalDateTime.of(2026, 9, 4, 10, 0));
        assertThat(p.get("pm10Value")).isEqualTo("28");
        assertThat(p.get("pm10Grade")).isEqualTo("1");
    }

    @Test
    void JSON_null_결측은_자바_null로_안전하게_넘긴다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("airkorea-realtime-measure")
            .cleansedPayload("[" + ROW + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        // pm10Flag가 JSON null이었다 - row.getString()을 그냥 쓰면 예외가 나므로 isNull 방어가 필요.
        assertThat(captor.getValue().get("pm10Flag")).isNull();
    }

    @Test
    void 통신장애_결측_문자열은_원본_그대로_남긴다() throws LoadException {
        String row = ROW.replace("\"pm10Value\":\"28\"", "\"pm10Value\":\"-\"")
            .replace("\"pm10Flag\":null", "\"pm10Flag\":\"통신장애\"");
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("airkorea-realtime-measure")
            .cleansedPayload("[" + row + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        assertThat(captor.getValue().get("pm10Value")).isEqualTo("-");
        assertThat(captor.getValue().get("pm10Flag")).isEqualTo("통신장애");
    }

    @Test
    void raw_json에는_우리가_추가한_facilityId가_섞이지_않는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("airkorea-realtime-measure")
            .cleansedPayload("[" + ROW + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        String rawJson = (String) captor.getValue().get("rawJson");

        assertThat(rawJson).contains("\"stationName\":\"별양동\"").contains("\"pm10Value\":\"28\"");
        assertThat(rawJson).doesNotContain("facilityId").doesNotContain("stationDistanceKm");
    }

    @Test
    void 행_하나가_실패해도_나머지_행은_적재되고_마지막에_실패로_보고한다() throws LoadException {
        String row2 = ROW.replace("\"stationName\":\"별양동\"", "\"stationName\":\"신사동\"")
            .replace("\"facilityId\":\"1270254\"", "\"facilityId\":\"1270552\"");
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("airkorea-realtime-measure")
            .cleansedPayload("[" + ROW + "," + row2 + "]")
            .build();
        doThrow(new RuntimeException("DB 오류")).when(mapper).upsert(any());

        assertThatThrownBy(() -> loader().load(dto))
            .isInstanceOf(LoadException.class)
            .hasMessageContaining("2행 중 2행 실패");

        verify(mapper, times(2)).upsert(any());
    }
}
