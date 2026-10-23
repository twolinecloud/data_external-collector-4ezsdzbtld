package egovframework.external.publicdata.transfer;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * ZIP 안의 경로를 정하는 곳 - <b>폴더 배치 규칙은 전부 여기에만 있다.</b> Xenon이 유형 폴더 구조를 받아주지
 * 않으면 {@link #documentDir}만 {@code law/<법령명>}으로 바꾸면 된다.
 *
 * <pre>
 * law/{법률,시행령,시행규칙}/&lt;법령명&gt;/&lt;법령명&gt;.json
 *                                  /별표/별표1.hwp, 별표1.pdf, 서식3의2.hwp ...
 * admrul/{훈령,예규,...}/&lt;규칙명&gt;/&lt;규칙명&gt;.json
 *                                  /별표/...   /첨부/첨부1.hwp ...
 * </pre>
 *
 * <p><b>유형 분류는 이름이 아니라 법종구분 값으로 한다</b> - 제목 끝("...법/...령/...규칙")으로 나누면 법률 293건
 * 중 113건이 틀렸다(2026-10 전수 확인). 대통령령은 "시행령", 부령·총리령·대법원규칙은 "시행규칙" 폴더로 가는데,
 * 상위 법률이 따로 없는 독립 대통령령/부령도 그렇게 분류되므로 폴더 이름은 법적 의미가 아니라 유형 표시다.
 * 법률이나 대통령령·부령이 아닌 값(대통령긴급명령 등)은 "법률"로 둔다.</p>
 */
public final class LawArchiveLayout {

    public static final String LAW_TOP = "law";
    public static final String ADMRUL_TOP = "admrul";

    private static final String CATEGORY_ACT = "법률";
    private static final String CATEGORY_DECREE = "시행령";
    private static final String CATEGORY_RULE = "시행규칙";
    private static final String CATEGORY_UNKNOWN = "기타";

    /** UTF-8 기준 폴더/파일 이름 최대 바이트 - 실측 최대는 141바이트이고, 대부분의 파일시스템 한계(255)에 여유를 둔다. */
    private static final int MAX_NAME_BYTES = 200;

    private LawArchiveLayout() {
    }

    public static String topFolder(LawDocument doc) {
        return LawDocument.ADMIN_RULE.equals(doc.docType()) ? ADMRUL_TOP : LAW_TOP;
    }

    public static String category(LawDocument doc) {
        String kind = doc.kind() == null ? "" : doc.kind().strip();
        if (LawDocument.ADMIN_RULE.equals(doc.docType())) {
            return kind.isEmpty() ? CATEGORY_UNKNOWN : sanitize(kind);
        }
        if ("대통령령".equals(kind)) {
            return CATEGORY_DECREE;
        }
        if (kind.endsWith("부령") || kind.endsWith("총리령") || kind.endsWith("규칙")) {
            return CATEGORY_RULE;
        }
        return CATEGORY_ACT;
    }

    /** {@code law/시행령/<법령명>} - 문서 1건의 폴더. 같은 이름이 둘이면 호출부가 {@code dirName}에 법령ID를 붙여 부른다. */
    public static String documentDir(LawDocument doc, String dirName) {
        return topFolder(doc) + "/" + category(doc) + "/" + dirName;
    }

    public static String bodyPath(String documentDir, String dirName) {
        return documentDir + "/" + dirName + ".json";
    }

    /**
     * 별표 파일의 확장자를 뺀 이름: 구분 + 번호(앞의 0 제거) + 가지번호("의N"). 법제처 표기와 같다(서식86의2).
     * 번호가 "0000"(번호 없는 단일 별표)이면 구분만 쓴다.
     */
    public static String annexBaseName(LawDocument.Annex annex) {
        String category = annex.category() == null || annex.category().isBlank() ? "별표" : sanitize(annex.category());
        StringBuilder sb = new StringBuilder(category);
        String number = stripLeadingZeros(annex.number());
        if (!number.isEmpty()) {
            sb.append(number);
            String branch = stripLeadingZeros(annex.branch());
            if (!branch.isEmpty()) {
                sb.append('의').append(branch);
            }
        }
        return sb.toString();
    }

    /** 첨부는 원본 파일명이 길고 한글/공백이 많아 번호로 쓴다. 원본 이름은 manifest에 남는다. */
    public static String attachmentBaseName(int index) {
        return "첨부" + index;
    }

    /**
     * 폴더/파일 이름으로 쓸 수 없는 문자를 바꾼다: 경로 구분자·윈도우 금지 문자·제어문자는 '_', 앞뒤 공백과
     * 끝의 '.'은 제거(윈도우에서 열리지 않는다 - 응답 이름에 끝 공백이 있는 사례가 있었다), NFC 정규화(맥에서 만든 NFD
     * 이름과의 불일치 방지), 바이트 길이 제한.
     */
    public static String sanitize(String raw) {
        String s = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean bad = c < 0x20 || c == 0x7f || "/\\:*?\"<>|".indexOf(c) >= 0;
            sb.append(bad ? '_' : c);
        }
        String cleaned = sb.toString().strip();
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        cleaned = truncateBytes(cleaned, MAX_NAME_BYTES);
        return cleaned.isEmpty() ? "_" : cleaned;
    }

    /** 대소문자만 다른 이름도 같은 폴더로 보는 파일시스템이 있어 중복 판정용 키로 쓴다. */
    public static String collisionKey(String path) {
        return path.toLowerCase(Locale.ROOT);
    }

    private static String stripLeadingZeros(String digits) {
        if (digits == null) {
            return "";
        }
        String s = digits.strip().replaceFirst("^0+", "");
        return s;
    }

    private static String truncateBytes(String s, int maxBytes) {
        if (s.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int bytes = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + len > maxBytes) {
                break;
            }
            sb.appendCodePoint(cp);
            bytes += len;
            i += Character.charCount(cp);
        }
        return sb.toString().strip();
    }
}
