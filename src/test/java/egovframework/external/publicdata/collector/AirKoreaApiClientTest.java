package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link AirKoreaApiClient} 검증. 응답 예시는 2026-09-03 실호출에서 받은 형태를 축소한 것 -
 * 기상청과 다른 두 지점(목록이 {@code body.items[]} 배열, 실패 시 최상위가
 * {@code OpenAPI_ServiceResponse}로 교체)과 재시도 동작에 초점을 맞춘다.
 */
class AirKoreaApiClientTest {

    private static final String ENDPOINT = "https://airkorea.example.invalid/getCtprvnRltmMesureDnsty";
    private static final String URL = ENDPOINT + "?serviceKey=test-key&sidoName=%EC%A0%84%EA%B5%AD&pageNo=1";

    private static final String TIMEOUT_BODY = """
        {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
          "errMsg":"SERVICETIMEOUT_ERROR","returnAuthMsg":"서비스 연결실패 에러","returnReasonCode":"05"}}}
        """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private AirKoreaApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new AirKoreaApiClient(restTemplate);
    }

    @Test
    void items가_배열인_에어코리아_봉투를_읽는다() {
        // 기상청은 body.items.item[]이라 같은 파서를 쓰면 예외 없이 0건이 된다 - 그 차이를 고정.
        expectOnce(withSuccess("""
            {"response":{"body":{"totalCount":2,"items":[
              {"stationName":"음성읍","sidoName":"충북","pm10Value":"20","dataTime":"2026-09-03 14:00"},
              {"stationName":"중구","sidoName":"서울","pm10Value":"28","dataTime":"2026-09-03 14:00"}],
              "pageNo":1,"numOfRows":1000},
              "header":{"resultMsg":"NORMAL_CODE","resultCode":"00"}}}
            """, MediaType.APPLICATION_JSON));

        List<String> items = call();

        assertThat(items).hasSize(2);
        assertThat(new JSONObject(items.get(0)).getString("pm10Value")).isEqualTo("20");
        server.verify();
    }

    @Test
    void 통신장애_결측도_원본_그대로_담는다() {
        // 실측 673개소 중 75개소가 이 형태였다 - 숫자로 바꾸려 들면 그 행에서 터지므로 원본 보존.
        expectOnce(withSuccess("""
            {"response":{"body":{"totalCount":1,"items":[
              {"stationName":"중구","pm10Value":"-","pm10Flag":"통신장애","pm10Grade":null}],
              "pageNo":1},"header":{"resultCode":"00","resultMsg":"NORMAL_CODE"}}}
            """, MediaType.APPLICATION_JSON));

        JSONObject item = new JSONObject(call().get(0));

        assertThat(item.getString("pm10Value")).isEqualTo("-");
        assertThat(item.getString("pm10Flag")).isEqualTo("통신장애");
    }

    @Test
    void 일시적_연결실패는_재시도해서_성공시킨다() {
        // 실호출 검증에서 3회 중 2회가 이 오류였다. 수집 주기가 시간당 1회라 다음 주기를
        // 기다리면 그 시각 자료를 영영 놓친다.
        server.expect(once(), requestTo(URL))
            .andRespond(withSuccess(TIMEOUT_BODY, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URL))
            .andRespond(withSuccess("""
                {"response":{"body":{"totalCount":1,"items":[{"stationName":"중구","pm10Value":"28"}],
                "pageNo":1},"header":{"resultCode":"00","resultMsg":"NORMAL_CODE"}}}
                """, MediaType.APPLICATION_JSON));

        assertThat(call()).hasSize(1);
        server.verify();
    }

    @Test
    void 재시도를_다_써도_실패하면_수집_실패로_올린다() {
        for (int i = 0; i < 3; i++) {
            server.expect(once(), requestTo(URL))
                .andRespond(withSuccess(TIMEOUT_BODY, MediaType.APPLICATION_JSON));
        }

        assertThatThrownBy(this::call)
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("일시적 실패가 3회 반복됨")
            .hasMessageContaining("SERVICETIMEOUT_ERROR");
        server.verify();
    }

    @Test
    void 일시적이지_않은_오류는_재시도하지_않고_바로_실패한다() {
        // 미등록 서비스키 같은 실패는 되풀이해도 달라지지 않는다 - 스케줄러 스레드만 잡아먹는다.
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("""
            {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
              "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR","returnReasonCode":"30"}}}
            """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::call)
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("에어코리아 API 오류")
            .hasMessageContaining("returnReasonCode=30");
        server.verify(); // 호출이 정확히 1회였는지 - 재시도하지 않았음을 확인
    }

    @Test
    void 한글_파라미터를_URL_인코딩해서_보낸다() {
        // 원문 그대로 붙이면 API가 400으로 거절한다(2026-09-03 운영 첫 수집에서 실측).
        // 기대 URL이 인코딩된 형태이므로, 인코딩을 빠뜨리면 이 테스트가 요청 불일치로 깨진다.
        expectOnce(withSuccess("""
            {"response":{"body":{"totalCount":0,"items":[],"pageNo":1},
            "header":{"resultCode":"00","resultMsg":"NORMAL_CODE"}}}
            """, MediaType.APPLICATION_JSON));

        assertThat(call()).isEmpty();
        server.verify();
    }

    @Test
    void 요청_오류_4xx는_재시도하지_않는다() {
        // 되풀이해도 그대로라 시간만 버린다 - 실제로 400에 3회를 쓰고 4초를 버렸다.
        server.expect(once(), requestTo(URL))
            .andRespond(withBadRequest());

        assertThatThrownBy(this::call)
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("요청 오류");
        server.verify(); // 호출이 1회였는지 - 재시도하지 않았음을 확인
    }

    @Test
    void 서버_오류_5xx는_재시도한다() {
        server.expect(once(), requestTo(URL)).andRespond(withServerError());
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("""
            {"response":{"body":{"totalCount":1,"items":[{"stationName":"중구","pm10Value":"28"}],
            "pageNo":1},"header":{"resultCode":"00","resultMsg":"NORMAL_CODE"}}}
            """, MediaType.APPLICATION_JSON));

        assertThat(call()).hasSize(1);
        server.verify();
    }

    @Test
    void resultCode가_정상이_아니면_실패로_올린다() {
        expectOnce(withSuccess("""
            {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"}}}
            """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::call)
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("resultCode=03");
    }

    @Test
    void 서비스키가_비어있으면_호출_전에_실패한다() {
        assertThatThrownBy(() -> client.call("소스", "API", ENDPOINT, "", Map.of()))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("서비스키 설정이 비어있음");
    }

    private void expectOnce(ResponseCreator response) {
        server.expect(once(), requestTo(URL)).andRespond(response);
    }

    /**
     * 수집기와 <b>똑같이</b> 한글 원문을 넘긴다 - 여기서 인코딩된 값을 넘기면 클라이언트의
     * 인코딩 책임을 건너뛰어, 운영에서 400을 맞은 그 경로를 테스트가 통과시켜 버린다
     * (2026-09-03 실제로 그렇게 놓쳤다).
     */
    private List<String> call() {
        return client.call("소스", "API", ENDPOINT, "test-key", Map.of("sidoName", "전국"));
    }
}
