package egovframework.external.publicdata.collector;

import java.util.Map;

/**
 * 법령/행정규칙명에 흔히 섞여 들어가는, 표준 가운뎃점(<code>·</code>, U+00B7)과 육안으로
 * 거의 구분되지 않는 유사문자(look-alike)를 표준형으로 정규화한다.
 *
 * <p><b>배경(2026-09-08)</b>: 대상 목록(<code>moleg-criminal-laws.csv</code>)의 "국가공무원
 * 복무·징계 관련 예규"에 표준 가운뎃점 대신 한글 자모 "아래아"(ㆍ, U+318D)가 섞여 있어
 * 행정규칙 정확명 조회({@code target=admrul})가 "일치하는 행정규칙이 없습니다"로 계속
 * 실패했다 - 육안으로는 거의 구분이 안 되는 문자라 CSV를 직접 봐도 못 알아챘다. 전체
 * 재검사 결과 같은 유사문자를 쓰는 항목이 법령 25건에 더 있었는데, 그쪽은 우연히 드러나지
 * 않았을 뿐이다(아래 참고).</p>
 *
 * <p><b>법제처 API 자체의 관용성에는 기대지 않는다</b> - {@code target=eflaw}(법령)가 이
 * 차이에 관대한 것처럼 보였지만, 실제로 검증해보니 신뢰할 수 있는 정규화 규칙이 아니라
 * 예측 불가능한 내부 동작이었다(표준 문자를 정확히 넣었는데도 다른 항목에 우연히 매칭돼
 * 오히려 실패하는 경우까지 확인됨, 2026-09-08). 그래서 API가 알아서 봐줄 거라 가정하지
 * 않고, 호출 직전({@link DirectLawSourceAdapter}) 우리 쪽에서 표준형으로 미리 맞춰 보낸다 -
 * 대상 목록이 CSV든(현재) admin-db든(추후 db 소스 전환 시) 이 관문 하나만 지키면 된다.</p>
 */
final class LawNameConfusables {

    /**
     * 표준 가운뎃점(U+00B7)과 혼동되는 문자 -> 표준형 매핑. 실제로 CSV에서 발견된
     * U+318D 외에, 같은 부류로 흔히 섞이는 문장부호류를 미리 포함해뒀다 - 전부 발생을
     * 확인한 건 아니지만(현재 확인된 사례는 U+318D 하나) 같은 클래스의 재발을 막기 위함.
     */
    private static final Map<Character, Character> MIDDLE_DOT_LOOKALIKES = Map.of(
        'ㆍ', '·', // ㆍ 한글 자모 아래아 (2026-09-08 실제 발견 사례)
        '‧', '·', // ‧ HYPHENATION POINT
        '⋅', '·', // ⋅ DOT OPERATOR
        '∙', '·', // ∙ BULLET OPERATOR
        '•', '·'  // • BULLET
    );

    private LawNameConfusables() {
    }

    /** @return 유사문자를 표준형으로 바꾼 이름. 바꿀 게 없으면 입력을 그대로 반환(새 객체 안 만듦). */
    static String normalize(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder normalized = null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            Character replacement = MIDDLE_DOT_LOOKALIKES.get(c);
            if (replacement != null) {
                if (normalized == null) {
                    normalized = new StringBuilder(name);
                }
                normalized.setCharAt(i, replacement);
            }
        }
        return normalized == null ? name : normalized.toString();
    }
}
