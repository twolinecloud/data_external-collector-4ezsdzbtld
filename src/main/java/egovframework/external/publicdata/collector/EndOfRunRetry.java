package egovframework.external.publicdata.collector;

import java.time.Duration;

/**
 * 수집 실행 한 번이 대상 전체를 다 돈 뒤, 일시 오류로 실패한 것만 기다렸다가 다시 시도하는 규칙
 * ({@link PublicDataCollector#endOfRunRetry()}). 실패한 그 자리에서 바로 다시 부르지 않는 건, 상대 서버가 몇 분~수십 분씩
 * 불안정할 때 곧바로 다시 불러도 같은 결과라서다.
 *
 * @param passes 최대 재시도 차수 (0이면 재시도 안 함)
 * @param delay  각 차수 전에 기다리는 시간
 */
public record EndOfRunRetry(int passes, Duration delay) {

    public static final EndOfRunRetry NONE = new EndOfRunRetry(0, Duration.ZERO);

    /**
     * 법령/행정규칙 본문 조회. 2026-10-27 05:04~05:46 KST 법제처가 불안정해 10건이 실패했다(본문 없는 404가 수 ms 만에,
     * 502, 읽기 타임아웃) - 같은 요청이 몇 시간 뒤엔 모두 정상이었다. 하루 한 번 수집이라 그냥 두면 다음 날까지 빠진다.
     */
    public static final EndOfRunRetry LAW = new EndOfRunRetry(2, Duration.ofMinutes(3));

    /**
     * 기상청 초단기실황·초단기예보. 2026-11-10 06시 이후 호출 시점(매시 :12 / :47)에 기상청이 "데이터 없음(resultCode=03)"을
     * 59개소 전부에 돌려줘 몇 시간치가 비었다 - 같은 요청이 조금 뒤엔 정상 응답이었다. 다음 주기가 곧 재시도지만 그 주기는
     * 다른 기준시각을 받아 이전 시각이 영영 비므로, 같은 기준시각을 몇 분 뒤 다시 부른다.
     */
    public static final EndOfRunRetry KMA_NODATA = new EndOfRunRetry(3, Duration.ofMinutes(3));

    /**
     * 다시 해 볼 만한 실패인가 - 통신·서버 오류("API 호출 실패: 404/502/I/O error ...")와 수집 타임아웃, 기상청의 "데이터 없음"
     * (NODATA_ERROR - 발표 직후 제공 지연). 응답은 정상인데 내용이
     * 문제인 실패(예: "법령 조회 실패" - 이름이 없는 법령)는 다시 해도 같으니 제외한다.
     */
    public static boolean isTransient(String failureLog) {
        return failureLog != null && (failureLog.contains("API 호출 실패") || failureLog.startsWith("타임아웃")
            || failureLog.contains("NODATA_ERROR"));
    }
}
