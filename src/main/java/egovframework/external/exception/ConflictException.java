package egovframework.external.exception;

import egovframework.external.response.ResponseCode;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 이미 같은 작업이 진행 중이라 요청을 받을 수 없을 때(HTTP 409). */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class ConflictException extends ExceptionBase {
    public ConflictException(Logger l) {
        logger = l;
        errorCode = ResponseCode.CONFLICT;
    }
    public ConflictException(Logger l, @Nullable String message) {
        logger = l;
        errorCode = ResponseCode.CONFLICT;
        this.additionalMessage = message;
    }

    @Override
    public int getStatusCode() {
        return HttpStatus.CONFLICT.value();
    }
}
