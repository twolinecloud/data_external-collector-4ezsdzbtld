package egovframework.external.service;

import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.dto.CollectionAttemptLogDto;
import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.CollectException;
import egovframework.external.model.AttemptStatus;
import egovframework.external.model.CollectResult;
import egovframework.external.model.ExecutionType;
import egovframework.external.staging.CollectionAttemptLogStore;
import egovframework.external.staging.RawStagingStore;
import egovframework.external.utility.PipelineLogUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 하나의 {@link PublicDataCollector}를 실행하고 결과를 영속화하는 공통 로직.
 *
 * <p>{@code PublicDataCollectorScheduler}(자동 스케줄)와 {@code PublicDataCollectController}(수동 트리거)가
 * 이 서비스를 공유해서, 실행 경로가 스케줄이든 수동이든 완전히 동일한 처리(raw_staging 적재,
 * collection_attempt_log 기록, 메트릭 기록)를 보장한다.</p>
 *
 * <p>{@link RawStagingStore}/{@link CollectionAttemptLogStore}는 포트 인터페이스라 현재
 * 메모리 구현({@code InMemory*Store})에 의존하지만, 나중에 DB 어댑터로 교체해도 이 서비스는
 * 변경할 필요 없다.</p>
 *
 * <p>관측성: {@code public_data_collect_attempts_total}(카운터, collectorKey/executionType/status
 * 태그) / {@code public_data_collect_duration_seconds}(타이머, collectorKey 태그)를
 * {@link MeterRegistry}에 기록 - {@code /actuator/prometheus}에서 확인 가능.</p>
 *
 * <p><b>개별 수집 타임아웃(2026-08-26)</b>: {@code collector.collect()}를 별도 워커
 * 스레드에 맡기고 {@code Future.get(timeout)}으로 기다린다 - 스케줄러 스레드가 이 호출에
 * 타임아웃 없이 그대로 블로킹되면(실측 2026-08-25 23:20경, 에러 로그 없이 9시간 정지 사고)
 * 스케줄러 전체가 멈춰버리기 때문. 워커 스레드가 진짜로 영원히 안 끝나는 호출에 물려있어도
 * 이쪽(호출부)은 타임아웃 시점에 포기하고 다음 스케줄로 넘어간다 - 워커 스레드 자체는
 * (인터럽트가 안 먹히는 블로킹 콜이면) 누수될 수 있지만, 스케줄러 스레드풀
 * ({@code PublicDataSchedulingConfig})과 분리돼있어 파이프라인 전체가 멈추는 것보단 훨씬 낫다.</p>
 */
@Service
public class PublicDataCollectionAttemptService {

    private static final Logger logger = LogManager.getLogger(PublicDataCollectionAttemptService.class);
    private static final String STAGE = "COLLECT";
    private static final Duration DEFAULT_COLLECT_TIMEOUT = Duration.ofSeconds(180);

    private final RawStagingStore rawStagingStore;
    private final CollectionAttemptLogStore collectionAttemptLogStore;
    private final MeterRegistry meterRegistry;
    private final Duration collectTimeout;
    private final ExecutorService collectExecutor =
        Executors.newCachedThreadPool(r -> new Thread(r, "collect-worker"));

    // 생성자가 2개(운영용/테스트용)라 스프링이 자동으로 하나를 고르지 못함 - 명시적으로
    // 지정 필요(실측 2026-08-26, LogCollectorClient와 동일 패턴 - 로컬 부팅 확인 중 발견).
    @Autowired
    public PublicDataCollectionAttemptService(RawStagingStore rawStagingStore,
            CollectionAttemptLogStore collectionAttemptLogStore, MeterRegistry meterRegistry) {
        this(rawStagingStore, collectionAttemptLogStore, meterRegistry, DEFAULT_COLLECT_TIMEOUT);
    }

    /** 타임아웃을 직접 지정하는 생성자 - 테스트에서 180초를 실제로 기다리지 않고 타임아웃 경로를 검증할 때 씀. */
    PublicDataCollectionAttemptService(RawStagingStore rawStagingStore,
            CollectionAttemptLogStore collectionAttemptLogStore, MeterRegistry meterRegistry,
            Duration collectTimeout) {
        this.rawStagingStore = rawStagingStore;
        this.collectionAttemptLogStore = collectionAttemptLogStore;
        this.meterRegistry = meterRegistry;
        this.collectTimeout = collectTimeout;
    }

    @PreDestroy
    void shutdown() {
        collectExecutor.shutdownNow();
    }

    /**
     * @return 이번 실행 결과 요약 - 로그 컬렉터(외부 배치 로그 시스템) 연동에서 여러 컬렉터의
     *         결과를 모아 한 번에 보고(bulk)할 때 씀. 기존 호출부(스케줄러/컨트롤러)는 반환값을
     *         무시해도 그대로 동작한다(부수효과는 이전과 동일).
     */
    public CollectResult run(PublicDataCollector collector, ExecutionType executionType) {
        String sourceName = collector.sourceName();
        String apiName = collector.apiName();
        String collectorKey = collector.key();
        Timer.Sample sample = Timer.start(meterRegistry);

        List<String> rawPayloads;
        Future<List<String>> future = collectExecutor.submit(collector::collect);
        try {
            rawPayloads = future.get(collectTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            long tookMs = stopTimer(sample, collectorKey);
            String message = "타임아웃(" + collectTimeout.toMillis() + "ms 초과)";
            PipelineLogUtils.warn(logger, STAGE, sourceName, apiName, message + " (" + tookMs + "ms)");
            logAttempt(sourceName, apiName, executionType, AttemptStatus.FAILED, 0, message, collectorKey);
            return new CollectResult(collectorKey, sourceName, apiName, AttemptStatus.FAILED, 0, message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long tookMs = stopTimer(sample, collectorKey);
            String message = "수집 대기 중 인터럽트됨";
            PipelineLogUtils.warn(logger, STAGE, sourceName, apiName, message + " (" + tookMs + "ms)");
            logAttempt(sourceName, apiName, executionType, AttemptStatus.FAILED, 0, message, collectorKey);
            return new CollectResult(collectorKey, sourceName, apiName, AttemptStatus.FAILED, 0, message);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CollectException ce) {
                long tookMs = stopTimer(sample, collectorKey);
                PipelineLogUtils.warn(logger, STAGE, sourceName, apiName, ce.getMessage() + " (" + tookMs + "ms)");
                logAttempt(sourceName, apiName, executionType, AttemptStatus.FAILED, 0, ce.getMessage(), collectorKey);
                return new CollectResult(collectorKey, sourceName, apiName, AttemptStatus.FAILED, 0, ce.getMessage());
            }
            // 수집기 구현체가 CollectException으로 감싸지 않은 미처리 예외 - 그래도 배치를 죽이지 않고 이 소스만 실패 처리
            long tookMs = stopTimer(sample, collectorKey);
            String message = "UNHANDLED EXCEPTION: " + cause.getClass().getName() + " - " + cause.getMessage();
            PipelineLogUtils.error(logger, STAGE, sourceName, apiName, message + " (" + tookMs + "ms)", cause);
            logAttempt(sourceName, apiName, executionType, AttemptStatus.FAILED, 0, message, collectorKey);
            return new CollectResult(collectorKey, sourceName, apiName, AttemptStatus.FAILED, 0, message);
        }

        // 항목(카테고리x시간) 하나마다 행을 만들지 않고, 이번 수집 1회 전체를 JSON 배열 하나로
        // 묶어서 행 1개로 저장한다 - 단기예보 한 번에 900건 가까이 나오는데 그걸 낱개로 쌓으면
        // 인메모리 스토어가 스케줄 몇 바퀴만 돌아도 무한정 불어남.
        if (!rawPayloads.isEmpty()) {
            String combinedPayload = "[" + String.join(",", rawPayloads) + "]";
            RawStagingDto dto = RawStagingDto.builder()
                .sourceName(sourceName)
                .apiName(apiName)
                .operationKey(collector.operationKey())
                .facilityId(collector.facilityId())
                .collectorKey(collectorKey)
                .rawPayload(combinedPayload)
                .expiresAt(expiresAt(collector))
                .build();
            rawStagingStore.insert(dto);
        }

        long tookMs = stopTimer(sample, collectorKey);
        PipelineLogUtils.info(logger, STAGE, sourceName, apiName,
            "collected " + rawPayloads.size() + " record(s) in " + tookMs + "ms");
        logAttempt(sourceName, apiName, executionType, AttemptStatus.SUCCESS, rawPayloads.size(), null, collectorKey);
        return new CollectResult(collectorKey, sourceName, apiName, AttemptStatus.SUCCESS, rawPayloads.size(), null);
    }

    /**
     * 유효기간을 밝힌 수집기(현재 기상청 6종)만 만료 시각이 붙는다 -
     * {@link PublicDataCollector#stagingExpiresAt(LocalDate)}. 기준이 수집 시각이 아니라
     * 수집 <b>날짜</b>라서 오늘 날짜만 넘긴다.
     */
    private LocalDateTime expiresAt(PublicDataCollector collector) {
        return collector.stagingExpiresAt(LocalDate.now());
    }

    private long stopTimer(Timer.Sample sample, String collectorKey) {
        long tookNanos = sample.stop(Timer.builder("public_data_collect_duration_seconds")
            .description("공공데이터 수집(API 호출 ~ raw_staging 적재) 소요시간")
            .tag("collectorKey", collectorKey)
            .register(meterRegistry));
        return TimeUnit.NANOSECONDS.toMillis(tookNanos);
    }

    private void logAttempt(String sourceName, String apiName, ExecutionType executionType,
                             AttemptStatus status, int recordCount, String failureLog, String collectorKey) {
        CollectionAttemptLogDto log = CollectionAttemptLogDto.builder()
            .sourceName(sourceName)
            .apiName(apiName)
            .executionType(executionType.name())
            .status(status.name())
            .recordCount(recordCount)
            .failureLog(failureLog)
            .build();
        collectionAttemptLogStore.insert(log);

        Counter.builder("public_data_collect_attempts_total")
            .description("공공데이터 수집 시도 건수")
            .tag("collectorKey", collectorKey)
            .tag("executionType", executionType.name())
            .tag("status", status.name())
            .register(meterRegistry)
            .increment();
    }
}
