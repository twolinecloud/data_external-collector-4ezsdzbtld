package egovframework.external.publicdata.source;

/**
 * 외부 공공 API "호출 1회"를 수행하는 포트. 수집기/클라이언트는 이 포트로 원천 응답 원문만 받는다.
 * 페이지 순회, 응답 파싱, 오류 분류, 재시도 정책은 호출하는 쪽(KmaApiClient 등)에 남는다.
 *
 * <p>구현체: {@link DirectExternalApiAdapter}(원천에 직접 연결). 메타빌드 ESB 경유 구현체는 ESB 스펙이
 * 확정된 뒤 이 인터페이스를 구현해 추가한다 - 호출하는 쪽은 바뀌지 않는다.</p>
 *
 * <p><b>예외 계약</b>(직접 연결 시 기존 동작과 동일): 연결·타임아웃·HTTP 오류는 Spring
 * {@code RestClientException} 계열(4xx는 {@code HttpClientErrorException}), 잘못된 URI는
 * {@code IllegalArgumentException}으로 던진다. 호출하는 쪽이 이 구분(재시도 여부)을 쓰므로 다른 구현체도
 * 같은 계열로 변환해야 한다.</p>
 */
public interface ExternalApiPort {

    /** 응답 본문을 문자열로 받는다(JSON 등 UTF-8 텍스트). */
    String getText(ExternalApiCall call);

    /** 응답 본문을 바이트 그대로 받는다(CP949 텍스트처럼 호출하는 쪽이 직접 디코딩하는 경우). */
    byte[] getBytes(ExternalApiCall call);
}
