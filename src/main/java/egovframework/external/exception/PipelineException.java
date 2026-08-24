package egovframework.external.exception;

/**
 * 파이프라인(수집/정제/적재) 처리 중 발생하는 예외의 최상위 클래스.
 *
 * <p>기존 {@link egovframework.external.exception.ExceptionBase}는
 * {@code @ControllerAdvice}(서블릿 요청 파이프라인) 전용이라 {@code @Scheduled}
 * 메서드 안에서 던지면 절대 잡히지 않는다. 그래서 배치 컨텍스트에서는 이 클래스를
 * 최상위로 하는 별도 예외 계층을 사용한다. HTTP status 개념이 없다.</p>
 */
public abstract class PipelineException extends RuntimeException {

    private final String sourceName;
    private final String apiName;

    protected PipelineException(String sourceName, String apiName, String message) {
        super(message);
        this.sourceName = sourceName;
        this.apiName = apiName;
    }

    protected PipelineException(String sourceName, String apiName, String message, Throwable cause) {
        super(message, cause);
        this.sourceName = sourceName;
        this.apiName = apiName;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getApiName() {
        return apiName;
    }

    public abstract String getStage();
}
