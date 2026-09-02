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
 * CLEANSE_FAILED / LOAD_FAILED 로 남는다.</p>
 *
 * <p>등록된 적재기가 없는 operationKey는 LOAD_SKIPPED로 종결한다 - 실패가 아니라 "아직 적재
 * 대상이 아님"이고, 이 행이 사라지는 건 유실이 아니다(2026-09-02).</p>
 *
 * <p>LOAD_FAILED는 다음 적재 주기에서 재시도 대상이 되고({@code PublicDataLoadService}),
 * {@code public-data.load.max-attempts}회까지 실패하면 LOAD_ABANDONED로 전이해 더는
 * 재시도하지 않는다 - 계속 실패하는 행이 매 주기 재시도를 독점하는 것을 막기 위함.
 * (CLEANSE_FAILED 재시도는 아직 없음 - 이 주석이 예전엔 "재시도 대상이 된다"고 했지만
 * 실제로는 구현이 없던 상태였고, 2026-08-31에 적재 쪽만 실제 구현하며 문구를 맞춤)</p>
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
    /** 적재 실패 사유. LOAD_SKIPPED일 때는 실패가 아니라 <b>건너뛴 사유</b>가 들어간다. */
    String loadFailureLog;
    /** 적재 시도 횟수 - 실패할 때마다 1씩 증가. max-attempts에 도달하면 LOAD_ABANDONED. */
    int loadAttemptCount;
    String processedBatchId;
    /**
     * 이 행을 더 붙들고 있을 이유가 없어지는 시각 ({@code PublicDataCollector.stagingExpiresAt}).
     * {@code null}이면 기한 없음 - 법령/재난문자처럼 기한을 두지 않는 소스가 그렇다.
     * 기한이 지난 행은 상태와 무관하게 {@code RawStagingStore#insert} 시점에 폐기된다.
     * 기상청 6종은 날짜 기준이라 자정 경계가 들어간다(수집일 D면 D+2일 0시).
     */
    LocalDateTime expiresAt;
    LocalDateTime collectedAt;
    LocalDateTime cleansedAt;
    LocalDateTime loadedAt;
}
