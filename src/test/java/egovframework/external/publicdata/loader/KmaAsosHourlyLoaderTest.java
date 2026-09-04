package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.publicdata.loader.mapper.AsosHourlyMapper;
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
class KmaAsosHourlyLoaderTest {

    @Mock
    private AsosHourlyMapper mapper;

    private KmaAsosHourlyLoader loader() {
        return new KmaAsosHourlyLoader(mapper);
    }

    private static final String ROW = """
        {"TM":"202609031300","STN":"108","WD":"29","WS":"4.6","GST_WD":"-9","GST_WS":"-9.0","GST_TM":"-9",
         "PA":"1025.7","PS":"1028.0","PT":"2","PR":"1.7","TA":"23.1","TD":"22.1","HM":"96.0","PV":"26.6",
         "RN":"-9.0","RN_DAY":"-9.0","RN_JUN":"-9.0","RN_INT":"-9.0","SD_HR3":"-9.0","SD_DAY":"-9.0","SD_TOT":"-9.0",
         "WC":"-9","WP":"-9","WW":"-","CA_TOT":"2","CA_MID":"0","CH_MIN":"-9","CT":"-","CT_TOP":"-9","CT_MID":"-9","CT_LOW":"-9",
         "VS":"3233","SS":"1.0","SI":"-9.00","ST_GD":"-9","TS":"-1.0","TE_005":"-99.0","TE_01":"-99.0","TE_02":"-99.0","TE_03":"-99.0",
         "ST_SEA":"-9","WH":"-9.0","BF":"-9","IR":"3","IX":"-9",
         "facilityId":"1270254","stnNm":"Seoul","stnDistanceKm":16.2}""";

    @Test
    void operationKey는_ASOS만_지원한다() {
        assertThat(loader().supports("kma-asos-hourly")).isTrue();
        assertThat(loader().supports("kma-weather-warning-list")).isFalse();
    }

    @Test
    void TM을_TIMESTAMP로_파싱하고_시정_습도를_원본_그대로_담는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-asos-hourly")
            .cleansedPayload("[" + ROW + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        Map<String, Object> p = captor.getValue();

        assertThat(p.get("facilityId")).isEqualTo("1270254");
        assertThat(p.get("stnId")).isEqualTo("108");
        assertThat(p.get("stnNm")).isEqualTo("Seoul");
        assertThat(p.get("baseDtm")).isEqualTo(LocalDateTime.of(2026, 9, 3, 13, 0));
        // 시정은 10m 단위 원본값 그대로(3233 = 32.3km) - 여기서 환산하지 않는다.
        assertThat(p.get("vs")).isEqualTo("3233");
        assertThat(p.get("hm")).isEqualTo("96.0");
    }

    @Test
    void raw_json에는_우리가_추가한_facilityId_stnNm이_섞이지_않는다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-asos-hourly")
            .cleansedPayload("[" + ROW + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        String rawJson = (String) captor.getValue().get("rawJson");

        assertThat(rawJson).contains("\"TM\":\"202609031300\"").contains("\"VS\":\"3233\"");
        assertThat(rawJson).doesNotContain("facilityId").doesNotContain("stnNm").doesNotContain("stnDistanceKm");
    }

    @Test
    void 결측_표기는_숫자로_바꾸지_않고_그대로_넘긴다() throws LoadException {
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-asos-hourly")
            .cleansedPayload("[" + ROW + "]")
            .build();

        loader().load(dto);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper, times(1)).upsert(captor.capture());
        // WW="-", WC/WP="-9" 등 결측 표기가 VARCHAR 그대로 들어간다.
        assertThat(captor.getValue().get("ww")).isEqualTo("-");
        assertThat(captor.getValue().get("wc")).isEqualTo("-9");
    }

    @Test
    void 행_하나가_실패해도_나머지_행은_적재되고_마지막에_실패로_보고한다() throws LoadException {
        String row2 = ROW.replace("\"STN\":\"108\"", "\"STN\":\"112\"")
            .replace("\"facilityId\":\"1270254\"", "\"facilityId\":\"1270552\"");
        RawStagingDto dto = RawStagingDto.builder()
            .operationKey("kma-asos-hourly")
            .cleansedPayload("[" + ROW + "," + row2 + "]")
            .build();
        doThrow(new RuntimeException("DB 오류")).when(mapper).upsert(any());

        assertThatThrownBy(() -> loader().load(dto))
            .isInstanceOf(LoadException.class)
            .hasMessageContaining("2행 중 2행 실패");

        // 실패해도 두 행 모두 upsert가 시도됐다 - 첫 행 실패로 배치 전체가 중단되지 않는다.
        verify(mapper, times(2)).upsert(any());
    }
}
