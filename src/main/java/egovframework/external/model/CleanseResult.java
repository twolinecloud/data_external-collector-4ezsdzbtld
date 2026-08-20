package egovframework.external.model;

/**
 * {@code PublicDataCleanseService.cleanseAllPending()} 1회 실행 결과. 기존엔 총 처리건수만
 * 반환했는데, 로그 컬렉터 연동(배치 종료 시 성공/실패 건수 분리 보고)에 성공/실패 분리가
 * 필요해 확장함(private-doc/log-collector-api-spec.md §8 "구현 시 확인된 코드 공백" 참고).
 */
public record CleanseResult(int totalProcessed, int successCount, int failCount) {
}
