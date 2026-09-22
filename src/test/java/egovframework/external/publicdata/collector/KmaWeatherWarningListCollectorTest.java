// src/test/java/egovframework/external/publicdata/collector/KmaWeatherWarningListCollectorTest.java
package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KmaWeatherWarningListCollectorTest {

    @Mock
    private KmaApiClient apiClient;

    private static final String ENDPOINT = "https://apis.data.go.kr/1360000/WthrWrnInfoService";
    private static final String SERVICE_KEY = "test-key";

    @Test
    void 목록조회_결과와_통보문상세를_결합한다() throws CollectException {
        KmaWeatherWarningListCollector collector =
            new KmaWeatherWarningListCollector(apiClient, ENDPOINT, SERVICE_KEY);

        // 1. getWthrWrnList 응답 목킹
        String listItem = new JSONObject()
            .put("stnId", "108")
            .put("title", "[특보] 제09-102호 : 2026.09.22.06:00 / 풍랑주의보 발표 (*)")
            .put("tmFc", 202609220600L)
            .put("tmSeq", 102)
            .toString();

        when(apiClient.call(eq("공공데이터포털 (기상청 기상특보)"), eq("기상특보목록조회"), eq(ENDPOINT + "/getWthrWrnList"), eq(SERVICE_KEY), any()))
            .thenReturn(List.of(listItem));

        // 2. getWthrWrnMsg 응답 목킹
        String msgItem = new JSONObject()
            .put("stnId", "108")
            .put("tmFc", 202609220600L)
            .put("tmSeq", 102)
            .put("t1", "풍랑주의보 발표")
            .put("t2", "(1) 풍랑주의보 발표 : 제주도남쪽바깥먼바다")
            .put("t6", "o 풍랑주의보 : 제주도남쪽바깥먼바다")
            .put("warFc", "1 ")
            .toString();

        when(apiClient.call(eq("공공데이터포털 (기상청 기상특보)"), eq("기상특보통보문조회"), eq(ENDPOINT + "/getWthrWrnMsg"), eq(SERVICE_KEY), any()))
            .thenReturn(List.of(msgItem));

        List<String> result = collector.collect();

        assertThat(result).hasSize(1);
        JSONObject combined = new JSONObject(result.get(0));
        assertThat(combined.getString("stnId")).isEqualTo("108");
        assertThat(combined.getInt("tmSeq")).isEqualTo(102);
        assertThat(combined.getString("title")).contains("풍랑주의보 발표");
        assertThat(combined.getString("t1")).isEqualTo("풍랑주의보 발표");
        assertThat(combined.getString("t2")).contains("제주도남쪽바깥먼바다");
        assertThat(combined.getString("t6")).contains("제주도남쪽바깥먼바다");
        assertThat(combined.getString("warFc")).isEqualTo("1 ");
    }

    @Test
    void 통보문_상세_조회_실패시에도_목록_기본정보로_안전하게_반환한다() throws CollectException {
        KmaWeatherWarningListCollector collector =
            new KmaWeatherWarningListCollector(apiClient, ENDPOINT, SERVICE_KEY);

        String listItem = new JSONObject()
            .put("stnId", "108")
            .put("title", "호우주의보")
            .put("tmFc", 202609220600L)
            .put("tmSeq", 1)
            .toString();

        when(apiClient.call(eq("공공데이터포털 (기상청 기상특보)"), eq("기상특보목록조회"), eq(ENDPOINT + "/getWthrWrnList"), eq(SERVICE_KEY), any()))
            .thenReturn(List.of(listItem));

        // getWthrWrnMsg 호출 시 예외 발생
        when(apiClient.call(eq("공공데이터포털 (기상청 기상특보)"), eq("기상특보통보문조회"), eq(ENDPOINT + "/getWthrWrnMsg"), eq(SERVICE_KEY), any()))
            .thenThrow(new CollectException("공공데이터포털", "기상특보통보문조회", "통신 실패"));

        List<String> result = collector.collect();

        assertThat(result).hasSize(1);
        JSONObject item = new JSONObject(result.get(0));
        assertThat(item.getString("stnId")).isEqualTo("108");
        assertThat(item.getInt("tmSeq")).isEqualTo(1);
        assertThat(item.getString("title")).isEqualTo("호우주의보");
        assertThat(item.has("t2")).isFalse();
    }
}
