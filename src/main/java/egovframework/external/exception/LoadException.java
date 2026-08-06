package egovframework.external.exception;

/** 적재 단계 실패. admin-db 최종 테이블 upsert 실패 등. */
public class LoadException extends PipelineException {

    public LoadException(String sourceName, String apiName, String message) {
        super(sourceName, apiName, message);
    }

    public LoadException(String sourceName, String apiName, String message, Throwable cause) {
        super(sourceName, apiName, message, cause);
    }

    @Override
    public String getStage() {
        return "LOAD";
    }
}
