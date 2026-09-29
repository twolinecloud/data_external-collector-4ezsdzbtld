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

    /**
     * 단계별 결과를 배치 최종 상태로 합칠 때 더 나쁜 쪽(SUCCESS &lt; PARTIAL &lt; FAIL). 한 단계가
     * FAIL이면 다음 단계로 넘어간 행이 없어 그 단계에서 배치가 닫히므로, 앞 단계의 FAIL 뒤에
     * 성공 단계가 이어지는 조합은 생기지 않는다.
     */
    public static LogCollectorStatus worst(LogCollectorStatus a, LogCollectorStatus b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
