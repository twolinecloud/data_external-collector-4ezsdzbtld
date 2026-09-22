// src/main/java/egovframework/external/publicdata/cleanser/KmaWarningAreaMatcher.java
package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.FacilityMasterRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기상특보 통보문(getWthrWrnMsg) 상세 구역(t2, t6)과 교정시설(행정구역) 간의 정밀 매칭기.
 *
 * <p>
 * 1. 해상 전용 특보(풍랑, 폭풍해일)는 육상 교정시설에 매칭하지 않음.<br>
 * 2. 복합 특보(강풍주의보·풍랑주의보 등)는 육상 특보에 해당하는 발효 구역(t2, t6)만 추출하여
 *    시설의 시·도 및 시·군·구 지명과 대조함.<br>
 * 3. 기상청 본청(stnId=108, 전국) 특보라도 실제 구역에 해당 시설의 지명이 없으면 매칭 제외.
 * </p>
 */
public final class KmaWarningAreaMatcher {

    private static final Pattern PAREN_PATTERN = Pattern.compile("\\(([^)]+)\\)");
    private static final Set<String> MARINE_TERMS = Set.of(
        "앞바다", "먼바다", "연안바다", "평수구역", "바깥먼바다", "안쪽먼바다", "해역"
    );

    private KmaWarningAreaMatcher() {
    }

    /**
     * 특보 발표문(제목/t1/t2/t6)이 해당 교정시설의 관할 구역에 해당하는지 판별.
     *
     * @param title    특보 제목 (예: "[특보] 제09-102호... / 풍랑주의보 발표 (*)")
     * @param t1       통보문 t1 필드 (특보 현상 목록)
     * @param t2       통보문 t2 필드 (이번 발표/해제 구역 목록)
     * @param t6       통보문 t6 필드 (현재 발효 중인 전체 구역 목록)
     * @param facility 교정시설 마스터 레코드 (facilityId, facilityName, sido, sigungu)
     * @return 매칭 여부
     */
    public static boolean matches(String title, String t1, String t2, String t6, FacilityMasterRecord facility) {
        // 1. 순수 해상 전용 특보 필터링 (풍랑/폭풍해일 단독)
        String combinedTitle = (title != null ? title : "") + " " + (t1 != null ? t1 : "");
        if (isPureMarineWarning(combinedTitle)) {
            return false;
        }

        // 2. 통보문 구역(t2 우선, 없으면 t6)이 존재하는 경우 구역 매칭
        String areaText = (t2 != null && !t2.isBlank()) ? t2 : t6;
        if (areaText != null && !areaText.isBlank()) {
            return matchesAreaText(areaText, facility);
        }

        // 3. 통보문 구역 텍스트가 없는 경우(getWthrWrnMsg 조회 실패 포함, 2026-09-22 실측 -
        // 서울동부구치소에 관할 지역과 무관한 풍랑·강풍 복합특보가 매칭된 고객 문의의 원인). 해상
        // 키워드가 섞인 복합특보는 구역을 확인 못 한 채로 매칭 허용하면 해상 특보가 내륙 시설에
        // 다시 새는 원래 버그가 재현되므로, 이번 주기는 매칭을 보류한다(다음 주기 통보문 조회가
        // 성공하면 정상 매칭됨). 해상 키워드가 아예 없는 순수 육상 특보(호우 등)만 레거시대로
        // 지점 관할 전체에 매칭 허용한다.
        if (containsMarineKeyword(combinedTitle)) {
            return false;
        }
        return true;
    }

    private static boolean containsMarineKeyword(String title) {
        return title.contains("풍랑") || title.contains("폭풍해일");
    }

    /**
     * 육상 특보 없이 순수 해상 특보(풍랑, 폭풍해일)만 포함된 제목인지 판별.
     */
    public static boolean isPureMarineWarning(String title) {
        boolean hasMarine = containsMarineKeyword(title);
        if (!hasMarine) {
            return false;
        }
        // 육상에 영향을 주는 특보가 하나라도 함께 있으면 복합 특보로 간주
        boolean hasLand = title.contains("호우") || title.contains("대설") || title.contains("강풍")
            || title.contains("태풍") || title.contains("한파") || title.contains("폭염")
            || title.contains("건조") || title.contains("황사") || title.contains("안개");
        return !hasLand;
    }

    /**
     * 통보문 구역 문자열(t2 또는 t6)과 시설의 시도/시군구 매칭.
     */
    private static boolean matchesAreaText(String areaText, FacilityMasterRecord facility) {
        List<String> tokens = extractLandTokens(areaText);
        if (tokens.isEmpty()) {
            // 육상 구역이 하나도 없고 해상 구역만 있으면 매칭되지 않음
            return false;
        }

        List<String> sidoKeywords = getSidoKeywords(facility.sido());
        List<String> sigunguKeywords = getSigunguKeywords(facility.sigungu());

        for (String token : tokens) {
            // 토큰 예: "서울특별시", "경기도(가평, 성남, 의왕)", "울릉도.독도", "강원도(춘천, 원주)"
            // 1) 시군구 지명이 토큰에 직접 포함되어 있는가? (예: "울릉도.독도" -> 의왕/송파에는 없음)
            for (String sgg : sigunguKeywords) {
                if (token.contains(sgg)) {
                    return true;
                }
            }

            // 2) 시도 지명이 토큰에 포함되어 있는가?
            for (String sido : sidoKeywords) {
                if (token.contains(sido)) {
                    // 시도명 뒤에 괄호 (시군구 나열)가 있는 경우 검사
                    Matcher m = PAREN_PATTERN.matcher(token);
                    if (m.find()) {
                        String insideParen = m.group(1);
                        // 괄호 안에 시설의 시군구 키워드가 포함되어 있는지 확인
                        for (String sgg : sigunguKeywords) {
                            if (insideParen.contains(sgg)) {
                                return true;
                            }
                        }
                        // 괄호가 있는데 해당 시군구가 없으면 이 토큰은 다른 시군구 대상임
                    } else {
                        // 괄호 없이 시도 단위 전체 발표인 경우 (예: "서울특별시", "경기도", "제주도")
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 통보문 문자열에서 해상 구역을 제외하고 육상 구역 조각(토큰)만 추출.
     */
    private static List<String> extractLandTokens(String text) {
        List<String> result = new ArrayList<>();
        // 줄바꿈으로 먼저 분리
        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            String cleanLine = line.trim();
            if (cleanLine.isBlank()) {
                continue;
            }
            // 콜론(:) 뒤의 구역 목록을 취함 (예: "(1) 풍랑주의보 발표 : 제주도..." -> "제주도...")
            int colonIdx = cleanLine.indexOf(':');
            String section = colonIdx >= 0 ? cleanLine.substring(colonIdx + 1).trim() : cleanLine;

            // 쉼표(,)로 쪼개되, 괄호 내부의 쉼표는 쪼개지 않음
            List<String> parts = splitRespectingParens(section);
            for (String part : parts) {
                String token = part.trim();
                if (token.isBlank() || isMarineToken(token)) {
                    continue;
                }
                result.add(token);
            }
        }
        return result;
    }

    private static boolean isMarineToken(String token) {
        for (String term : MARINE_TERMS) {
            if (token.contains(term)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 괄호 안의 쉼표는 무시하고 바깥 쉼표로만 분리.
     */
    private static List<String> splitRespectingParens(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            }

            if (c == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        return parts;
    }

    private static List<String> getSidoKeywords(String sido) {
        if (sido == null || sido.isBlank()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        list.add(sido);
        if (sido.startsWith("서울")) list.add("서울");
        else if (sido.startsWith("경기")) list.add("경기");
        else if (sido.startsWith("인천")) list.add("인천");
        else if (sido.startsWith("강원")) list.add("강원");
        else if (sido.startsWith("충청북") || sido.equals("충북")) { list.add("충북"); list.add("충청북도"); }
        else if (sido.startsWith("충청남") || sido.equals("충남")) { list.add("충남"); list.add("충청남도"); }
        else if (sido.startsWith("전북") || sido.startsWith("전라북")) { list.add("전북"); list.add("전라북도"); }
        else if (sido.startsWith("전남") || sido.startsWith("전라남") || sido.contains("광주")) {
            list.add("전남"); list.add("전라남도"); list.add("광주");
        }
        else if (sido.startsWith("경상북") || sido.equals("경북")) { list.add("경북"); list.add("경상북도"); }
        else if (sido.startsWith("경상남") || sido.equals("경남")) { list.add("경남"); list.add("경상남도"); }
        else if (sido.startsWith("제주")) { list.add("제주"); list.add("제주도"); }
        else if (sido.startsWith("대구")) list.add("대구");
        else if (sido.startsWith("부산")) list.add("부산");
        else if (sido.startsWith("울산")) list.add("울산");
        else if (sido.startsWith("대전")) list.add("대전");
        else if (sido.startsWith("세종")) list.add("세종");
        return list;
    }

    private static List<String> getSigunguKeywords(String sigungu) {
        if (sigungu == null || sigungu.isBlank()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        list.add(sigungu);

        // 시/군/구 복합 명칭 분리 (예: "안양시동안구" -> "안양", "동안구")
        int siIdx = sigungu.indexOf('시');
        int gunIdx = sigungu.indexOf('군');
        int guIdx = sigungu.lastIndexOf('구');

        if (siIdx > 0) {
            String siName = sigungu.substring(0, siIdx);
            list.add(siName);
            list.add(siName + "시");
            if (guIdx > siIdx) {
                String guName = sigungu.substring(siIdx + 1);
                list.add(guName);
                if (guName.endsWith("구")) {
                    list.add(guName.substring(0, guName.length() - 1));
                }
            }
        } else if (gunIdx > 0) {
            String gunName = sigungu.substring(0, gunIdx);
            list.add(gunName);
            list.add(gunName + "군");
        } else if (guIdx > 0) {
            String guName = sigungu.substring(0, guIdx);
            list.add(guName);
            list.add(guName + "구");
        }

        return list;
    }
}
