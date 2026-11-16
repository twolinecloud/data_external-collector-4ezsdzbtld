package egovframework.external.publicdata.source;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectExternalApiAdapterTest {

    private static ExternalApiCall call(ExternalApiCall.Channel channel, String keyParam, String key,
                                        Map<String, String> params, boolean encode) {
        return new ExternalApiCall(channel, "출처", "API", "https://example.invalid/op", keyParam, key, params, encode);
    }

    private static Map<String, String> params(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void 인증키는_이미_인코딩된_값을_그대로_붙이고_파라미터는_원문_순서대로_붙인다() {
        URI uri = DirectExternalApiAdapter.uriOf(call(ExternalApiCall.Channel.DEFAULT, "serviceKey",
            "abc%2Bdef%3D%3D", params("pageNo", "1", "numOfRows", "100", "base_time", "0600"), false));

        assertEquals("https://example.invalid/op?serviceKey=abc%2Bdef%3D%3D&pageNo=1&numOfRows=100&base_time=0600",
            uri.toString());
    }

    @Test
    void encodeParams이면_한글_값만_UTF8로_인코딩한다() {
        URI uri = DirectExternalApiAdapter.uriOf(call(ExternalApiCall.Channel.DEFAULT, "serviceKey",
            "k%2F", params("sidoName", "전국", "pageNo", "1"), true));

        assertEquals("https://example.invalid/op?serviceKey=k%2F&sidoName=%EC%A0%84%EA%B5%AD&pageNo=1",
            uri.toString());
    }

    @Test
    void API허브는_authKey_이름으로_붙인다() {
        URI uri = DirectExternalApiAdapter.uriOf(call(ExternalApiCall.Channel.API_HUB, "authKey",
            "hubkey", params("tm", "202611160000"), false));

        assertEquals("https://example.invalid/op?authKey=hubkey&tm=202611160000", uri.toString());
    }

    @Test
    void 잘못된_URI는_IllegalArgumentException으로_올린다() {
        assertThrows(IllegalArgumentException.class, () -> DirectExternalApiAdapter.uriOf(
            call(ExternalApiCall.Channel.DEFAULT, "serviceKey", "k", params("q", "공 백"), false)));
    }

    @Test
    void 채널에_맞는_RestTemplate으로_호출하고_텍스트와_바이트를_구분한다() {
        RestTemplate defaults = mock(RestTemplate.class);
        RestTemplate hub = mock(RestTemplate.class);
        byte[] raw = {1, 2, 3};
        when(defaults.getForObject(any(URI.class), eq(String.class))).thenReturn("text");
        when(hub.getForObject(any(URI.class), eq(byte[].class))).thenReturn(raw);
        DirectExternalApiAdapter adapter = new DirectExternalApiAdapter(defaults, hub);

        assertEquals("text", adapter.getText(call(ExternalApiCall.Channel.DEFAULT, "serviceKey", "k", params(), false)));
        assertSame(raw, adapter.getBytes(call(ExternalApiCall.Channel.API_HUB, "authKey", "k", params(), false)));
        verify(defaults).getForObject(any(URI.class), eq(String.class));
        verify(hub).getForObject(any(URI.class), eq(byte[].class));
    }
}
