package egovframework.external.staging;

import egovframework.external.dto.CollectionAttemptLogDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** {@link CollectionAttemptLogStore}의 메모리 구현. 유실 조건은 {@link InMemoryRawStagingStore}와 동일. */
@Component
public class InMemoryCollectionAttemptLogStore implements CollectionAttemptLogStore {

    private final Map<Long, CollectionAttemptLogDto> records = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public void insert(CollectionAttemptLogDto dto) {
        dto.setId(sequence.incrementAndGet());
        dto.setAttemptedAt(LocalDateTime.now());
        records.put(dto.getId(), dto);
    }

    @Override
    public List<CollectionAttemptLogDto> findUnclaimed() {
        return records.values().stream()
            .filter(r -> r.getClaimedBatchId() == null)
            .sorted(Comparator.comparing(CollectionAttemptLogDto::getAttemptedAt))
            .collect(Collectors.toList());
    }

    @Override
    public void claimBatch(List<Long> ids, String batchId) {
        for (Long id : ids) {
            CollectionAttemptLogDto dto = records.get(id);
            if (dto != null) {
                dto.setClaimedBatchId(batchId);
            }
        }
    }
}
