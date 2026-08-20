package egovframework.external.publicdata.collector;

/**
 * safetydata.go.kr이 JSON 구조는 정상으로 주면서 header.resultCode로 실패를 알린 경우
 * (HTTP는 200이라 {@code RestClientException}으로는 안 잡힘).
 *
 * <p>확인된 코드: {@code 00}=정상, {@code 30}=등록되지 않은 서비스키.</p>
 */
public class SafetyDataApiException extends RuntimeException {

    private final String resultCode;

    public SafetyDataApiException(String resultCode, String resultMsg) {
        super("safetydata API 오류 [" + resultCode + "] " + resultMsg);
        this.resultCode = resultCode;
    }

    public String resultCode() {
        return resultCode;
    }
}
