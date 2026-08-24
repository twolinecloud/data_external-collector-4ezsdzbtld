package egovframework.external.model;

/**
 * {@code FacilitySyncService.sync()} 1회 실행 결과. 이미 PENDING으로 등록된 항목은
 * 중복 집계하지 않는다 - 이번 실행에서 "새로 큐에 등록한" 건수만 센다.
 */
public record FacilitySyncResult(int newCount, int removedCount) {
}
