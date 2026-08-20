package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DisasterMsgCollector} 검증. 응답 문자열은 2026-08-18 실호출로 확인한 실제 구조를 축약한 것.
 */
@ExtendWith(MockitoExtension.class)
class DisasterMsgCollectorTest {

    @Mock
    private RestTemplate restTemplate;

    private static String page(int totalCount, int... sns) {
        StringBuilder body = new StringBuilder();
        for (int sn : sns) {
            if (body.length() > 0) {
                body.append(',');
            }
            body.append("{\"SN\":").append(sn)
                .append(",\"RCPTN_RGN_NM\":\"경상남도 거제시 \",\"DST_SE_NM\":\"호우\"}");
        }
        return "{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE\",\"errorMsg\":null},"
            + "\"totalCount\":" + totalCount + ",\"body\":[" + body + "]}";
    }

    private DisasterMsgCollector collector(String endpoint, String serviceKey) {
        return new DisasterMsgCollector(restTemplate, endpoint, serviceKey);
    }

    @Test
    void key_sourceName_apiName_은_고정값이고_facilityId는_없다() {
        DisasterMsgCollector c = collector("https://example.invalid", "test");
        assertThat(c.key()).isEqualTo("safetydata-disaster-msg-list");
        assertThat(c.operationKey()).isEqualTo(c.key());
        assertThat(c.sourceName()).isEqualTo("재난안전데이터공유플랫폼 (행정안전부)");
        assertThat(c.apiName()).isEqualTo("긴급재난문자 목록조회");
        assertThat(c.facilityId()).isNull();
    }

    @Test
    void envelope을_벗겨_재난문자_1건씩_반환한다() throws CollectException {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(page(2, 266798, 266799));

        List<String> result = collector("https://example.invalid", "test-key").collect();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).contains("\"SN\":266798");
        assertThat(result.get(1)).contains("\"SN\":266799");
        // 봉투(header/totalCount)는 항목에 섞여 나오면 안 됨
        assertThat(result).noneMatch(item -> item.contains("resultCode"));
    }

    @Test
    void crtDt를_오늘로_넣어_호출한다() throws CollectException {
        // crtDt가 없으면 API가 SN 오름차순으로 2023년 데이터를 주기 때문에 반드시 있어야 한다
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(page(1, 1));

        collector("https://example.invalid/DSSP-IF-00247", "k").collect();

        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(captor.capture(), eq(String.class));
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(captor.getValue().toString()).contains("crtDt=" + today);
    }

    @Test
    void 요청_URL에_serviceKey와_엔드포인트가_포함된다() throws CollectException {
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(page(1, 1));

        collector("https://example.invalid/DSSP-IF-00247", "my-service-key").collect();

        ArgumentCaptor<URI> captor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(captor.capture(), eq(String.class));
        assertThat(captor.getValue().toString())
            .contains("DSSP-IF-00247")
            .contains("serviceKey=my-service-key")
            .contains("returnType=json");
    }

    @Test
    void totalCount가_한_페이지를_넘으면_다음_페이지도_받아온다() throws CollectException {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(page(3, 1, 2))   // 1페이지: 2건 (전체 3건)
            .thenReturn(page(3, 3));     // 2페이지: 나머지 1건

        List<String> result = collector("https://example.invalid", "k").collect();

        assertThat(result).hasSize(3);
    }

    @Test
    void 빈_페이지를_받으면_totalCount와_무관하게_중단한다() throws CollectException {
        // totalCount를 믿고 무한히 돌지 않도록 하는 방어
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn(page(999, 1))
            .thenReturn(page(999));

        List<String> result = collector("https://example.invalid", "k").collect();

        assertThat(result).hasSize(1);
        verify(restTemplate, org.mockito.Mockito.times(2)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void resultCode가_실패면_CollectException으로_감싼다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"header\":{\"resultCode\":\"30\",\"resultMsg\":\"SERVICE KEY IS NOT REGISTERED ERROR\","
                + "\"errorMsg\":\"등록되지 않은 서비스키\"},\"body\":null}");

        assertThatThrownBy(() -> collector("https://example.invalid", "bad").collect())
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("safetydata API 오류")
            .hasMessageContaining("등록되지 않은 서비스키");
    }

    @Test
    void 응답이_JSON이_아니면_파싱실패로_감싼다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn("<html>error</html>");

        assertThatThrownBy(() -> collector("https://example.invalid", "k").collect())
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("응답 파싱 실패");
    }

    @Test
    void 엔드포인트가_비어있으면_호출_없이_CollectException을_던진다() {
        assertThatThrownBy(() -> collector("", "test").collect())
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("엔드포인트/서비스키");
    }

    @Test
    void 서비스키가_비어있으면_호출_없이_CollectException을_던진다() {
        assertThatThrownBy(() -> collector("https://example.invalid", "").collect())
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("엔드포인트/서비스키");
    }

    @Test
    void API_호출_자체가_실패하면_CollectException으로_감싼다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenThrow(new org.springframework.web.client.ResourceAccessException("연결 실패"));

        assertThatThrownBy(() -> collector("https://example.invalid", "test").collect())
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("API 호출 실패");
    }
}
