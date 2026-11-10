package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 호출 시각을 지정하면 그 시각의 기준시각으로 요청하는지 - 비어 버린 지난 시각을 채우는 보충 실행의 바탕. */
class KmaCollectorRequestedAtTest {

    private final KmaApiClient apiClient = mock(KmaApiClient.class);
    private final Location location =
        new FacilityLocationLoader(new CsvFacilityMasterSource(new FacilityMasterCsvLoader())).all().get(0);

    @SuppressWarnings("unchecked")
    private Map<String, String> requestedParams(String path) throws Exception {
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(apiClient).call(anyString(), anyString(), eq("https://example.invalid" + path), anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void 초단기실황은_지정한_시각의_정각_발표분을_요청한다() throws Exception {
        when(apiClient.call(any(), any(), any(), any(), any())).thenReturn(List.of());

        new KmaUltraSrtNcstCollector(apiClient, "https://example.invalid", "k", location,
            LocalDateTime.of(2026, 11, 10, 6, 10)).collect();

        Map<String, String> params = requestedParams("/getUltraSrtNcst");
        assertThat(params).containsEntry("base_date", "20261110").containsEntry("base_time", "0600");
    }

    @Test
    void 초단기예보는_지정한_시각의_30분_발표분을_요청한다() throws Exception {
        when(apiClient.call(any(), any(), any(), any(), any())).thenReturn(List.of());

        new KmaUltraSrtFcstCollector(apiClient, "https://example.invalid", "k", location,
            LocalDateTime.of(2026, 11, 10, 6, 45)).collect();

        Map<String, String> params = requestedParams("/getUltraSrtFcst");
        assertThat(params).containsEntry("base_date", "20261110").containsEntry("base_time", "0630");
    }
}
