package egovframework.external.exception;

/** 정제 단계 실패. 검증/정규화/매핑 실패 등. */
public class CleanseException extends PipelineException {

    public CleanseException(String sourceName, String apiName, String message) {
        super(sourceName, apiName, message);
    }

    public CleanseException(String sourceName, String apiName, String message, Throwable cause) {
        super(sourceName, apiName, message, cause);
    }

    @Override
    public String getStage() {
        return "CLEANSE";
    }
}
