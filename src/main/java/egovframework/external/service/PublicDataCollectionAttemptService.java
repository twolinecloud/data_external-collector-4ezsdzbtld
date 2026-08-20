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
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
 */
@Service
@RequiredArgsConstructor
public class PublicDataCollectionAttemptService {

    private static final Logger logger = LogManager.getLogger(PublicDataCollectionAttemptService.class);
    private static final String STAGE = "COLLECT";

    private final RawStagingStore rawStagingStore;
    private final CollectionAttemptLogStore collectionAttemptLogStore;
    private final MeterRegistry meterRegistry;

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
        try {
            rawPayloads = collector.collect();
        } catch (CollectException e) {
            long tookMs = stopTimer(sample, collectorKey);
            PipelineLogUtils.warn(logger, STAGE, sourceName, apiName, e.getMessage() + " (" + tookMs + "ms)");
            logAttempt(sourceName, apiName, executionType, AttemptStatus.FAILED, 0, e.getMessage(), collectorKey);
            return new CollectResult(collectorKey, sourceName, apiName, AttemptStatus.FAILED, 0, e.getMessage());
        } catch (Exception e) {
            // 수집기 구현체가 CollectException으로 감싸지 않은 미처리 예외 - 그래도 배치를 죽이지 않고 이 소스만 실패 처리
            long tookMs = stopTimer(sample, collectorKey);
            String message = "UNHANDLED EXCEPTION: " + e.getClass().getName() + " - " + e.getMessage();
            PipelineLogUtils.error(logger, STAGE, sourceName, apiName, message + " (" + tookMs + "ms)", e);
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
                .build();
            rawStagingStore.insert(dto);
        }

        long tookMs = stopTimer(sample, collectorKey);
        PipelineLogUtils.info(logger, STAGE, sourceName, apiName,
            "collected " + rawPayloads.size() + " record(s) in " + tookMs + "ms");
        logAttempt(sourceName, apiName, executionType, AttemptStatus.SUCCESS, rawPayloads.size(), null, collectorKey);
        return new CollectResult(collectorKey, sourceName, apiName, AttemptStatus.SUCCESS, rawPayloads.size(), null);
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
