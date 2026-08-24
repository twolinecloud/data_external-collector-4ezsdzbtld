package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link VWorldGeocoder} 응답 파싱 검증. 실제 응답 형태는 2026-08-24 실 서비스키로 확인한
 * 값을 그대로 고정({@code status=OK}/{@code NOT_FOUND}, {@code result.point.x/y},
 * {@code refined.structure.level1/level2}).
 */
@ExtendWith(MockitoExtension.class)
class VWorldGeocoderTest {

    @Mock
    private RestTemplate restTemplate;

    private VWorldGeocoder geocoder() {
        return new VWorldGeocoder(restTemplate, "http://api.vworld.kr/req/address", "test-key");
    }

    @Test
    void 성공_응답에서_위경도와_시도_시군구를_뽑는다() {
        String body = """
            {"response":{"status":"OK",
             "refined":{"structure":{"level1":"경기도","level2":"과천시"}},
             "result":{"point":{"x":"126.98350683980327","y":"37.42668762186121"}}}}
            """;
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(body);

        GeocodeResult result = geocoder().geocode("경기도 과천시 관문로 47");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.lat()).isCloseTo(37.42668762186121, within(0.000001));
        assertThat(result.lon()).isCloseTo(126.98350683980327, within(0.000001));
        assertThat(result.sidoNm()).isEqualTo("경기도");
        assertThat(result.sigunguNm()).isEqualTo("과천시");
    }

    @Test
    void NOT_FOUND_응답은_notFound로_분류한다() {
        String body = """
            {"response":{"status":"NOT_FOUND","record":{"total":"0"}}}
            """;
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(body);

        GeocodeResult result = geocoder().geocode("충청북도 청주시 서원구 청남로0000번길 00");

        assertThat(result.status()).isEqualTo(GeocodeResult.NOT_FOUND);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void 호출_자체가_예외를_던지면_failed로_처리하고_전파하지_않는다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenThrow(new RestClientException("connection refused"));

        GeocodeResult result = geocoder().geocode("아무 주소");

        assertThat(result.status()).isEqualTo(GeocodeResult.FAILED);
    }

    @Test
    void 서비스키가_없으면_호출_자체를_안_하고_failed를_반환한다() {
        VWorldGeocoder noKey = new VWorldGeocoder(restTemplate, "http://api.vworld.kr/req/address", "");

        GeocodeResult result = noKey.geocode("아무 주소");

        assertThat(result.status()).isEqualTo(GeocodeResult.FAILED);
    }
}
