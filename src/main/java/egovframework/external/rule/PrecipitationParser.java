package egovframework.external.rule;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기상청 강수량 필드(RN1/PCP)의 mm 값을 파싱한다. 실측 확인된 값 형태
 * (private-doc/terrain-rule-base-spec.md §5-1, cleanse-db-schema-spec.md §2.0):
 *
 * <ul>
 *   <li>{@code "강수없음"}/{@code "적설없음"} → 0</li>
 *   <li>{@code "30.0~50.0mm"} (구간) → 상한(50.0)을 보수적으로 사용 - 알림이 목적이라 과소평가보다 나음</li>
 *   <li>{@code "1.0mm 미만"} → 그 값(1.0) 자체를 근사 상한으로 사용(구간이 작아 실무 영향 미미)</li>
 *   <li>{@code "50.0mm 이상"} → 그 값(50.0)을 사용</li>
 *   <li>{@code "1.0mm"} (단일값) → 그대로</li>
 *   <li>{@code "23.5"}/{@code "0"} (단위 없는 순수 숫자 - 초단기실황 RN1 실측 형태) → 그대로</li>
 * </ul>
 *
 * <p>위 어느 형태에도 안 맞으면 조용히 0으로 처리하지 않고 예외를 던진다 - API가 새 형식을
 * 쓰기 시작했을 때 알림 수치를 과소평가한 채로 조용히 넘어가면 안 되기 때문
 * (이 프로젝트의 "실측 없이 넘겨짚지 않는다" 원칙과 동일한 이유).</p>
 */
public final class PrecipitationParser {

    private static final Pattern RANGE = Pattern.compile("^(\\d+(?:\\.\\d+)?)~(\\d+(?:\\.\\d+)?)mm$");
    private static final Pattern LESS_THAN = Pattern.compile("^(\\d+(?:\\.\\d+)?)mm\\s*미만$");
    private static final Pattern OR_MORE = Pattern.compile("^(\\d+(?:\\.\\d+)?)mm\\s*이상$");
    private static final Pattern PLAIN_MM = Pattern.compile("^(\\d+(?:\\.\\d+)?)mm$");

    private PrecipitationParser() {
    }

    public static double parseMm(String raw) {
        if (raw == null) {
            return 0.0;
        }
        String v = raw.trim();
        if (v.isEmpty() || v.equals("강수없음") || v.equals("적설없음")) {
            return 0.0;
        }

        Matcher m;
        if ((m = RANGE.matcher(v)).matches()) {
            return Double.parseDouble(m.group(2));
        }
        if ((m = LESS_THAN.matcher(v)).matches()) {
            return Double.parseDouble(m.group(1));
        }
        if ((m = OR_MORE.matcher(v)).matches()) {
            return Double.parseDouble(m.group(1));
        }
        if ((m = PLAIN_MM.matcher(v)).matches()) {
            return Double.parseDouble(m.group(1));
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("강수량 값 형식을 해석할 수 없음: '" + raw + "'", e);
        }
    }
}
