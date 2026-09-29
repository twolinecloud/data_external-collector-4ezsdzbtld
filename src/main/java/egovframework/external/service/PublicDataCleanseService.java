package egovframework.external.service;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.CleanseException;
import egovframework.external.model.CleanseResult;
import egovframework.external.publicdata.cleanser.CleansedJsonDropWriter;
import egovframework.external.publicdata.cleanser.JsonStructureDriftDetector;
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
 * (수집 실패를 그 소스만 격리시키는 것과 동일한 원칙).</p>
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
    private final CleansedJsonDropWriter jsonDropWriter;
    private final JsonStructureDriftDetector structureDriftDetector;

    /**
     * 정제 대기(COLLECTED) 행이 있는 수집 실행(collectRunId) 목록, 오래된 순 - 정제는 수집 실행
     * 1회 단위로 처리한다(2026-09-29, {@code PublicDataPipelineRunner} 참고).
     */
    public List<String> pendingRunIds() {
        return rawStagingStore.pendingRunIds("COLLECTED");
    }

    /**
     * 그 수집 실행의 대기 행 하나를 소모하지 않고 들여다본다 - 배치를 시작하기 전에 카테고리와
     * origin execId를 알아야 해서. {@link RawStagingStore#findByStatusAndRunId}는 상태를 바꾸지
     * 않는 순수 조회다.
     */
    public Optional<RawStagingDto> peekRun(String collectRunId) {
        List<RawStagingDto> peek = rawStagingStore.findByStatusAndRunId("COLLECTED", 1, collectRunId);
        return peek.isEmpty() ? Optional.empty() : Optional.of(peek.get(0));
    }

    /**
     * 그 수집 실행의 COLLECTED 행을 다 뺄 때까지 반복 처리. 각 행은 처리 후 상태가 바뀌므로 자연
     * 종료됨.
     *
     * @return 총/성공/실패 건수 - 로그 컬렉터 배치 종료 보고용
     */
    public CleanseResult cleanseRun(String collectRunId) {
        int totalProcessed = 0;
        int successCount = 0;
        int failCount = 0;
        List<RawStagingDto> batch;
        while (!(batch = rawStagingStore.findByStatusAndRunId("COLLECTED", BATCH_SIZE, collectRunId)).isEmpty()) {
            for (RawStagingDto dto : batch) {
                boolean success = cleanseOne(dto);
                totalProcessed++;
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }
        }
        return new CleanseResult(totalProcessed, successCount, failCount);
    }

    /** @return 정제 성공 여부 */
    private boolean cleanseOne(RawStagingDto dto) {
        String operationKey = dto.getOperationKey();
        Timer.Sample sample = Timer.start(meterRegistry);

        Optional<PublicDataCleanser> cleanser = cleanserRegistry.find(operationKey);
        if (cleanser.isEmpty()) {
            String message = "정제기 없음: operationKey=" + operationKey;
            fail(dto, sample, operationKey, message, null);
            return false;
        }

        structureDriftDetector.check(cleanser.get(), operationKey, dto.getRawPayload());

        try {
            String cleansedPayload = cleanser.get().cleanse(dto.getRawPayload());
            long tookMs = stopTimer(sample, operationKey);
            rawStagingStore.markCleansed(dto.getId(), cleansedPayload, null);
            jsonDropWriter.write(dto.getCollectorKey(), cleansedPayload);
            recordAttempt(operationKey, "SUCCESS");
            PipelineLogUtils.info(logger, STAGE, dto.getSourceName(), dto.getApiName(),
                "raw_staging id=" + dto.getId() + " 정제 완료 (" + tookMs + "ms, runId="
                    + dto.getCollectRunId() + ", execId=" + dto.getOriginExecId() + ")");
            return true;
        } catch (CleanseException e) {
            fail(dto, sample, operationKey, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            fail(dto, sample, operationKey, "UNHANDLED EXCEPTION: " + e.getClass().getName() + " - " + e.getMessage(), e);
            return false;
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
