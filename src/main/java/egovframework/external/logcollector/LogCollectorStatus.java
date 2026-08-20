package egovframework.external.logcollector;

/**
 * 로그 컬렉터 공통코드 C04(처리 상태) 중 우리가 배치/단계 종료 시 실제로 산출하는 값만.
 * WAIT/RUNNING/CANCELED는 우리 쪽에서 "이미 끝난 결과"를 보고할 때는 안 씀(실측,
 * private-doc/log-collector-api-spec.md §5.1).
 */
public enum LogCollectorStatus {
    SUCCESS, PARTIAL, FAIL;

    /** 개별 결과(성공/실패) 집계 - 전부 성공 SUCCESS, 전부 실패 FAIL, 섞이면 PARTIAL. */
    public static LogCollectorStatus aggregate(int successCount, int failCount) {
        if (failCount == 0) {
            return SUCCESS;
        }
        if (successCount == 0) {
            return FAIL;
        }
        return PARTIAL;
    }
}
