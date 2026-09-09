package egovframework.external.publicdata.collector;

import java.util.Set;

/**
 * 법령/행정규칙명에 흔히 섞여 들어가는 가운뎃점류 문자들을, 호출할 API(target)가 실제로
 * 요구하는 형태로 정규화한다.
 *
 * <p><b>배경(2026-09-08)</b>: 대상 목록(<code>moleg-criminal-laws.csv</code>)의 "국가공무원
 * 복무·징계 관련 예규"에 표준 가운뎃점(<code>·</code>, U+00B7) 대신 한글 자모 "아래아"
 * (<code>ㆍ</code>, U+318D)가 섞여 있어 행정규칙 정확명 조회({@code target=admrul})가
 * "일치하는 행정규칙이 없습니다"로 계속 실패했다 - 육안으로는 거의 구분이 안 되는 문자라
 * CSV를 직접 봐도 못 알아챘다.</p>
 *
 * <p><b>정정(2026-09-09)</b>: 최초 조치(fc24277)는 모든 target에 일괄로 표준 가운뎃점(·)으로
 * 정규화했는데, 이게 틀렸다. 파드 안에서 {@code law.go.kr}을 직접 호출해 실측한 결과, 두
 * target이 가운뎃점 문자에 대해 <b>정반대</b> 요구사항을 가진다:</p>
 * <ul>
 *   <li>{@code target=admrul}(행정규칙) - 표준 가운뎃점(·)을 줘야 매칭됨. 유사문자(ㆍ)를
 *       주면 "일치하는 행정규칙이 없습니다".</li>
 *   <li>{@code target=eflaw}(법령) - 반대로 유사문자(ㆍ)를 줘야 매칭됨. 표준 가운뎃점(·)을
 *       주면 JSON이 아니라 HTML 에러 페이지가 돌아와 파싱 실패로 이어짐(2026-09-09 05:00
 *       정기 실행에서 eflaw 25건이 이 이유로 실패, {@code moleg-eflaw-dot-char-regression}
 *       메모 참고).</li>
 * </ul>
 * <p>"eflaw의 관용성은 예측 불가능한 내부 동작"이라던 이전 결론도 틀렸음 - 실제로는
 * 결정론적이고, 그냥 admrul과 방향이 반대였을 뿐이다. 그래서 이 클래스는 표준형 한
 * 방향으로만 정규화하지 않고, 호출부({@link DirectLawSourceAdapter})가 target에 맞는
 * 메서드({@link #normalizeForLaw}/{@link #normalizeForAdminRule})를 골라 쓰도록 분리한다.</p>
 */
final class LawNameConfusables {

    private static final char STANDARD_DOT = '·';   // U+00B7 MIDDLE DOT - admrul이 요구
    private static final char LOOKALIKE_DOT = 'ㆍ';  // U+318D 한글 자모 아래아 - eflaw가 요구

    /**
     * 가운뎃점으로 흔히 오인되는 문자 전체(위 두 표준 후보 포함) - 실제로 CSV에서 발견된
     * U+318D 외에, 같은 부류로 흔히 섞이는 문장부호류를 미리 포함해뒀다(전부 발생을 확인한
     * 건 아니지만 같은 클래스의 재발을 막기 위함).
     */
    private static final Set<Character> DOT_VARIANTS = Set.of(
        LOOKALIKE_DOT, STANDARD_DOT,
        '‧', // HYPHENATION POINT
        '⋅', // DOT OPERATOR
        '∙', // BULLET OPERATOR
        '•'  // BULLET
    );

    private LawNameConfusables() {
    }

    /** {@code target=admrul}(행정규칙) 호출 직전용 - 표준 가운뎃점(·)으로 통일. */
    static String normalizeForAdminRule(String name) {
        return normalizeTo(name, STANDARD_DOT);
    }

    /** {@code target=eflaw}(법령) 호출 직전용 - 유사문자(ㆍ)로 통일. */
    static String normalizeForLaw(String name) {
        return normalizeTo(name, LOOKALIKE_DOT);
    }

    /** @return 가운뎃점류 문자를 전부 {@code target}으로 바꾼 이름. 바꿀 게 없으면 입력을 그대로 반환(새 객체 안 만듦). */
    private static String normalizeTo(String name, char target) {
        if (name == null) {
            return null;
        }
        StringBuilder normalized = null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != target && DOT_VARIANTS.contains(c)) {
                if (normalized == null) {
                    normalized = new StringBuilder(name);
                }
                normalized.setCharAt(i, target);
            }
        }
        return normalized == null ? name : normalized.toString();
    }
}
