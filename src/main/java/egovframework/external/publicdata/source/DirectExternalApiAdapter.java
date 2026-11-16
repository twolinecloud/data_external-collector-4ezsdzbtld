package egovframework.external.publicdata.source;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 원천 API에 직접 연결하는 {@link ExternalApiPort} 구현. 각 클라이언트가 예전에 직접 하던 URL 조립과
 * {@code RestTemplate.getForObject(URI, ...)} 호출을 그대로 옮겼다 - 동작이 바뀌면 안 된다.
 *
 * <p>인증 키는 이미 URL-encode된 문자열이라 {@code URI.create}로 직접 만든다. String 오버로드를 쓰면
 * URI 템플릿으로 취급해 키를 한 번 더 인코딩해 깨뜨린다(공공데이터포털 연동의 흔한 실수).</p>
 */
@Component
public class DirectExternalApiAdapter implements ExternalApiPort {

    private final RestTemplate defaultRestTemplate;
    private final RestTemplate apiHubRestTemplate;

    @Autowired
    public DirectExternalApiAdapter(@Qualifier("restTemplate") RestTemplate defaultRestTemplate,
                                    @Qualifier("kmaApiHubRestTemplate") RestTemplate apiHubRestTemplate) {
        this.defaultRestTemplate = defaultRestTemplate;
        this.apiHubRestTemplate = apiHubRestTemplate;
    }

    /** 하나의 RestTemplate으로 두 채널을 모두 처리하는 구성 - 테스트와 단일 설정용. */
    public DirectExternalApiAdapter(RestTemplate restTemplate) {
        this(restTemplate, restTemplate);
    }

    @Override
    public String getText(ExternalApiCall call) {
        return restTemplateOf(call).getForObject(uriOf(call), String.class);
    }

    @Override
    public byte[] getBytes(ExternalApiCall call) {
        return restTemplateOf(call).getForObject(uriOf(call), byte[].class);
    }

    private RestTemplate restTemplateOf(ExternalApiCall call) {
        return call.channel() == ExternalApiCall.Channel.API_HUB ? apiHubRestTemplate : defaultRestTemplate;
    }

    /** @throws IllegalArgumentException URI 형식이 잘못된 경우(호출하는 쪽이 요청 오류로 분류) */
    static URI uriOf(ExternalApiCall call) {
        StringBuilder url = new StringBuilder(call.endpoint()).append('?')
            .append(call.keyParam()).append('=').append(call.key());
        call.params().forEach((name, value) -> {
            url.append('&').append(name).append('=');
            url.append(call.encodeParams() ? URLEncoder.encode(value, StandardCharsets.UTF_8) : value);
        });
        return URI.create(url.toString());
    }
}
