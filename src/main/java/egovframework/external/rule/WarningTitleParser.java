package egovframework.external.rule;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기상특보목록(getWthrWrnList) {@code title} 자유텍스트에서 현상명/단계/상태를 뽑는다.
 * 실측 형식(2026-08-14~18): {@code "[특보] 제08-47호 : 2026.08.14.11:00 / 폭염주의보 해제 (*)"},
 * {@code "[특보] 제08-24호 : 2026.08.14.10:00 / 호우주의보 발표 (*)"}.
 *
 * <p>이 API는 발표(활성화) 이력과 해제(종료) 이력을 같은 목록에 함께 반환한다 - 재난문자
 * {@code DST_SE_NM}처럼 "지금 유효한지"가 필드로 분리돼있지 않으므로, 같은 stnId·현상 조합
 * 안에서 발표시각이 가장 늦은 항목의 상태(발표/해제)로 "현재 유효 여부"를 판단해야 한다
 * (이 파싱 자체는 상태 판단을 하지 않고, 재료가 되는 (현상, 상태) 쌍만 뽑는다 -
 * {@link RuleEvaluationService}가 최신 것만 골라 판단).</p>
 */
public final class WarningTitleParser {

    private static final Pattern PATTERN = Pattern.compile("/\\s*(\\S+?)(주의보|경보)\\s*(발표|해제)");

    private WarningTitleParser() {
    }

    public record ParsedWarning(String phenomenon, String level, String status) {
        public boolean isActive() {
            return "발표".equals(status);
        }
    }

    public static Optional<ParsedWarning> parse(String title) {
        if (title == null) {
            return Optional.empty();
        }
        Matcher m = PATTERN.matcher(title);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedWarning(m.group(1).trim(), m.group(2), m.group(3)));
    }
}
