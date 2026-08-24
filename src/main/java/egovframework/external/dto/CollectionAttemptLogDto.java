package egovframework.external.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * {@code data_collector.collection_attempt_log} 1 row.
 *
 * <p>소스별 개별 스케줄러(또는 수동 트리거)가 실행될 때마다 남기는 시도 로그.
 * raw_staging 행 생성 여부와 무관하게, API 호출 자체의 성패를 기록한다.
 * 이후 정제+적재 배치가 {@code claimed_batch_id IS NULL} 인 로그를 모아
 * COLLECT 단계의 실행이력(pipeline_stage_execution/pipeline_source_execution_detail)을 구성한다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionAttemptLogDto {
    Long id;
    String sourceName;
    String apiName;
    String executionType;
    String status;
    Integer recordCount;
    String failureLog;
    LocalDateTime attemptedAt;
    String claimedBatchId;
}
