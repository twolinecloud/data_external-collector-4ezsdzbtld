package egovframework.external.staging;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.utility.PipelineLogUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * {@link RawStagingStore}의 메모리 구현.
 *
 * <p>프로세스가 재시작되면 그 시점까지의 작업 데이터가 전부 유실된다 - 날씨 데이터는
 * 다음 스케줄 실행이 금방 돌아와 자연 복구되므로 감내 가능하다고 판단.
 * 클러스터에서 인스턴스가 2개 이상으로 늘어나면(현재 HPA min=max=1) 인스턴스마다 별도의
 * 메모리를 갖게 되어 중복 수집/작업 유실이 생길 수 있음 - 그 전에 반드시 해결 필요.</p>
 *
 * <p><b>종결 행 회수(2026-09-02)</b>: 예전엔 어떤 상태의 행도 지우지 않아서 수집 1회마다
 * 행이 하나씩 영구히 쌓였다. 적재기가 없는 법제처처럼 매일 전량이 LOAD_ABANDONED로 끝나는
 * 소스가 들어오자 하루 약 491행씩 힙에 눌러앉았다(운영 실측). {@link #insert}가 같은
 * collectorKey의 종결 상태 행을 회수해서, 소스별로 "처리 중인 행 + 최신 1행"만 남게 한다.
 * 자세한 근거는 {@link #insert} 주석 참고.</p>
 *
 * <p><b>유효기간 만료(2026-09-02)</b>: 회수는 <b>다음 수집이 들어와야</b> 도는 규칙이라,
 * 적재가 오래 막히면 미적재 행은 회수 대상이 아니어서 계속 남는다. 데이터에 유효기간이 있는
 * 소스({@link egovframework.external.publicdata.collector.PublicDataCollector#stagingExpiresAt}를
 * 밝힌 기상청 6종)는 기한이 지나면 상태와 무관하게 폐기한다.</p>
 */
@Component
public class InMemoryRawStagingStore implements RawStagingStore {

    private static final Logger logger = LogManager.getLogger(InMemoryRawStagingStore.class);
    private static final String STAGE = "STAGING";

    /**
     * 어떤 단계도 다시 집어가지 않는 상태 - 이 행들은 {@link #insert}가 회수 대상으로 본다.
     *
     * <p>CLEANSE_FAILED가 여기 들어있는 건 정제 재시도가 아직 없기 때문이다
     * ({@link RawStagingDto} 주석 참고). 정제 재시도를 구현하게 되면 반드시 이 집합에서
     * 빼야 한다 - 안 그러면 재시도 대상이 회수돼서 사라진다.</p>
     */
    private static final Set<String> TERMINAL_STATUSES =
        Set.of("LOADED", "LOAD_SKIPPED", "LOAD_ABANDONED", "CLEANSE_FAILED");

    private final Map<Long, RawStagingDto> records = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    /**
     * 신규 수집분을 넣기 전에, 같은 collectorKey의 <b>종결된</b> 행을 회수한다.
     *
     * <p>collectorKey는 수집 단위 하나(법령 1건, 시설 1곳의 예보, 재난문자 목록조회)를
     * 가리키므로, 그 단위의 이전 수집분이 이미 끝난 상태라면 새 수집분이 그 자리를 대신하는
     * 게 맞다. 유일성 병합을 최종 테이블 자연키(예: {@code (sn, facility_id)})로 할 수 없는
     * 이유는 staging 행 1개가 논리 레코드 1건이 아니라 <b>수집 1회 응답 전체</b>이기
     * 때문이다({@link RawStagingDto#rawPayload}). 그래서 여기서는 "수집 단위"로 병합하고,
     * 레코드 단위 유일성은 지금처럼 적재 단계 upsert가 계속 담당한다.</p>
     *
     * <p>수집 케이스별로 결과가 갈린다:</p>
     * <ul>
     *   <li><b>법령/행정규칙</b> - 매일 05:00에 같은 collectorKey로 전량 재수집. 이전 행이
     *       LOAD_ABANDONED(적재기 미협의)로 끝나 있으므로 회수되어 <b>1행만 유지</b>된다.
     *       며칠이 지나도 최신 1건만 남는다.</li>
     *   <li><b>기상청 예보/실황</b> - 발표시각마다 새 수집이지만 이전 시각분은 이미 LOADED라
     *       회수된다. 시각별 이력은 최종 테이블이 {@code (facility_id, base_dtm, ...)}로
     *       들고 있으므로 staging에 남길 이유가 없다.</li>
     *   <li><b>재난문자</b> - 10분마다 매번 다른 배치를 받지만, 적재가 끝난 이전 배치는
     *       회수된다. 문자 자체는 최종 테이블에 {@code (sn, facility_id)}로 계속 쌓인다.</li>
     * </ul>
     *
     * <p><b>미적재 행은 절대 건드리지 않는다</b>(COLLECTED/CLEANSED/LOAD_FAILED). 적재가
     * 막혀 있는 동안 새 수집이 들어와도 이전 수집분을 덮지 않으므로, 적재 지연 중에 데이터가
     * 조용히 사라지는 일은 없다 - 그 경우엔 행이 잠깐 여러 개로 늘었다가 적재가 풀리면서
     * 다시 줄어든다.</p>
     *
     * <p>여기서 <b>유효기간이 지난 행도 같이 폐기</b>한다({@link RawStagingDto#expiresAt}).
     * 회수 규칙은 같은 collectorKey의 다음 수집이 들어와야 도는데, 적재가 오래 막히면 미적재
     * 행은 회수 대상이 아니라 계속 남기 때문이다. 기한을 밝힌 소스는 기상청 6종뿐이고, 이쪽은
     * 날짜 기준으로 어제 0시 이후 수집분까지만 적재할 값어치가 있다. 만료 폐기는 collectorKey를 가리지 않고
     * 전체를 훑는다 - 수집이 아예 끊긴 소스(시설 제외, 수집기 비활성화)의 잔여 행도 그래야
     * 정리된다.</p>
     *
     * <p>회수는 맵 전체 스캔이라 O(n)이지만, 이 규칙 자체가 n을 소스 수 규모로 묶어두고
     * {@link #findByStatus}가 이미 주기마다 같은 스캔을 하고 있어 새로 생기는 부담은 아니다.
     * 락을 잡지 않으므로 스캔 도중 종결된 행은 이번에 놓칠 수 있는데, 다음 수집에서 회수되니
     * 문제되지 않는다.</p>
     */
    @Override
    public void insert(RawStagingDto dto) {
        evict(dto.getCollectorKey());
        dto.setId(sequence.incrementAndGet());
        dto.setStatus("COLLECTED");
        dto.setCollectedAt(LocalDateTime.now());
        records.put(dto.getId(), dto);
    }

    private void evict(String collectorKey) {
        LocalDateTime now = LocalDateTime.now();
        List<RawStagingDto> doomed = records.values().stream()
            .filter(r -> isExpired(r, now) || isSupersededBy(r, collectorKey))
            .collect(Collectors.toList());

        for (RawStagingDto r : doomed) {
            // 만료로 지우는 행이 아직 적재 전이면 그 수집분은 여기서 사라진다. 종결 행이 정리되는
            // 건 정상이지만 이 경우는 "적재가 하루 넘게 밀렸다"는 뜻이라 조용히 넘기면 안 된다.
            if (isExpired(r, now) && !TERMINAL_STATUSES.contains(r.getStatus())) {
                PipelineLogUtils.warn(logger, STAGE, r.getSourceName(), r.getApiName(),
                    "raw_staging id=" + r.getId() + " 유효기간 경과로 폐기 - status=" + r.getStatus()
                        + ", 수집=" + r.getCollectedAt() + " (적재되지 않음)");
            }
            records.remove(r.getId());
        }
    }

    private boolean isExpired(RawStagingDto dto, LocalDateTime now) {
        return dto.getExpiresAt() != null && now.isAfter(dto.getExpiresAt());
    }

    /** collectorKey가 없는 수집기(테스트/구버전 경로)는 회수 대상을 특정할 수 없으므로 건너뛴다. */
    private boolean isSupersededBy(RawStagingDto dto, String collectorKey) {
        return collectorKey != null
            && collectorKey.equals(dto.getCollectorKey())
            && TERMINAL_STATUSES.contains(dto.getStatus());
    }

    @Override
    public List<RawStagingDto> findByStatus(String status, int limit, Set<String> operationKeys, boolean exclude) {
        return records.values().stream()
            .filter(r -> status.equals(r.getStatus()))
            .filter(r -> operationKeys.isEmpty() || (operationKeys.contains(r.getOperationKey()) != exclude))
            .sorted(Comparator.comparing(RawStagingDto::getCollectedAt))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public void markCleansed(Long id, String cleansedPayload, String processedBatchId) {
        update(id, dto -> {
            dto.setStatus("CLEANSED");
            dto.setCleansedPayload(cleansedPayload);
            dto.setProcessedBatchId(processedBatchId);
            dto.setCleansedAt(LocalDateTime.now());
        });
    }

    @Override
    public void markCleanseFailed(Long id, String failureLog, String processedBatchId) {
        update(id, dto -> {
            dto.setStatus("CLEANSE_FAILED");
            dto.setCleanseFailureLog(failureLog);
            dto.setProcessedBatchId(processedBatchId);
        });
    }

    @Override
    public void markLoaded(Long id) {
        update(id, dto -> {
            dto.setStatus("LOADED");
            dto.setLoadedAt(LocalDateTime.now());
        });
    }

    @Override
    public void markLoadSkipped(Long id, String reason) {
        update(id, dto -> {
            dto.setStatus("LOAD_SKIPPED");
            dto.setLoadFailureLog(reason);
        });
    }

    @Override
    public void markLoadFailed(Long id, String failureLog) {
        update(id, dto -> {
            dto.setStatus("LOAD_FAILED");
            dto.setLoadFailureLog(failureLog);
            dto.setLoadAttemptCount(dto.getLoadAttemptCount() + 1);
        });
    }

    @Override
    public void markLoadAbandoned(Long id, String failureLog) {
        update(id, dto -> {
            dto.setStatus("LOAD_ABANDONED");
            dto.setLoadFailureLog(failureLog);
            dto.setLoadAttemptCount(dto.getLoadAttemptCount() + 1);
        });
    }

    private void update(Long id, Consumer<RawStagingDto> mutator) {
        RawStagingDto dto = records.get(id);
        if (dto == null) {
            throw new IllegalArgumentException("존재하지 않는 raw staging id: " + id);
        }
        mutator.accept(dto);
    }
}
