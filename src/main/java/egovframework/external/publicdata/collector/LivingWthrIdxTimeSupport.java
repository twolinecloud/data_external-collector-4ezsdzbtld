package egovframework.external.publicdata.collector;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 생활기상지수(자외선지수/대기정체지수) {@code time} 파라미터 계산. 가이드 문서(2026-08-24
 * data.go.kr 참고문서 zip) "생산주기: 일8회, 생산시간: 00~21(3hr)" 기준 - KST 00/03/06/09/
 * 12/15/18/21시에 발표되므로, 현재 시각 이하의 가장 최근 발표시각을 고른다.
 *
 * <p>실측(2026-08-24) 확인: 발표시각 정각에 바로 조회해도 {@code resultCode=00}으로 정상
 * 응답됨 - 별도 제공지연 버퍼 불필요(다른 기상청 API들과 달리 스케줄 크론에서 몇 분 여유를
 * 두는 정도로 충분, 이 클래스 자체엔 버퍼 로직 없음).</p>
 *
 * <p>포맷은 {@code yyyyMMddHH}(분 없이 10자리) - 다른 기상청 API의 baseDate+baseTime
 * 조합과 다르니 주의(가이드 예제 "2021070618" 실측 확인, {@code KmaDateTimeSupport}와는
 * 무관한 별도 포맷).</p>
 */
final class LivingWthrIdxTimeSupport {

    private static final int[] ISSUE_HOURS = {0, 3, 6, 9, 12, 15, 18, 21};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private LivingWthrIdxTimeSupport() {
    }

    /** @return 현재 시각(KST) 이하의 가장 최근 발표시각을 {@code yyyyMMddHH} 문자열로. */
    static String latestIssuedTime(LocalDateTime nowKst) {
        int hour = nowKst.getHour();
        int latestIssueHour = ISSUE_HOURS[0];
        for (int issueHour : ISSUE_HOURS) {
            if (issueHour <= hour) {
                latestIssueHour = issueHour;
            }
        }
        LocalDateTime issuedAt = nowKst.withHour(latestIssueHour).withMinute(0).withSecond(0).withNano(0);
        return issuedAt.format(TIME_FMT);
    }
}
