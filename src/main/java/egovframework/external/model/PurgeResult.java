package egovframework.external.model;

/**
 * {@code PublicDataPurgeService.purgeExpired()} 1회 실행 결과. {@code LoadResult}와 달리
 * 행 단위가 아니라 "테이블 단위"로 성공/실패를 센다 - 삭제 자체는 테이블당 1번의 DELETE
 * 문으로 끝나서 행 단위 성공/실패 개념이 없고, 테이블별로 격리해서 하나가 실패해도
 * 나머지 테이블은 계속 처리한다(적재 단계의 fail-isolation 원칙과 동일).
 */
public record PurgeResult(int totalDeleted, int successTableCount, int failTableCount) {
}
