package egovframework.external.model;

/**
 * 적재 1회 실행 결과({@code PublicDataLoadService.loadRun}/{@code retryFailed}). {@code CollectResult}/
 * {@code CleanseResult}와 동일한 패턴 - 로그 컬렉터 연동 시 그대로 재사용 가능하게 맞춰둠.
 */
public record LoadResult(int totalProcessed, int successCount, int failCount) {
}
