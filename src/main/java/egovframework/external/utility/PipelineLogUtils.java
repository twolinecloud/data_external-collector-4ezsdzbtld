package egovframework.external.utility;

import org.apache.logging.log4j.Logger;

/**
 * 파이프라인(수집/정제/적재) 전용 로깅 유틸.
 *
 * <p>기존 {@link egovframework.external.utility.LogUtils}는 {@code HttpServletRequest}에
 * 결합돼 있어 {@code @Scheduled} 컨텍스트에서 재사용할 수 없어 별도로 둔다.
 * jobId/stage/source를 구조화된 필드로 남긴다.</p>
 */
public class PipelineLogUtils {

    public static void debug(Logger logger, String stage, String source, String api, String message) {
        logger.debug("[{}] source={} api={} - {}", stage, source, api, message);
    }

    public static void info(Logger logger, String stage, String source, String api, String message) {
        logger.info("[{}] source={} api={} - {}", stage, source, api, message);
    }

    public static void warn(Logger logger, String stage, String source, String api, String message) {
        logger.warn("[{}] source={} api={} - {}", stage, source, api, message);
    }

    public static void error(Logger logger, String stage, String source, String api, String message, Throwable cause) {
        logger.error("[{}] source={} api={} - {}", stage, source, api, message, cause);
    }
}
