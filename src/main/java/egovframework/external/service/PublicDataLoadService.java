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
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * raw_staging의 CLEANSED 행을 꺼내 알맞은 {@link PublicDataLoader}로 admin-db 최종 테이블에
 * upsert해서 LOADED/LOAD_FAILED로 전이시키는 오케스트레이션. {@code PublicDataCleanseService}와
 * 대칭 구조.
 *
 * <p>{@code public-data.load.enabled=false}(기본값)면 raw_staging을 아예 건드리지 않고
 * 바로 빈 결과를 반환한다 - 스케줄/수동 트리거 어느 경로로 호출되든 이 가드 하나로 전부
 * 막힌다(꺼진 상태에서 CLEANSED 행이 "로더 없음"으로 잘못 LOAD_FAILED 처리되는 것 방지).</p>
 *
 * <p><b>실패 행 재시도(2026-08-31)</b>: 예전엔 CLEANSED만 조회해서, 한 번 LOAD_FAILED가 된
 * 행은 두 번 다시 쳐다보지 않았다. {@code raw_staging}이 인메모리라 그 행은 재시도 경로 없이
 * 그대로 소멸했고, 재난문자처럼 "당일만 조회 가능한" API는 자정을 넘기면 영구 유실이었다
 * (cleanse-db-schema-spec.md §4.1-A). 이제 매 주기 LOAD_FAILED를 먼저 재시도하고,
 * {@code max-attempts}회까지 실패하면 LOAD_ABANDONED로 종결시킨다.</p>
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
    private final int maxAttempts;

    public PublicDataLoadService(
        RawStagingStore rawStagingStore,
        PublicDataLoaderRegistry loaderRegistry,
        MeterRegistry meterRegistry,
        @Value("${public-data.load.enabled:false}") boolean enabled,
        @Value("${public-data.load.max-attempts:3}") int maxAttempts
    ) {
        this.rawStagingStore = rawStagingStore;
        this.loaderRegistry = loaderRegistry;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
    }

    /**
     * CLEANSED 상태 전체를 다 뺄 때까지 반복 처리.
     *
     * @return 총/성공/실패 건수 (enabled=false면 전부 0)
     */
    public LoadResult loadAllPending() {
        return loadPending(Set.of(), false);
    }

    /**
     * operationKey로 걸러서 CLEANSED 상태를 처리 - 로그 컬렉터 배치를 EXTERNAL_PUBLIC/
     * EXTERNAL_LAW로 나눠 보고하기 위해 도입(2026-08-27, {@code PublicDataLoadScheduler}
     * 참고). {@code operationKeys}가 비어있으면 {@link #loadAllPending()}과 동일.
     */
    public LoadResult loadPending(Set<String> operationKeys, boolean exclude) {
        if (!enabled) {
            return new LoadResult(0, 0, 0);
        }

        Tally tally = new Tally();

        // 1) 지난 주기에 실패한 행 재시도. 여기서는 소진될 때까지 반복하지 않는다 - 재시도가
        //    또 실패하면 그 행이 다시 LOAD_FAILED가 되어 같은 조회에 즉시 다시 잡히므로
        //    호출 하나가 무한루프에 빠진다(RawStagingStore#findByStatus 주석의 경고와 같은
        //    상황). 한 주기에 최대 BATCH_SIZE건만 재시도하고 나머지는 다음 주기로 넘긴다.
        for (RawStagingDto dto : rawStagingStore.findByStatus("LOAD_FAILED", BATCH_SIZE, operationKeys, exclude)) {
            tally.add(loadOne(dto));
        }

        // 2) 신규 CLEANSED 행. 실패하면 LOAD_FAILED로 빠져나가 이 조회에 다시 안 걸리므로
        //    (1)과 달리 소진될 때까지 반복해도 안전하다. 이번 주기에 실패한 행은 (1)이 이미
        //    지나갔으니 다음 주기부터 재시도된다 - 일시적 장애에 시간 여유를 주는 효과.
        List<RawStagingDto> batch;
        while (!(batch = rawStagingStore.findByStatus("CLEANSED", BATCH_SIZE, operationKeys, exclude)).isEmpty()) {
            for (RawStagingDto dto : batch) {
                tally.add(loadOne(dto));
            }
        }
        return new LoadResult(tally.total, tally.success, tally.fail);
    }

    /** loadPending의 두 단계가 같은 집계를 공유하기 위한 카운터. */
    private static final class Tally {
        private int total;
        private int success;
        private int fail;

        void add(boolean succeeded) {
            total++;
            if (succeeded) {
                success++;
            } else {
                fail++;
            }
        }
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
        int attempt = dto.getLoadAttemptCount() + 1;
        boolean abandoned = attempt >= maxAttempts;

        if (abandoned) {
            rawStagingStore.markLoadAbandoned(dto.getId(), message);
            recordAbandoned(operationKey);
        } else {
            rawStagingStore.markLoadFailed(dto.getId(), message);
        }
        // status 태그는 기존 대시보드가 쓰던 값(SUCCESS/FAILED)을 그대로 유지하고, 포기는
        // 별도 카운터로 뺀다 - "영구 유실"은 단순 실패와 알람 기준이 달라야 하기 때문.
        recordAttempt(operationKey, "FAILED");

        String detail = message + " [시도 " + attempt + "/" + maxAttempts + ", "
            + (abandoned ? "재시도 한도 소진 - 이 행은 포기함" : "다음 주기에 재시도") + "]"
            + " (" + tookMs + "ms)";
        if (cause != null) {
            PipelineLogUtils.error(logger, STAGE, dto.getSourceName(), dto.getApiName(),
                "raw_staging id=" + dto.getId() + " - " + detail, cause);
        } else {
            PipelineLogUtils.warn(logger, STAGE, dto.getSourceName(), dto.getApiName(),
                "raw_staging id=" + dto.getId() + " - " + detail);
        }
    }

    private void recordAbandoned(String operationKey) {
        Counter.builder("public_data_load_abandoned_total")
            .description("재시도 한도를 소진해 적재를 포기한 raw_staging 건수 (데이터 영구 유실)")
            .tag("operationKey", operationKey == null ? "unknown" : operationKey)
            .register(meterRegistry)
            .increment();
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
