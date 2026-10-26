// src/main/java/egovframework/external/publicdata/cleanser/KmaWarningAreaMatcher.java
package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.FacilityMasterRecord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기상특보 통보문(getWthrWrnMsg) 상세 구역(t2, t6)과 교정시설(행정구역) 간의 정밀 매칭기.
 *
 * <p>
 * 통보문 구역은 {@code 시·도(항목, 항목(부가정보), ...)} 구조다. 예: {@code 경기도(김포, 파주동북부)},
 * {@code 전라남도(신안(흑산면제외), 무안북부)}, {@code 인천(옹진 제외)}. 지명 문자열이 어디에든 들어 있으면
 * 매칭하는 방식은 다른 지역 이름에 우연히 들어 있는 글자("용인서북부"의 "서북", "서해남부전해상"의 "해남")에 걸려서
 * (2026-10-26 고객 문의 이후 실데이터 점검), 구조로 읽는다:
 * </p>
 * <ol>
 *   <li>해상 전용 특보(풍랑, 폭풍해일)와 해상 구역(앞바다·먼바다·전해상 등)은 육상 시설에 매칭하지 않는다.</li>
 *   <li>구역 텍스트가 없으면(통보문 조회 실패 포함) 매칭을 보류한다. 수집이 10분마다 같은 날 목록을 다시 받으므로
 *       다음 주기에 조회가 성공하면 그때 매칭된다. 구역을 모르는 채로 전 시설에 매칭하면 안 된다.</li>
 *   <li><b>시·도가 먼저 맞아야 한다.</b> 시·도가 맞은 토큰 안에서만 항목 단위로 시설의 시군구와 비교한다
 *       (항목 이름이 시군구 키워드로 시작해야 한다: "양주"는 "남양주"에 걸리지 않고 "파주동북부"는 "파주"에 걸린다).</li>
 *   <li>{@code X 제외}는 시·도 전체에서 나열한 곳만 빼는 뜻이다. 항목 안의 {@code 신안(흑산면제외)}는 신안으로 본다.</li>
 *   <li>기상청 본청(stnId=108, 전국) 특보라도 실제 구역에 해당 시설의 지명이 없으면 매칭 제외.</li>
 * </ol>
 */
public final class KmaWarningAreaMatcher {

    private static final Set<String> MARINE_TERMS = Set.of(
        "앞바다", "먼바다", "연안바다", "평수구역", "바깥먼바다", "안쪽먼바다", "해역", "전해상"
    );

    /** 시군구 키워드에서 뺄 방향 글자 - "천안시서북구"의 "서북"처럼 다른 지명의 일부와 겹친다. */
    private static final String DIRECTION_CHARS = "동서남북중";

    /** 광주 광역시의 자치구. 전남광주통합특별시(2026-07 통합) 소속 시설이 광주인지 전남인지 가르는 데 쓴다. */
    private static final Set<String> GWANGJU_DISTRICTS = Set.of("동구", "서구", "남구", "북구", "광산구");

    /**
     * 기상청 통보문이 시군구 대신 쓰는 예보구역 이름과 그 구역에 속한 시군구. 통보문에 "인천(강화, 인천남부, 인천북부)"처럼
     * 시군구가 아니라 구역 이름만 오면 시설의 시군구를 이 표로 찾아야 한다.
     * 인천만 확인했다(교정시설 중 인천구치소(미추홀구)가 해당). 구성은 기상청 공식 문서로 확인하지 못했다 -
     * 다른 시·도에서 같은 일이 생기면(정제기가 "알 수 없는 구역" 경고를 남긴다) 여기에 추가한다.
     */
    private static final Map<String, Set<String>> ZONE_MEMBERS = Map.of(
        "인천남부", Set.of("중구", "동구", "미추홀구", "연수구", "남동구"),
        "인천북부", Set.of("계양구", "서구", "부평구")
    );

    /** 통보문 최상위 구역 이름으로 알려진 시·도 표기. 여기에도 해상에도 없으면 {@link #unrecognizedAreas}가 알려준다. */
    private static final List<String> KNOWN_PROVINCE_NAMES = List.of(
        "서울", "경기", "인천", "강원", "충청북도", "충북", "충청남도", "충남", "전북", "전라북도", "전남", "전라남도",
        "광주", "경상북도", "경북", "경상남도", "경남", "제주", "대구", "부산", "울산", "대전", "세종", "전남광주통합특별시"
    );

    /** 시·도 이름은 아니지만 통보문 최상위에 정상적으로 나오는 이름. */
    private static final List<String> KNOWN_NON_PROVINCE_AREAS = List.of("울릉도.독도", "서해5도", "없 음");

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

        // 2. 통보문 구역(t2 우선, 없으면 t6). 둘 다 없으면 구역을 확인할 수 없으므로 매칭을 보류한다(다음 수집 주기에
        // 통보문 조회가 성공하면 정상 매칭됨). 예전에는 해상 키워드가 없는 육상 특보만 지점 관할 전체에 매칭했는데,
        // 2026-10-15 강풍주의보가 수도권 13개 시설 전부에 들어간 일이 있어 그만뒀다.
        String areaText = (t2 != null && !t2.isBlank()) ? t2 : t6;
        if (areaText == null || areaText.isBlank()) {
            return false;
        }
        return matchesAreaText(areaText, facility);
    }

    private static boolean containsMarineKeyword(String title) {
        return title.contains("풍랑") || title.contains("폭풍해일");
    }

    private static boolean containsLandKeyword(String title) {
        return title.contains("호우") || title.contains("대설") || title.contains("강풍")
            || title.contains("태풍") || title.contains("한파") || title.contains("폭염")
            || title.contains("건조") || title.contains("황사") || title.contains("안개");
    }

    /**
     * 육상 특보 없이 순수 해상 특보(풍랑, 폭풍해일)만 포함된 제목인지 판별.
     */
    public static boolean isPureMarineWarning(String title) {
        // 육상에 영향을 주는 특보가 하나라도 함께 있으면 복합 특보로 간주
        return containsMarineKeyword(title) && !containsLandKeyword(title);
    }

    /**
     * 통보문 구역에 시·도 이름으로도, 해상 구역으로도, 알려진 예외로도 설명되지 않는 최상위 이름이 있으면 돌려준다.
     * 기상청이 시·도 표기를 바꾸거나(행정구역 통합 등) 새 구역 이름을 쓰기 시작하면 시설이 조용히 매칭에서 빠지므로,
     * 정제기가 이 목록이 비어 있지 않을 때 경고를 남겨 사람이 알아채게 한다.
     */
    public static List<String> unrecognizedAreas(String t2, String t6) {
        Set<String> result = new LinkedHashSet<>();
        for (String text : new String[] {t2, t6}) {
            for (Area area : parseAreas(text)) {
                String top = area.top();
                if (top.isBlank() || top.startsWith("o ") || isMarineToken(top)) {
                    continue;
                }
                boolean known = KNOWN_PROVINCE_NAMES.stream().anyMatch(top::contains)
                    || KNOWN_NON_PROVINCE_AREAS.stream().anyMatch(top::contains);
                if (!known) {
                    result.add(top);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 통보문 구역 문자열(t2 또는 t6)과 시설의 시도/시군구 매칭.
     */
    private static boolean matchesAreaText(String areaText, FacilityMasterRecord facility) {
        List<String> sidoKeywords = getSidoKeywords(facility.sido(), facility.sigungu());
        List<String> sigunguKeywords = getSigunguKeywords(facility.sigungu());
        if (sidoKeywords.isEmpty()) {
            return false;
        }

        for (Area area : parseAreas(areaText)) {
            if (isMarineToken(area.top()) || !containsAny(area.top(), sidoKeywords)) {
                continue; // 해상 구역이거나 다른 시·도
            }
            if (area.items() == null) {
                return true; // "서울특별시", "경기도"처럼 시·도 전체 발표
            }
            if (area.exclusion()) {
                // "인천(옹진 제외)": 시·도 전체에서 나열한 곳만 뺀다
                if (area.items().stream().noneMatch(item -> itemMatches(item.name(), sigunguKeywords))) {
                    return true;
                }
                continue;
            }
            for (Item item : area.items()) {
                if (itemMatches(item.name(), sigunguKeywords)) {
                    return true;
                }
                for (Map.Entry<String, Set<String>> zone : ZONE_MEMBERS.entrySet()) {
                    if (item.name().startsWith(zone.getKey())
                        && sigunguKeywords.stream().anyMatch(zone.getValue()::contains)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 항목 이름이 시설 시군구 키워드와 같거나 그 키워드로 시작하는가 ("파주동북부"는 "파주"로 시작). */
    private static boolean itemMatches(String itemName, List<String> sigunguKeywords) {
        for (String k : sigunguKeywords) {
            if (itemName.equals(k) || itemName.startsWith(k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMarineToken(String token) {
        for (String term : MARINE_TERMS) {
            if (token.contains(term)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 통보문 파싱

    /** 항목 하나: {@code 이름} 또는 {@code 이름(부가정보)}. 부가정보는 비교에 쓰지 않는다. */
    private record Item(String name) {
    }

    /**
     * 최상위 구역 하나.
     *
     * @param top       괄호 앞 이름 (예: "전라남도", "인천")
     * @param items     괄호 안 항목들. 괄호가 없으면 null(시·도 전체)
     * @param exclusion 괄호 안이 "X 제외" 형태라 항목이 포함이 아니라 제외 대상인가
     */
    private record Area(String top, List<Item> items, boolean exclusion) {
    }

    private static List<Area> parseAreas(String text) {
        List<Area> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String line : text.split("\r?\n")) {
            String cleanLine = line.trim();
            if (cleanLine.isBlank()) {
                continue;
            }
            // 콜론(:) 앞은 "(1) 풍랑주의보 발표" 같은 머리말, 뒤가 구역 목록
            int colonIdx = cleanLine.indexOf(':');
            String header = colonIdx >= 0 ? cleanLine.substring(0, colonIdx) : "";
            // 풍랑·폭풍해일 줄의 구역은 바다다. 같은 통보문의 강풍 줄과 섞이지 않게 줄 단위로 버린다.
            if (containsMarineKeyword(header) && !containsLandKeyword(header)) {
                continue;
            }
            String section = colonIdx >= 0 ? cleanLine.substring(colonIdx + 1).trim() : cleanLine;
            for (String part : splitTopLevel(section)) {
                String token = part.trim();
                if (!token.isBlank()) {
                    result.add(parseArea(token));
                }
            }
        }
        return result;
    }

    private static Area parseArea(String token) {
        int open = token.indexOf('(');
        if (open < 0) {
            return new Area(token.trim(), null, false);
        }
        String top = token.substring(0, open).trim();
        String inner = innerOfParen(token, open);

        List<Item> items = new ArrayList<>();
        boolean exclusion = false;
        for (String raw : splitTopLevel(inner)) {
            String itemText = raw.trim();
            if (itemText.isBlank()) {
                continue;
            }
            // 항목 자체가 "옹진 제외"이면 이 구역 전체가 제외 표현이다. "신안(흑산면제외)"처럼 괄호가 있으면 항목의 부가정보.
            if (itemText.endsWith("제외") && itemText.indexOf('(') < 0) {
                exclusion = true;
                for (String name : itemText.substring(0, itemText.length() - 2).split("[,\\s]+")) {
                    if (!name.isBlank()) {
                        items.add(new Item(name.trim()));
                    }
                }
                continue;
            }
            int paren = itemText.indexOf('(');
            items.add(new Item((paren >= 0 ? itemText.substring(0, paren) : itemText).trim()));
        }
        return new Area(top, items, exclusion);
    }

    /** {@code open} 위치의 여는 괄호와 짝이 맞는 닫는 괄호까지의 안쪽. 닫는 괄호가 없으면 끝까지. */
    private static String innerOfParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return s.substring(open + 1, i);
                }
            }
        }
        return s.substring(open + 1);
    }

    /** 괄호 안의 쉼표는 무시하고 바깥 쉼표로만 분리. */
    private static List<String> splitTopLevel(String text) {
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

    // ---------------------------------------------------------------- 시설 쪽 키워드

    private static List<String> getSidoKeywords(String sido, String sigungu) {
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
        else if (sido.contains("전남") && sido.contains("광주")) {
            // 전남광주통합특별시: 기상청은 아직 광주와 전라남도를 따로 쓴다. 시설의 시군구로 어느 쪽인지 가른다.
            if (sigungu != null && GWANGJU_DISTRICTS.contains(sigungu)) {
                list.add("광주");
            } else {
                list.add("전남");
                list.add("전라남도");
            }
        }
        else if (sido.startsWith("전남") || sido.startsWith("전라남")) { list.add("전남"); list.add("전라남도"); }
        else if (sido.contains("광주")) list.add("광주");
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
        List<String> derived = new ArrayList<>();

        // 시/군/구 복합 명칭 분리 (예: "안양시동안구" -> "안양", "동안구")
        int siIdx = sigungu.indexOf('시');
        int gunIdx = sigungu.indexOf('군');
        int guIdx = sigungu.lastIndexOf('구');

        if (siIdx > 0) {
            String siName = sigungu.substring(0, siIdx);
            derived.add(siName);
            derived.add(siName + "시");
            if (guIdx > siIdx) {
                String guName = sigungu.substring(siIdx + 1);
                derived.add(guName);
                if (guName.endsWith("구")) {
                    derived.add(guName.substring(0, guName.length() - 1));
                }
            }
        } else if (gunIdx > 0) {
            String gunName = sigungu.substring(0, gunIdx);
            derived.add(gunName);
            derived.add(gunName + "군");
        } else if (guIdx > 0) {
            String guName = sigungu.substring(0, guIdx);
            derived.add(guName);
            derived.add(guName + "구");
        }

        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(sigungu);
        for (String k : derived) {
            // 한 글자("포항시북구"의 "북")와 방향 글자만으로 된 이름("천안시서북구"의 "서북구", "서북")은 다른 지명에도 있다.
            if (k.length() >= 2 && !isDirectionOnly(k)) {
                keywords.add(k);
            }
        }
        return new ArrayList<>(keywords);
    }

    /** "서북", "동남구"처럼 방향 글자(와 끝의 "구")로만 된 이름인가. */
    private static boolean isDirectionOnly(String name) {
        String core = name.endsWith("구") ? name.substring(0, name.length() - 1) : name;
        if (core.isEmpty()) {
            return false;
        }
        for (char c : core.toCharArray()) {
            if (DIRECTION_CHARS.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
