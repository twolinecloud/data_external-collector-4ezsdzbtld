package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link KmaApiHubClient}의 CP949 고정폭 응답 파싱 검증. 실제 API 허브 응답
 * ({@code kma_sfctm2.php}, 2026-09-03 실측)의 형태를 그대로 축소해서 쓴다 - 주석/헤더 줄,
 * {@code #7777END} 종료 표시, 결측 표기(-9 / -), 한글 포함 여부까지.
 */
class KmaApiHubClientTest {

    private static final Charset CP949 = Charset.forName("CP949");
    private static final String ENDPOINT = "https://apihub.example.invalid/kma_sfctm2.php";
    private static final String URL = ENDPOINT + "?authKey=test-key&stn=0";
    private static final List<String> FIELDS = List.of("TM", "STN", "HM", "WW", "VS", "IX");

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private KmaApiHubClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new KmaApiHubClient(restTemplate, new long[] {0L, 0L}); // 대기 없이 3회 시도
    }

    @Test
    void 주석줄을_걷어내고_데이터줄만_필드명과_짝지어_JSON으로_돌려준다() {
        respondWith("""
            #START7777
            #  1. TM     : 관측시각 (KST)
            # YYMMDDHHMI STN   HM WW    VS IX
            202609010700 108 96.0 01   580  1
            202609010700 136 98.0 19   124  1
            #7777END
            """);

        List<String> items = call();

        assertThat(items).hasSize(2);
        JSONObject first = new JSONObject(items.get(0));
        assertThat(first.getString("TM")).isEqualTo("202609010700");
        assertThat(first.getString("STN")).isEqualTo("108");
        assertThat(first.getString("VS")).isEqualTo("580");
        assertThat(first.getString("HM")).isEqualTo("96.0");
    }

    @Test
    void 결측_표기는_원본_그대로_담는다() {
        // 결측 판정은 정제 단계 몫 - 수집은 원본 보존이 목적이라 -9 / - 를 그대로 넘긴다.
        respondWith("""
            #START7777
            202609010700 295 93.0 -    4080 -9
            #7777END
            """);

        JSONObject item = new JSONObject(call().get(0));

        assertThat(item.getString("WW")).isEqualTo("-");
        assertThat(item.getString("IX")).isEqualTo("-9");
    }

    @Test
    void 한글_응답이_CP949로_디코딩된다() {
        respondWith("""
            #START7777
            #  기상청 지상관측 시간자료
            202609010700 108 96.0 01   580  1
            #7777END
            """);

        // 헤더의 한글이 깨지면 본문 파싱도 함께 깨진다 - 데이터가 온전히 나오는지로 확인.
        assertThat(call()).hasSize(1);
    }

    @Test
    void 컬럼_수가_예상과_다르면_수집을_실패시킨다() {
        respondWith("""
            #START7777
            202609010700 108 96.0 01 580 1 9999
            #7777END
            """);

        // 조용히 밀린 값이 들어가면 시정 자리에 습도가 저장되는 식으로 오염된다 - 실패가 낫다.
        assertThatThrownBy(this::call)
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("응답 컬럼 수가 예상과 다름");
    }

    @Test
    void 활용신청_안된_API의_JSON_오류응답을_수집_실패로_올린다() {
        // 200 OK에 JSON 오류 본문이 실려 온다(2026-09-03 stn_inf.php 실측) - 그냥 두면
        // 데이터 줄이 0건이라 "성공 0건"으로 조용히 넘어간다.
        respondWith("""
            {
              "result" : {
                "status" : 403,
                "message" : "활용신청이 필요한 API 입니다."
              }
            }
            """);

        assertThatThrownBy(this::call)
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("API 허브 오류 응답");
    }

    @Test
    void 인증키가_비어있으면_호출_전에_실패한다() {
        assertThatThrownBy(() ->
            client.call("소스", "API", ENDPOINT, "", Map.of(), FIELDS))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("인증키 설정이 비어있음");
    }

    @Test
    void 게이트웨이_504는_재시도해서_성공시킨다() {
        // 2026-09-21 실측: 허브가 30초를 붙잡고 504를 내던 시각의 바로 다음 시각엔 320ms에 성공했다.
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        server.expect(once(), requestTo(URL))
            .andRespond(withSuccess("202609010700 108 96.0 01   580  1\n".getBytes(CP949), MediaType.TEXT_PLAIN));

        assertThat(call()).hasSize(1);
        server.verify();
    }

    @Test
    void 재시도를_다_써도_실패하면_시도_횟수와_함께_수집_실패로_올린다() {
        for (int i = 0; i < 3; i++) {
            server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        }

        assertThatThrownBy(this::call)
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("API 호출 실패(3회 시도)")
            .hasMessageContaining("504");
        server.verify();
    }

    @Test
    void 요청_오류_4xx는_재시도하지_않는다() {
        // 인증키 오류 같은 실패는 되풀이해도 그대로다.
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(this::call)
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("요청 오류");
        server.verify(); // 호출이 정확히 1회였는지 - 재시도하지 않았음을 확인
    }

    private void respondWith(String body) {
        server.expect(requestTo(ENDPOINT + "?authKey=test-key&stn=0"))
            .andRespond(withSuccess(body.getBytes(CP949), MediaType.TEXT_PLAIN));
    }

    private List<String> call() {
        return client.call("소스", "API", ENDPOINT, "test-key", Map.of("stn", "0"), FIELDS);
    }
}
