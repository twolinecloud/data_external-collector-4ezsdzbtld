package egovframework.external.service;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;
import egovframework.external.model.LoadResult;
import egovframework.external.publicdata.loader.PublicDataLoader;
import egovframework.external.publicdata.loader.PublicDataLoaderRegistry;
import egovframework.external.staging.RawStagingStore;
import egovframework.external.utility.PipelineLogUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * raw_staging의 CLEANSED 행을 꺼내 알맞은 {@link PublicDataLoader}로 admin-db 최종 테이블에
 * upsert해서 LOADED/LOAD_FAILED로 전이시키는 오케스트레이션. {@code PublicDataCleanseService}와
 * 대칭 구조.
 *
 * <p>{@code public-data.load.enabled=false}(기본값)면 raw_staging을 아예 건드리지 않고
 * 바로 빈 결과를 반환한다 - 스케줄/수동 트리거 어느 경로로 호출되든 이 가드 하나로 전부
 * 막힌다(꺼진 상태에서 CLEANSED 행이 "로더 없음"으로 잘못 LOAD_FAILED 처리되는 것 방지).</p>
 */
@Service
public class PublicDataLoadService {

    private static final Logger logger = LogManager.getLogger(PublicDataLoadService.class);
    private static final String STAGE = "LOAD";
    private static final int BATCH_SIZE = 100;

    private final RawStagingStore rawStagingStore;
    private final PublicDataLoaderRegistry loaderRegistry;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;

    public PublicDataLoadService(
        RawStagingStore rawStagingStore,
        PublicDataLoaderRegistry loaderRegistry,
        MeterRegistry meterRegistry,
        @Value("${public-data.load.enabled:false}") boolean enabled
    ) {
        this.rawStagingStore = rawStagingStore;
        this.loaderRegistry = loaderRegistry;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
    }

    /**
     * CLEANSED 상태 전체를 다 뺄 때까지 반복 처리.
     *
     * @return 총/성공/실패 건수 (enabled=false면 전부 0)
     */
    public LoadResult loadAllPending() {
        if (!enabled) {
            return new LoadResult(0, 0, 0);
        }

        int totalProcessed = 0;
        int successCount = 0;
        int failCount = 0;
        List<RawStagingDto> batch;
        while (!(batch = rawStagingStore.findByStatus("CLEANSED", BATCH_SIZE)).isEmpty()) {
            for (RawStagingDto dto : batch) {
                boolean success = loadOne(dto);
                totalProcessed++;
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                }
            }
        }
        return new LoadResult(totalProcessed, successCount, failCount);
    }

    /** @return 적재 성공 여부 */
    private boolean loadOne(RawStagingDto dto) {
        String operationKey = dto.getOperationKey();
        Timer.Sample sample = Timer.start(meterRegistry);

        Optional<PublicDataLoader> loader = loaderRegistry.find(operationKey);
        if (loader.isEmpty()) {
            String message = "적재기 없음: operationKey=" + operationKey;
            fail(dto, sample, operationKey, message, null);
            return false;
        }

        try {
            loader.get().load(dto);
            long tookMs = stopTimer(sample, operationKey);
            rawStagingStore.markLoaded(dto.getId());
            recordAttempt(operationKey, "SUCCESS");
            PipelineLogUtils.info(logger, STAGE, dto.getSourceName(), dto.getApiName(),
                "raw_staging id=" + dto.getId() + " 적재 완료 (" + tookMs + "ms)");
            return true;
        } catch (LoadException e) {
            fail(dto, sample, operationKey, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            fail(dto, sample, operationKey, "UNHANDLED EXCEPTION: " + e.getClass().getName() + " - " + e.getMessage(), e);
            return false;
        }
    }

    private void fail(RawStagingDto dto, Timer.Sample sample, String operationKey, String message, Throwable cause) {
        long tookMs = stopTimer(sample, operationKey);
        rawStagingStore.markLoadFailed(dto.getId(), message);
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
        long tookNanos = sample.stop(Timer.builder("public_data_load_duration_seconds")
            .description("raw_staging 적재 소요시간")
            .tag("operationKey", operationKey == null ? "unknown" : operationKey)
            .register(meterRegistry));
        return TimeUnit.NANOSECONDS.toMillis(tookNanos);
    }

    private void recordAttempt(String operationKey, String status) {
        Counter.builder("public_data_load_attempts_total")
            .description("적재 시도 건수")
            .tag("operationKey", operationKey == null ? "unknown" : operationKey)
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }
}
