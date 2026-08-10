package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DirectLawSourceAdapter}의 성공/실패 응답 처리 검증. law.go.kr은 기상청과 달리
 * resultCode/resultMsg 봉투가 없고, 성공은 최상위 키 "법령", 실패는 "Law" 키에 에러 메시지
 * 문자열만 온다(private-doc 31번 항목에서 실 API로 확인).
 */
@ExtendWith(MockitoExtension.class)
class DirectLawSourceAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private DirectLawSourceAdapter adapter(String endpoint, String oc) {
        return new DirectLawSourceAdapter(restTemplate, endpoint, oc);
    }

    @Test
    void 성공_응답이면_원문을_그대로_반환한다() throws CollectException {
        String body = "{\"법령\":{\"법령명\":\"형법\"}}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(body);

        String result = adapter("https://example.invalid", "test").fetchLawBody("소스", "API", "284025");

        assertThat(result).isEqualTo(body);
    }

    @Test
    void 요청_URL에_OC와_MST가_포함된다() throws CollectException {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"법령\":{}}");

        adapter("https://example.invalid", "test-oc").fetchLawBody("소스", "API", "284025");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().toString())
            .contains("OC=test-oc")
            .contains("MST=284025")
            .contains("target=law");
    }

    @Test
    void 존재하지_않는_MST면_Law_키의_에러메시지를_담아_CollectException을_던진다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"Law\": \"일치하는 법령이 없습니다.  법령명을 확인하여 주십시오.\"}");

        assertThatThrownBy(() -> adapter("https://example.invalid", "test").fetchLawBody("소스", "API", "99999"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("일치하는 법령이 없습니다");
    }

    @Test
    void 엔드포인트가_비어있으면_호출_없이_CollectException을_던진다() {
        assertThatThrownBy(() -> adapter("", "test").fetchLawBody("소스", "API", "284025"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("엔드포인트/OC");
    }

    @Test
    void OC가_비어있으면_호출_없이_CollectException을_던진다() {
        assertThatThrownBy(() -> adapter("https://example.invalid", "").fetchLawBody("소스", "API", "284025"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("엔드포인트/OC");
    }

    @Test
    void 응답이_JSON_형식이_아니면_형식오류_CollectException으로_감싼다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("이건 JSON이 아님");

        assertThatThrownBy(() -> adapter("https://example.invalid", "test").fetchLawBody("소스", "API", "284025"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("응답 파싱 실패");
    }
}
