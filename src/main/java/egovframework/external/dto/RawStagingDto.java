package egovframework.external.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * {@code data_collector.raw_staging} 1 row.
 *
 * <p>수집→정제→적재 재처리 체크포인트. {@code status} 값은
 * COLLECTED -&gt; CLEANSED -&gt; LOADED 순으로 전이하며, 실패 시
 * CLEANSE_FAILED / LOAD_FAILED 로 남아 다음 배치 실행에서 재시도 대상이 된다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawStagingDto {
    Long id;
    String sourceName;
    String apiName;
    String rawPayload;
    String status;
    String cleansedPayload;
    String cleanseFailureLog;
    String loadFailureLog;
    String processedBatchId;
    LocalDateTime collectedAt;
    LocalDateTime cleansedAt;
    LocalDateTime loadedAt;
}
