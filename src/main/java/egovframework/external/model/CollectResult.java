package egovframework.external.model;

/**
 * {@code PublicDataCollectionAttemptService.run()} 1회 실행 결과. 기존에도 내부적으로 계산하던
 * 값(성공여부/건수/실패사유)을 호출자에게 돌려주기 위한 것 - 로그 컬렉터(외부 배치 로그 시스템)
 * 연동에서 여러 컬렉터 실행 결과를 모아 한 번에 보고할 때 씀(private-doc/log-collector-api-spec.md).
 */
public record CollectResult(
    String collectorKey,
    String sourceName,
    String apiName,
    AttemptStatus status,
    int recordCount,
    String failureLog
) {
}
