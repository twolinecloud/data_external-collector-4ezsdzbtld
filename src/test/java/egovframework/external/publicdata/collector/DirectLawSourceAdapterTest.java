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

        String result = adapter("https://example.invalid", "test").fetchLawBody("소스", "API", "형법");

        assertThat(result).isEqualTo(body);
    }

    @Test
    void 요청_URL에_OC와_법령명이_포함된다() throws CollectException {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"법령\":{}}");

        adapter("https://example.invalid", "test-oc").fetchLawBody("소스", "API", "형법");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().toString())
            .contains("OC=test-oc")
            .contains("LM=" + java.net.URLEncoder.encode("형법", java.nio.charset.StandardCharsets.UTF_8))
            .contains("target=eflaw");
    }

    @Test
    void 법령명이_공백을_포함해도_URL_인코딩된다() throws CollectException {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"법령\":{}}");

        adapter("https://example.invalid", "test-oc").fetchLawBody("소스", "API", "형의 집행 및 수용자의 처우에 관한 법률");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().toString()).doesNotContain(" ");
    }

    @Test
    void 행정규칙명의_가운뎃점류_유사문자는_표준형으로_정규화해서_보낸다() throws CollectException {
        // 2026-09-08 실제 사례 - 한글 자모 아래아(ㆍ)가 섞여 있으면 행정규칙 조회가
        // "일치하는 행정규칙이 없습니다"로 실패했다(LawNameConfusablesTest 참고).
        // 어댑터가 API로 나가기 전에 표준 가운뎃점(·)으로 바꿔 보내는지 확인한다.
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"AdmRulService\":{}}");

        adapter("https://example.invalid", "test-oc")
            .fetchAdminRuleBody("소스", "API", "국가공무원 복무ㆍ징계 관련 예규");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));
        String expectedLm = java.net.URLEncoder.encode("국가공무원 복무·징계 관련 예규", java.nio.charset.StandardCharsets.UTF_8);
        assertThat(uriCaptor.getValue().toString()).contains("LM=" + expectedLm);
    }

    @Test
    void 법령명의_표준_가운뎃점은_한글_자모_아래아로_정규화해서_보낸다() throws CollectException {
        // 2026-09-09 실측 - eflaw는 admrul과 반대로 유사문자(ㆍ)를 줘야 매칭되고, 표준
        // 가운뎃점(·)을 주면 JSON이 아닌 HTML 에러 페이지가 온다(moleg-eflaw-dot-char-regression
        // 메모 참고). 어댑터가 API로 나가기 전에 아래아(ㆍ)로 바꿔 보내는지 확인한다.
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"법령\":{}}");

        adapter("https://example.invalid", "test-oc")
            .fetchLawBody("소스", "API", "인지 첩부·첨부 및 공탁 제공에 관한 특례법");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));
        String expectedLm = java.net.URLEncoder.encode("인지 첩부ㆍ첨부 및 공탁 제공에 관한 특례법", java.nio.charset.StandardCharsets.UTF_8);
        assertThat(uriCaptor.getValue().toString()).contains("LM=" + expectedLm);
    }

    @Test
    void 일치하는_법령이_없으면_Law_키의_에러메시지를_담아_CollectException을_던진다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"Law\": \"일치하는 법령이 없습니다.  법령명을 확인하여 주십시오.\"}");

        assertThatThrownBy(() -> adapter("https://example.invalid", "test").fetchLawBody("소스", "API", "존재하지않는법"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("일치하는 법령이 없습니다");
    }

    @Test
    void 엔드포인트가_비어있으면_호출_없이_CollectException을_던진다() {
        assertThatThrownBy(() -> adapter("", "test").fetchLawBody("소스", "API", "형법"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("엔드포인트/OC");
    }

    @Test
    void OC가_비어있으면_호출_없이_CollectException을_던진다() {
        assertThatThrownBy(() -> adapter("https://example.invalid", "").fetchLawBody("소스", "API", "형법"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("엔드포인트/OC");
    }

    @Test
    void 행정규칙_성공_응답이면_원문을_그대로_반환한다() throws CollectException {
        String body = "{\"AdmRulService\":{\"행정규칙기본정보\":{}}}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(body);

        String result = adapter("https://example.invalid", "test").fetchAdminRuleBody("소스", "API", "가석방 업무지침");

        assertThat(result).isEqualTo(body);
    }

    @Test
    void 행정규칙_요청_URL에_target이_admrul이다() throws CollectException {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"AdmRulService\":{}}");

        adapter("https://example.invalid", "test-oc").fetchAdminRuleBody("소스", "API", "가석방 업무지침");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uriCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().toString())
            .contains("OC=test-oc")
            .contains("LM=" + java.net.URLEncoder.encode("가석방 업무지침", java.nio.charset.StandardCharsets.UTF_8))
            .contains("target=admrul");
    }

    @Test
    void AdmRulService_키가_없으면_원문을_담아_CollectException을_던진다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("{\"OtherKey\": \"무언가\"}");

        assertThatThrownBy(() -> adapter("https://example.invalid", "test").fetchAdminRuleBody("소스", "API", "존재하지않는행정규칙"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("행정규칙 조회 실패");
    }

    @Test
    void 응답이_JSON_형식이_아니면_형식오류_CollectException으로_감싼다() {
        // target=eflaw는 법령명이 전혀 매칭 안 될 때 "Law" 키 JSON 대신 HTML 에러 페이지를
        // 반환하는 경우도 실측 확인됨(2026-08-28) - 이 경로가 그 경우도 커버한다.
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenReturn("이건 JSON이 아님");

        assertThatThrownBy(() -> adapter("https://example.invalid", "test").fetchLawBody("소스", "API", "형법"))
            .isInstanceOf(CollectException.class)
            .hasMessageContaining("응답 파싱 실패");
    }
}
