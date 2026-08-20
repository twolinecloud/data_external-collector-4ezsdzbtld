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
    /** 정제 단계가 알맞은 PublicDataCleanser를 찾는 안정적 키 (PublicDataCollector.operationKey()). */
    String operationKey;
    /** 위치기반 수집이면 그 기관 facilityId, 아니면 null. */
    String facilityId;
    /**
     * 컬렉터 인스턴스 하나를 유일하게 가리키는 키 (PublicDataCollector.key()). operationKey는
     * 오퍼레이션 단위(여러 인스턴스가 공유)라 facilityId가 null인 소스(예: 법령 44건 - 전부
     * operationKey="moleg-criminal-law")끼리는 서로 구분이 안 되는데, 이 값은 항상 유일함
     * (예: "moleg-criminal-law--001692", "kma-village-forecast-vilage-fcst--1270280").
     */
    String collectorKey;
    /** 이번 수집 1회 전체를 JSON 배열 문자열로 담음 (항목 하나당 행 하나가 아니라 수집 1회 = 행 1개). */
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
