package egovframework.external.publicdata.transfer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 정제된 payload(배열, 원소 1개 = 문서 1건)를 {@link LawDocument}로 바꾼다.
 *
 * <p>법령은 최상위 키가 {@code 법령}, 행정규칙은 {@code AdmRulService}이고 메타 위치도 다르다
 * (2026-10 실측: 법령 {@code 기본정보}, 행정규칙 {@code 행정규칙기본정보}). 별표 링크는 법령이
 * {@code /LSW/flDownload.do?flSeq=...}(상대경로)이고 행정규칙 첨부는 {@code http://law.go.kr/...}이다.</p>
 */
public final class LawDocumentParser {

    private static final String LAW_HOST = "https://www.law.go.kr";

    private LawDocumentParser() {
    }

    public static List<LawDocument> parse(String cleansedPayload) {
        JSONArray items = new JSONArray(cleansedPayload);
        List<LawDocument> docs = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            docs.add(parseOne(items.getJSONObject(i)));
        }
        return docs;
    }

    static LawDocument parseOne(JSONObject item) {
        if (item.has("법령")) {
            return parseLaw(item, item.getJSONObject("법령"));
        }
        if (item.has("AdmRulService")) {
            return parseAdminRule(item, item.getJSONObject("AdmRulService"));
        }
        throw new IllegalArgumentException("법령/행정규칙 본문이 아님: 최상위 키=" + item.keySet());
    }

    private static LawDocument parseLaw(JSONObject item, JSONObject root) {
        JSONObject info = root.getJSONObject("기본정보");
        return new LawDocument(LawDocument.LAW,
            required(info, "법령ID"),
            required(info, "법령명_한글").strip(),
            textOrContent(info.opt("법종구분")),
            info.optString("공포일자", ""),
            info.optString("시행일자", ""),
            textOrContent(info.opt("소관부처")),
            item.toString(),
            annexes(root.opt("별표")),
            List.of());
    }

    private static LawDocument parseAdminRule(JSONObject item, JSONObject root) {
        JSONObject info = root.getJSONObject("행정규칙기본정보");
        return new LawDocument(LawDocument.ADMIN_RULE,
            required(info, "행정규칙ID"),
            required(info, "행정규칙명").strip(),
            info.optString("행정규칙종류", ""),
            info.optString("발령일자", ""),
            info.optString("시행일자", ""),
            info.optString("소관부처명", ""),
            item.toString(),
            annexes(root.opt("별표")),
            attachments(root.opt("첨부파일")));
    }

    private static List<LawDocument.Annex> annexes(Object node) {
        List<LawDocument.Annex> result = new ArrayList<>();
        for (JSONObject unit : objects(node instanceof JSONObject o ? o.opt("별표단위") : node)) {
            List<String> images = new ArrayList<>();
            for (Object link : list(unit.opt("별표서식이미지파일링크"))) {
                String url = absolute(String.valueOf(link));
                if (url != null) {
                    images.add(url);
                }
            }
            result.add(new LawDocument.Annex(
                unit.optString("별표구분", ""),
                unit.optString("별표번호", ""),
                unit.optString("별표가지번호", ""),
                unit.optString("별표제목", ""),
                absolute(unit.optString("별표서식파일링크", "")),
                absolute(unit.optString("별표서식PDF파일링크", "")),
                images));
        }
        return result;
    }

    /** {@code 첨부파일}은 {@code {첨부파일링크: [...], 첨부파일명: [...]}} - 링크와 이름이 같은 순서의 병렬 배열이다. */
    private static List<LawDocument.Attachment> attachments(Object node) {
        List<LawDocument.Attachment> result = new ArrayList<>();
        for (JSONObject att : objects(node)) {
            List<Object> links = list(att.opt("첨부파일링크"));
            List<Object> names = list(att.opt("첨부파일명"));
            for (int i = 0; i < links.size(); i++) {
                String url = absolute(String.valueOf(links.get(i)));
                if (url != null) {
                    result.add(new LawDocument.Attachment(i < names.size() ? String.valueOf(names.get(i)) : "", url));
                }
            }
        }
        return result;
    }

    /** 상대경로는 법제처 호스트를 붙이고, http는 https로 올린다(http는 301로 어차피 넘어간다). */
    static String absolute(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String trimmed = link.strip();
        if (trimmed.startsWith("/")) {
            return LAW_HOST + trimmed;
        }
        if (trimmed.startsWith("http://")) {
            return "https://" + trimmed.substring("http://".length());
        }
        return trimmed;
    }

    private static String required(JSONObject o, String key) {
        String v = o.optString(key, "");
        if (v.isBlank()) {
            throw new IllegalArgumentException("필수 필드 없음: " + key);
        }
        return v;
    }

    /** {@code {"content": "법률", "법종구분코드": ...}} 형태와 단순 문자열 둘 다 받는다. */
    private static String textOrContent(Object v) {
        if (v instanceof JSONObject o) {
            return o.optString("content", "");
        }
        return v == null || JSONObject.NULL.equals(v) ? "" : v.toString();
    }

    private static List<Object> list(Object v) {
        if (v == null || JSONObject.NULL.equals(v) || "".equals(v)) {
            return List.of();
        }
        if (v instanceof JSONArray a) {
            return a.toList();
        }
        return List.of(v);
    }

    private static List<JSONObject> objects(Object v) {
        List<JSONObject> result = new ArrayList<>();
        if (v instanceof JSONObject o) {
            result.add(o);
        } else if (v instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) {
                if (a.get(i) instanceof JSONObject o) {
                    result.add(o);
                }
            }
        }
        return result;
    }
}
