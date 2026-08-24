package egovframework.external.exception;

/** 수집 단계 실패. 소스 API 호출 실패, 응답 파싱 실패 등. */
public class CollectException extends PipelineException {

    public CollectException(String sourceName, String apiName, String message) {
        super(sourceName, apiName, message);
    }

    public CollectException(String sourceName, String apiName, String message, Throwable cause) {
        super(sourceName, apiName, message, cause);
    }

    @Override
    public String getStage() {
        return "COLLECT";
    }
}
