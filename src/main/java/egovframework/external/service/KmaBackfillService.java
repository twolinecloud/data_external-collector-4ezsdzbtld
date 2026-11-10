package egovframework.external.service;

import egovframework.external.exception.ConflictException;
import egovframework.external.exception.InvalidParameterException;
import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.KmaLocationCollectorFactory;
import egovframework.external.publicdata.collector.PublicDataCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 기상청 초단기실황·초단기예보에서 비어 버린 지난 기준시각을 다시 수집한다. 2026-11-10 06~10시처럼 기상청이 "데이터 없음"을
 * 돌려준 시간대를 메우는 용도다.
 *
 * <p>기준시각 하나(1시간)가 수집 실행 1회다 - 평소 스케줄과 같은 경로({@link PublicDataPipelineRunner#collectAndAdvance})라
 * 정제·적재까지 이어지고, 적재는 (시설, 기준시각) 멱등키 upsert라서 이미 있는 시각을 다시 받아도 중복되지 않는다.
 * 오래 걸리므로 접수만 하고 백그라운드에서 순서대로 돈다. 한 번에 하나만 돌 수 있다(동시 요청은 409).
 */
@Service
public class KmaBackfillService {

    private static final Logger logger = LogManager.getLogger(KmaBackfillService.class);

    /** 한 번에 보충할 수 있는 기준시각 수 - 기상청이 지난 값을 오래 보관하지 않아 하루치면 충분하다. */
    static final int MAX_SLOTS = 24;

    /** 초단기실황(매시 정각 발표, 10분 이후 제공). */
    public static final String ULTRA_SRT_NCST = "kma-village-forecast-ultra-srt-ncst";
    /** 초단기예보(매시 30분 발표, 45분 이후 제공). */
    public static final String ULTRA_SRT_FCST = "kma-village-forecast-ultra-srt-fcst";

    /** 접수 결과 - 돌 기준시각(발표 시각) 목록. */
    public record Accepted(String operationKey, List<LocalDateTime> baseTimes) {
    }

    private final KmaLocationCollectorFactory factory;
    private final PublicDataPipelineRunner pipelineRunner;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();

    public KmaBackfillService(KmaLocationCollectorFactory factory, PublicDataPipelineRunner pipelineRunner,
            @Qualifier("taskScheduler") Executor executor) {
        this.factory = factory;
        this.pipelineRunner = pipelineRunner;
        this.executor = executor;
    }

    /**
     * @param operationKey {@link #ULTRA_SRT_NCST} 또는 {@link #ULTRA_SRT_FCST}
     * @param fromHour     처음 기준시각의 시(분 이하는 버림). 초단기예보면 그 시의 30분 발표분
     * @param toHour       마지막 기준시각의 시(포함)
     */
    public Accepted start(String operationKey, LocalDateTime fromHour, LocalDateTime toHour, String triggerBy) {
        List<LocalDateTime> baseTimes = baseTimes(operationKey, fromHour, toHour, LocalDateTime.now());
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException(logger, "이미 기준시각 보충이 진행 중입니다");
        }
        try {
            executor.execute(() -> runAll(operationKey, baseTimes, triggerBy));
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        logger.info("[BACKFILL] 접수 operationKey={} 기준시각={}건 {}~{} triggerBy={}", operationKey, baseTimes.size(),
            baseTimes.get(0), baseTimes.get(baseTimes.size() - 1), triggerBy);
        return new Accepted(operationKey, baseTimes);
    }

    /**
     * 보충할 기준시각 목록(오래된 순)을 검증해서 만든다. 아직 제공 전인 시각은 거절한다 - 기상청이 "데이터 없음"을 돌려줘서
     * 재시도만 길어지기 때문이다.
     */
    static List<LocalDateTime> baseTimes(String operationKey, LocalDateTime fromHour, LocalDateTime toHour, LocalDateTime now) {
        if (!ULTRA_SRT_NCST.equals(operationKey) && !ULTRA_SRT_FCST.equals(operationKey)) {
            throw new InvalidParameterException(logger, "보충을 지원하지 않는 오퍼레이션입니다: " + operationKey);
        }
        if (fromHour == null || toHour == null) {
            throw new InvalidParameterException(logger, "from, to 는 필수입니다 (예: 2026-11-10T06:00)");
        }
        LocalDateTime from = fromHour.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime to = toHour.truncatedTo(ChronoUnit.HOURS);
        if (from.isAfter(to)) {
            throw new InvalidParameterException(logger, "from 은 to 보다 늦을 수 없습니다");
        }
        long count = ChronoUnit.HOURS.between(from, to) + 1;
        if (count > MAX_SLOTS) {
            throw new InvalidParameterException(logger, "한 번에 보충할 수 있는 기준시각은 최대 " + MAX_SLOTS + "건입니다: " + count);
        }
        List<LocalDateTime> baseTimes = new ArrayList<>();
        for (LocalDateTime hour = from; !hour.isAfter(to); hour = hour.plusHours(1)) {
            LocalDateTime published = ULTRA_SRT_NCST.equals(operationKey) ? hour : hour.withMinute(30);
            if (requestedAt(operationKey, published).isAfter(now)) {
                throw new InvalidParameterException(logger, "아직 제공 전인 기준시각입니다: " + published);
            }
            baseTimes.add(published);
        }
        return baseTimes;
    }

    /** 그 발표 시각을 기준시각으로 계산해 주는 "호출 시각" - 실황은 정각+10분, 예보는 30분+15분 이후. */
    static LocalDateTime requestedAt(String operationKey, LocalDateTime published) {
        return ULTRA_SRT_NCST.equals(operationKey) ? published.plusMinutes(10) : published.plusMinutes(15);
    }

    private void runAll(String operationKey, List<LocalDateTime> baseTimes, String triggerBy) {
        Function<LocalDateTime, List<PublicDataCollector>> collectors = ULTRA_SRT_NCST.equals(operationKey)
            ? factory::ultraSrtNcstCollectorsAt
            : factory::ultraSrtFcstCollectorsAt;
        try {
            for (LocalDateTime published : baseTimes) {
                try {
                    pipelineRunner.collectAndAdvance(operationKey, collectors.apply(requestedAt(operationKey, published)),
                        ExecutionType.MANUAL, triggerBy);
                    logger.info("[BACKFILL] 기준시각 {} 수집 요청 완료 operationKey={}", published, operationKey);
                } catch (RuntimeException e) {
                    // 한 시각이 실패해도 나머지는 계속 - 어느 시각이 비었는지는 배치 이력에 남는다.
                    logger.warn("[BACKFILL] 기준시각 {} 수집 중 예외 operationKey={}", published, operationKey, e);
                }
            }
        } finally {
            running.set(false);
            logger.info("[BACKFILL] 끝 operationKey={} 기준시각={}건", operationKey, baseTimes.size());
        }
    }
}
