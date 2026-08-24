package egovframework.external.publicdata.collector;

/**
 * 공공데이터포털 응답의 {@code resultCode}가 정상(00)이 아닌 경우.
 *
 * <p>JSON 구조 자체가 깨진 "파싱 실패"와는 다른 문제 - API가 정상적으로 응답했지만
 * 업무적으로 실패(쿼터 초과, 인증 만료, 잘못된 파라미터 등)를 반환한 경우다. 이걸 구분해야
 * 실패 로그를 보고 "코드가 이상한가" vs "쿼터를 초과했나"를 바로 판단할 수 있다.
 * (에러코드 표는 weather-api.docx / data.go.kr 공개 스펙 기준. 두 API의 코드 체계가
 * 완전히 같진 않지만 겹치는 부분이 많아 통합 조회표로 관리)</p>
 */
public class KmaApiException extends RuntimeException {

    private final String resultCode;
    private final String resultMsg;

    public KmaApiException(String resultCode, String resultMsg) {
        super("resultCode=" + resultCode + " (" + describe(resultCode) + ") resultMsg=" + resultMsg);
        this.resultCode = resultCode;
        this.resultMsg = resultMsg;
    }

    public String getResultCode() {
        return resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    private static String describe(String code) {
        return switch (code) {
            case "01" -> "APPLICATION_ERROR-어플리케이션 에러";
            case "02" -> "DB_ERROR-데이터베이스 에러";
            case "03" -> "NODATA_ERROR-데이터없음";
            case "04" -> "HTTP_ERROR";
            case "05" -> "SERVICETIME_OUT-서비스 연결실패";
            case "10" -> "INVALID_REQUEST_PARAMETER_ERROR-잘못된 요청 파라미터";
            case "11" -> "NO_MANDATORY_REQUEST_PARAMETERS_ERROR-필수 파라미터 누락";
            case "12" -> "NO_OPENAPI_SERVICE_ERROR-서비스 없음/폐기";
            case "20" -> "SERVICE_ACCESS_DENIED_ERROR-서비스 접근거부 (서비스키 미신청 등)";
            case "21" -> "TEMPORARILY_DISABLE_THE_SERVICEKEY_ERROR-일시적으로 사용불가한 서비스키";
            case "22" -> "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR-일일 호출 한도 초과";
            case "23" -> "LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR-초당 호출 한도 초과";
            case "29" -> "BLACKLIST_IP_ACCESS_ERROR-차단된 IP";
            case "30" -> "SERVICE_KEY_IS_NOT_REGISTERED_ERROR-미등록 서비스키";
            case "31" -> "DEADLINE_HAS_EXPIRED_ERROR-서비스키 기한만료";
            case "32" -> "UNREGISTERED_IP_ERROR-미등록 IP";
            case "33" -> "UNSIGNED_CALL_ERROR-서명되지 않은 호출";
            case "99" -> "UNKNOWN_ERROR";
            default -> "알 수 없는 코드";
        };
    }
}
