package egovframework.external.staging;

import egovframework.external.dto.RawStagingDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * {@link RawStagingStore}의 메모리 구현.
 *
 * <p>프로세스가 재시작되면 그 시점까지의 작업 데이터가 전부 유실된다 - 날씨 데이터는
 * 다음 스케줄 실행이 금방 돌아와 자연 복구되므로 감내 가능하다고 판단 (private-doc 참고).
 * 클러스터에서 인스턴스가 2개 이상으로 늘어나면(현재 HPA min=max=1) 인스턴스마다 별도의
 * 메모리를 갖게 되어 중복 수집/작업 유실이 생길 수 있음 - 그 전에 반드시 해결 필요.</p>
 */
@Component
public class InMemoryRawStagingStore implements RawStagingStore {

    private final Map<Long, RawStagingDto> records = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public void insert(RawStagingDto dto) {
        dto.setId(sequence.incrementAndGet());
        dto.setStatus("COLLECTED");
        dto.setCollectedAt(LocalDateTime.now());
        records.put(dto.getId(), dto);
    }

    @Override
    public List<RawStagingDto> findByStatus(String status, int limit) {
        return records.values().stream()
            .filter(r -> status.equals(r.getStatus()))
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
    public void markLoadFailed(Long id, String failureLog) {
        update(id, dto -> {
            dto.setStatus("LOAD_FAILED");
            dto.setLoadFailureLog(failureLog);
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
