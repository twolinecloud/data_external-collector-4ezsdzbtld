package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KmaApiClient}의 페이지네이션 검증. 단기예보에서 실측 944~980건(numOfRows=1000에 근접)이
 * 관찰돼서, "totalCount를 안 읽어서 잘려도 모르는" 문제를 고치기 위해 도입 - 그 동작을 고정해둠.
 */
@ExtendWith(MockitoExtension.class)
class KmaApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private KmaApiClient apiClient() {
        return new KmaApiClient(restTemplate);
    }

    private static String page(int totalCount, int itemCount, int startIdx) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < itemCount; i++) {
            if (i > 0) items.append(',');
            items.append("{\"idx\":").append(startIdx + i).append('}');
        }
        return """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"totalCount":%d,"items":{"item":[%s]}}}}
            """.formatted(totalCount, items);
    }

    @Test
    void 첫_페이지에서_totalCount를_다_채우면_추가_호출을_안한다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(page(2, 2, 0));

        List<String> items = apiClient().call("소스", "API", "https://example.invalid", "key", params());

        assertThat(items).hasSize(2);
        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void totalCount가_더_크면_pageNo를_늘려가며_모자란_만큼_더_받아온다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(page(5, 2, 0))
            .thenReturn(page(5, 2, 2))
            .thenReturn(page(5, 1, 4));

        List<String> items = apiClient().call("소스", "API", "https://example.invalid", "key", params());

        assertThat(items).hasSize(5);
        verify(restTemplate, times(3)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void 요청_URL의_pageNo가_호출마다_증가한다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(page(4, 2, 0))
            .thenReturn(page(4, 2, 2));

        apiClient().call("소스", "API", "https://example.invalid", "key", params());

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, times(2)).getForObject(uriCaptor.capture(), eq(String.class));
        List<URI> uris = uriCaptor.getAllValues();
        assertThat(uris.get(0).toString()).contains("pageNo=1");
        assertThat(uris.get(1).toString()).contains("pageNo=2");
    }

    @Test
    void 빈_페이지를_받으면_totalCount를_못_채웠어도_중단한다() {
        // totalCount는 5라고 주장하지만 2페이지가 비어있는 방어적 상황 - 무한루프 방지
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(page(5, 2, 0))
            .thenReturn(page(5, 0, 0));

        List<String> items = apiClient().call("소스", "API", "https://example.invalid", "key", params());

        assertThat(items).hasSize(2);
        verify(restTemplate, times(2)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void totalCount가_비정상적으로_커도_MAX_PAGES에서_멈춘다() {
        // 매 페이지 2건씩 주면서 totalCount=9999라고 우기는 상황 (API 이상동작 방어)
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenAnswer(inv -> page(9999, 2, 0));

        List<String> items = apiClient().call("소스", "API", "https://example.invalid", "key", params());

        verify(restTemplate, times(10)).getForObject(any(URI.class), eq(String.class)); // MAX_PAGES=10
        assertThat(items).hasSize(20);
    }

    @Test
    void resultCode가_비정상이면_CollectException으로_감싸_던진다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("""
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"}}}
                """);

        assertThatThrownBy(() -> apiClient().call("소스", "API", "https://example.invalid", "key", params()))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("KMA API 오류");
    }

    private static Map<String, String> params() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("numOfRows", "2");
        p.put("dataType", "JSON");
        return p;
    }
}
