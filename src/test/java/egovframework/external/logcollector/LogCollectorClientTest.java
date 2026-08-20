package egovframework.external.logcollector;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogCollectorClientTest {

    private static final String BASE_URL = "https://kcais-admin.twolinecloud.com/logc";

    @Mock
    private RestTemplate restTemplate;

    private LogCollectorClient client(boolean enabled) {
        return new LogCollectorClient(restTemplate, BASE_URL, enabled);
    }

    @Test
    void enabled가_false면_네트워크_호출_없이_전부_empty를_반환한다() {
        LogCollectorClient c = client(false);

        assertThat(c.createBatch(new JSONObject())).isEmpty();
        assertThat(c.createStep("exec1", new JSONObject())).isEmpty();
        assertThat(c.postExternalCollects("exec1", new JSONArray())).isFalse();
        assertThat(c.finishStep("step1", new JSONObject())).isFalse();
        assertThat(c.finishBatch("exec1", new JSONObject())).isFalse();

        verify(restTemplate, never()).exchange(any(URI.class), any(HttpMethod.class), any(), eq(String.class));
    }

    @Test
    void createBatch_성공시_execId를_반환한다() {
        when(restTemplate.exchange(eq(URI.create(BASE_URL + "/api/v1/logs/batches")), eq(HttpMethod.POST), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"success\":true,\"result\":{\"execId\":\"20260820EXT001\"}}"));

        Optional<String> execId = client(true).createBatch(new JSONObject().put("jobId", "EXTERNAL_API"));

        assertThat(execId).contains("20260820EXT001");
    }

    @Test
    void createStep_성공시_stepLogId를_반환한다() {
        when(restTemplate.exchange(eq(URI.create(BASE_URL + "/api/v1/logs/batches/20260820EXT001/steps")), eq(HttpMethod.POST), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"result\":{\"stepLogId\":\"20260820EXT00101\"}}"));

        Optional<String> stepLogId = client(true).createStep("20260820EXT001", new JSONObject());

        assertThat(stepLogId).contains("20260820EXT00101");
    }

    @Test
    void 요청_바디가_JSON으로_직렬화되어_전송된다() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"result\":{\"execId\":\"x\"}}"));

        JSONObject body = new JSONObject().put("jobId", "EXTERNAL_API").put("dataTypeCd", "EXTERNAL");
        client(true).createBatch(body);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.POST), captor.capture(), eq(String.class));
        assertThat((String) captor.getValue().getBody()).contains("\"jobId\":\"EXTERNAL_API\"").contains("\"dataTypeCd\":\"EXTERNAL\"");
    }

    @Test
    void finishStep은_PATCH로_호출된다() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.PATCH), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"result\":{}}"));

        boolean ok = client(true).finishStep("step1", new JSONObject().put("stepStsCd", "SUCCESS"));

        assertThat(ok).isTrue();
        verify(restTemplate).exchange(eq(URI.create(BASE_URL + "/api/v1/logs/steps/step1")), eq(HttpMethod.PATCH), any(), eq(String.class));
    }

    @Test
    void 응답이_비정상이어도_예외를_던지지_않고_empty를_반환한다() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(), eq(String.class)))
            .thenThrow(new ResourceAccessException("연결 실패"));

        Optional<String> result = client(true).createBatch(new JSONObject());

        assertThat(result).isEmpty();
    }

    @Test
    void 응답_바디가_JSON이_아니어도_예외를_던지지_않는다() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("<html>error</html>"));

        Optional<String> result = client(true).createBatch(new JSONObject());

        assertThat(result).isEmpty();
    }

    @Test
    void postExternalCollects는_배열_바디를_그대로_보낸다() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"result\":{\"count\":1,\"ids\":[\"a\"]}}"));

        JSONArray body = new JSONArray().put(new JSONObject().put("srcNm", "test"));
        boolean ok = client(true).postExternalCollects("exec1", body);

        assertThat(ok).isTrue();
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
            eq(URI.create(BASE_URL + "/api/v1/logs/batches/exec1/external-collects")),
            eq(HttpMethod.POST), captor.capture(), eq(String.class));
        assertThat((String) captor.getValue().getBody()).contains("\"srcNm\":\"test\"");
    }
}
