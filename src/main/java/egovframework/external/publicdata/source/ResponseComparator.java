package egovframework.external.publicdata.source;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 같은 요청에 대한 두 응답(직접 연결 vs ESB 경유)이 "손실 없이 같은지" 비교한다. 바이트 일치가 아니라
 * 필드·값·배열·숫자·결측 표현이 같은지를 본다(메타빌드 요청서 기준).
 *
 * <ul>
 *   <li>JSON: 키 순서와 공백은 무시한다. 배열은 순서를 유지해 비교한다. 숫자는 값이 같으면 같고(1.0 = 1),
 *       문자열 "1"과 숫자 1은 다르다. null 과 키 부재는 다르다(결측 표현 보존).</li>
 *   <li>텍스트(API허브 등): 줄바꿈 종류(CRLF/LF)와 줄 끝 공백만 무시하고, 나머지는 줄 단위로 비교한다.</li>
 * </ul>
 *
 * 결과는 {@link Result}로 돌려주며, 차이는 경로와 함께 최대 {@link #MAX_DIFFERENCES}건까지만 담는다.
 */
public final class ResponseComparator {

    static final int MAX_DIFFERENCES = 20;

    private ResponseComparator() {
    }

    /** @param differences 차이 설명(경로: 직접값 ≠ 비교값). 같으면 비어 있다. */
    public record Result(boolean same, List<String> differences) {
        static Result of(List<String> differences) {
            return new Result(differences.isEmpty(), List.copyOf(differences));
        }
    }

    public static Result compareJson(String expected, String actual) {
        List<String> diffs = new ArrayList<>();
        Object left;
        Object right;
        try {
            left = parse(expected);
            right = parse(actual);
        } catch (JSONException e) {
            return Result.of(List.of("JSON 파싱 실패: " + e.getMessage()));
        }
        compareValue("$", left, right, diffs);
        return Result.of(diffs);
    }

    public static Result compareText(String expected, String actual) {
        String[] left = normalizeLines(expected);
        String[] right = normalizeLines(actual);
        List<String> diffs = new ArrayList<>();
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max && diffs.size() < MAX_DIFFERENCES; i++) {
            String l = i < left.length ? left[i] : null;
            String r = i < right.length ? right[i] : null;
            if (l == null || r == null || !l.equals(r)) {
                diffs.add("줄 " + (i + 1) + ": " + abbreviate(l) + " ≠ " + abbreviate(r));
            }
        }
        return Result.of(diffs);
    }

    private static String[] normalizeLines(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(line.stripTrailing());
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1); // 끝의 빈 줄 차이는 무시
        }
        return out.toArray(new String[0]);
    }

    private static Object parse(String json) {
        String trimmed = json == null ? "" : json.strip();
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }
        return new JSONObject(trimmed);
    }

    private static void compareValue(String path, Object left, Object right, List<String> diffs) {
        if (diffs.size() >= MAX_DIFFERENCES) {
            return;
        }
        if (left instanceof JSONObject l && right instanceof JSONObject r) {
            TreeSet<String> keys = new TreeSet<>(l.keySet());
            keys.addAll(r.keySet());
            for (String key : keys) {
                String child = path + "." + key;
                if (!l.has(key)) {
                    diffs.add(child + ": 직접 응답에 없음, 비교 응답에 있음 " + abbreviate(String.valueOf(r.get(key))));
                } else if (!r.has(key)) {
                    diffs.add(child + ": 비교 응답에 없음, 직접 응답에 있음 " + abbreviate(String.valueOf(l.get(key))));
                } else {
                    compareValue(child, l.get(key), r.get(key), diffs);
                }
            }
        } else if (left instanceof JSONArray l && right instanceof JSONArray r) {
            if (l.length() != r.length()) {
                diffs.add(path + ": 배열 길이 " + l.length() + " ≠ " + r.length());
            }
            for (int i = 0; i < Math.min(l.length(), r.length()); i++) {
                compareValue(path + "[" + i + "]", l.get(i), r.get(i), diffs);
            }
        } else if (left instanceof Number l && right instanceof Number r) {
            if (new BigDecimal(l.toString()).compareTo(new BigDecimal(r.toString())) != 0) {
                diffs.add(path + ": " + l + " ≠ " + r);
            }
        } else if (!sameScalar(left, right)) {
            diffs.add(path + ": " + describe(left) + " ≠ " + describe(right));
        }
    }

    private static boolean sameScalar(Object left, Object right) {
        if (left == JSONObject.NULL || right == JSONObject.NULL) {
            return left == right;
        }
        return left.getClass() == right.getClass() && left.equals(right);
    }

    private static String describe(Object value) {
        if (value == JSONObject.NULL) {
            return "null";
        }
        String kind = value instanceof String ? "문자열" : value instanceof Boolean ? "불린"
            : value instanceof Number ? "숫자" : value instanceof JSONObject ? "객체" : "배열";
        return kind + " " + abbreviate(String.valueOf(value));
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "(없음)";
        }
        return value.length() > 80 ? value.substring(0, 80) + "..." : value;
    }
}
