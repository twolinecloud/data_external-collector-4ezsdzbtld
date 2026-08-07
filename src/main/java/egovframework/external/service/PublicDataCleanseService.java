package egovframework.external.service;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.cleanser.PublicDataCleanser;
import egovframework.external.publicdata.cleanser.PublicDataCleanserRegistry;
import egovframework.external.staging.RawStagingStore;
import egovframework.external.utility.PipelineLogUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * raw_staging의 COLLECTED 행을 꺼내 알맞은 {@link PublicDataCleanser}로 정제해서
 * CLEANSED/CLEANSE_FAILED로 전이시키는 오케스트레이션. {@code PublicDataCollectionAttemptService}와
 * 대칭 구조 - 스케줄(자동)과 컨트롤러(수동) 양쪽이 이 서비스 하나를 공유한다.
 *
 * <p>정제기를 못 찾은 경우도 예외로 전체를 죽이지 않고 그 행만 CLEANSE_FAILED로 남긴다
 * (수집 실패를 그 소스만 격리시키는 것과 동일한 원칙 - private-doc 27번 항목 참고).</p>
 */
@Service
@RequiredArgsConstructor
public class PublicDataCleanseService {

    private static final Logger logger = LogManager.getLogger(PublicDataCleanseService.class);
    private static final String STAGE = "CLEANSE";
    private static final int BATCH_SIZE = 100;

    private final RawStagingStore rawStagingStore;
    private final PublicDataCleanserRegistry cleanserRegistry;
    private final MeterRegistry meterRegistry;

    /** COLLECTED 상태 전체를 다 뺄 때까지 반복 처리. 각 행은 처리 후 상태가 바뀌므로 자연 종료됨. */
    public int cleanseAllPending() {
        int totalProcessed = 0;
        List<RawStagingDto> batch;
        while (!(batch = rawStagingStore.findByStatus("COLLECTED", BATCH_SIZE)).isEmpty()) {
            for (RawStagingDto dto : batch) {
                cleanseOne(dto);
                totalProcessed++;
            }
        }
        return totalProcessed;
    }

    private void cleanseOne(RawStagingDto dto) {
        String operationKey = dto.getOperationKey();
        Timer.Sample sample = Timer.start(meterRegistry);

        Optional<PublicDataCleanser> cleanser = cleanserRegistry.find(operationKey);
        if (cleanser.isEmpty()) {
            String message = "정제기 없음: operationKey=" + operationKey;
            fail(dto, sample, operationKey, message, null);
            return;
        }

        try {
            String cleansedPayload = cleanser.get().cleanse(dto.getRawPayload());
            long tookMs = stopTimer(sample, operationKey);
            rawStagingStore.markCleansed(dto.getId(), cleansedPayload, null);
            recordAttempt(operationKey, "SUCCESS");
            PipelineLogUtils.info(logger, STAGE, dto.getSourceName(), dto.getApiName(),
                "raw_staging id=" + dto.getId() + " 정제 완료 (" + tookMs + "ms)");
        } catch (CleanseException e) {
            fail(dto, sample, operationKey, e.getMessage(), e);
        } catch (Exception e) {
            fail(dto, sample, operationKey, "UNHANDLED EXCEPTION: " + e.getClass().getName() + " - " + e.getMessage(), e);
        }
    }

    private void fail(RawStagingDto dto, Timer.Sample sample, String operationKey, String message, Throwable cause) {
        long tookMs = stopTimer(sample, operationKey);
        rawStagingStore.markCleanseFailed(dto.getId(), message, null);
        recordAttempt(operationKey, "FAILED");
        if (cause != null) {
            PipelineLogUtils.error(logger, STAGE, dto.getSourceName(), dto.getApiName(),
                "raw_staging id=" + dto.getId() + " - " + message + " (" + tookMs + "ms)", cause);
        } else {
            PipelineLogUtils.warn(logger, STAGE, dto.getSourceName(), dto.getApiName(),
                "raw_staging id=" + dto.getId() + " - " + message + " (" + tookMs + "ms)");
        }
    }

    private long stopTimer(Timer.Sample sample, String operationKey) {
        long tookNanos = sample.stop(Timer.builder("public_data_cleanse_duration_seconds")
            .description("raw_staging 정제 소요시간")
            .tag("operationKey", operationKey == null ? "unknown" : operationKey)
            .register(meterRegistry));
        return TimeUnit.NANOSECONDS.toMillis(tookNanos);
    }

    private void recordAttempt(String operationKey, String status) {
        Counter.builder("public_data_cleanse_attempts_total")
            .description("정제 시도 건수")
            .tag("operationKey", operationKey == null ? "unknown" : operationKey)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }
}
