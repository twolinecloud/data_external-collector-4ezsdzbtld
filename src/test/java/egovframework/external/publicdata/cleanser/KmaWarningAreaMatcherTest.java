// src/test/java/egovframework/external/publicdata/cleanser/KmaWarningAreaMatcherTest.java
package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.FacilityMasterRecord;
import egovframework.external.publicdata.collector.KmaWarningZone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmaWarningAreaMatcherTest {

    private final FacilityMasterRecord seoulDongbu = new FacilityMasterRecord("1270801", "서울동부구치소", "서울특별시", "송파구", "62", "125");
    private final FacilityMasterRecord seoulGuchiso = new FacilityMasterRecord("1270552", "서울구치소", "경기도", "의왕시", "60", "123");
    private final FacilityMasterRecord anyang = new FacilityMasterRecord("1270782", "안양교도소", "경기도", "안양시동안구", "60", "122");
    private final FacilityMasterRecord yeongwol = new FacilityMasterRecord("1272038", "영월교도소", "강원특별자치도", "영월군", "86", "119");
    private final FacilityMasterRecord pohang = new FacilityMasterRecord("1270537", "포항교도소", "경상북도", "포항시북구", "102", "94");

    @Test
    void 순수_풍랑주의보는_모든_육상시설에_매칭되지_않는다() {
        String title = "[특보] 제09-102호 : 2026.09.22.06:00 / 풍랑주의보 발표 (*)";
        String t1 = "풍랑주의보 발표";
        String t2 = "(1) 풍랑주의보 발표 : 제주도남쪽바깥먼바다, 제주도남동쪽안쪽먼바다";
        String t6 = "o 풍랑주의보 : 동해남부앞바다, 제주도남쪽바깥먼바다";

        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, seoulDongbu)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, seoulGuchiso)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, yeongwol)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, pohang)).isFalse();
    }

    @Test
    void 강풍과_풍랑_복합특보에서_울릉도만_해당되면_내륙시설은_매칭되지_않는다() {
        String title = "[특보] 제09-100호 : 2026.09.21.17:00 / 강풍주의보·풍랑주의보 발표 (*)";
        String t1 = "강풍주의보·풍랑주의보 발표";
        String t2 = "(1) 풍랑주의보 발표 : 동해남부북쪽안쪽먼바다\n(2) 강풍주의보 발표 : 울릉도.독도";
        String t6 = "o 강풍주의보 : 울릉도.독도\no 풍랑주의보 : 동해남부먼바다";

        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, seoulDongbu)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, seoulGuchiso)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, yeongwol)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, pohang)).isFalse();
    }

    @Test
    void 시도_전체_호우주의보는_해당_시도_소속_시설에_매칭된다() {
        String title = "[특보] 제08-50호 : 호우주의보 발표";
        String t1 = "호우주의보 발표";
        String t2 = "(1) 호우주의보 발표 : 서울특별시";

        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, null, seoulDongbu)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, null, seoulGuchiso)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, null, yeongwol)).isFalse();
    }

    @Test
    void 구역_텍스트가_없는_복합_해상특보는_통보문_조회_실패로_보고_매칭을_보류한다() {
        // 2026-09-22 실측: getWthrWrnMsg 조회 실패로 t2/t6가 비면(서울동부구치소 문의 원인),
        // 해상 키워드가 섞인 복합특보는 구역을 모르는 채로 매칭 허용하면 안 된다 - 전부 제외.
        String title = "[특보] 제09-100호 : 2026.09.21.17:00 / 강풍주의보·풍랑주의보 발표 (*)";
        String t1 = "강풍주의보·풍랑주의보 발표";

        assertThat(KmaWarningAreaMatcher.matches(title, t1, null, null, seoulDongbu)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, null, null, seoulGuchiso)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, null, null, yeongwol)).isFalse();
    }

    @Test
    void 구역_텍스트가_없으면_육상특보도_매칭을_보류한다() {
        // 2026-10-15 제10-8호 강풍주의보가 통보문 조회 실패로 구역 없이 와서 수도권 13개 시설 전부에 들어간 일이 있다.
        // 구역을 모르는 채로 매칭하지 않고, 다음 수집 주기에 조회가 성공하면 그때 매칭한다.
        String title = "[특보] 제10-8호 : 2026.10.15.06:30 / 강풍주의보 발표 (*)";

        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 발표", null, null, seoulDongbu)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 발표", "", "  ", yeongwol)).isFalse();
    }

    @Test
    void 시군구가_명시된_경우_해당_시군구_시설에만_매칭된다() {
        String title = "[특보] 제08-51호 : 호우주의보 발표";
        String t1 = "호우주의보 발표";
        String t2 = "(1) 호우주의보 발표 : 경기도(의왕, 안양, 성남), 강원도(영월)";

        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, null, seoulDongbu)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, null, seoulGuchiso)).isTrue(); // 의왕시
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, null, anyang)).isTrue();        // 안양시
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, null, yeongwol)).isTrue();     // 영월군
        assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, null, pohang)).isFalse();
    }

    // --- 2026-10-26 고객 문의: 인천구치소 미매칭, 한 글자 키워드 오매칭 ---

    private final FacilityMasterRecord incheon = new FacilityMasterRecord("1270802", "인천구치소", "인천광역시", "미추홀구", "55", "124");
    private final FacilityMasterRecord ongjin = new FacilityMasterRecord("9999001", "옹진가상시설", "인천광역시", "옹진군", "55", "124");
    private final FacilityMasterRecord daejeonJung = new FacilityMasterRecord("1270280", "대전지방교정청", "대전광역시", "중구", "67", "100");
    private final FacilityMasterRecord gwangjuBuk = new FacilityMasterRecord("1270344", "광주교도소", "전남광주통합특별시", "북구", "58", "74");

    @Test
    void 인천_옹진_제외_표현은_옹진을_뺀_인천_전체로_해석한다() {
        String title = "[특보] 제10-16호 : 2026.10.18.09:00 / 강풍주의보 발표 (*)";
        String t2 = "(1) 강풍주의보 발표 : 서울특별시, 인천(옹진 제외), 경기도(수원, 성남)";

        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 발표", t2, null, incheon)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 발표", t2, null, ongjin)).isFalse();
    }

    @Test
    void 인천_구역이_예보구역_이름으로_오면_시설의_구로_찾는다() {
        String title = "[특보] 제10-33호 : 2026.10.18.19:40 / 강풍주의보 해제 (*)";
        String t2 = "(1) 강풍주의보 해제 : 인천(강화, 인천남부, 인천북부)";

        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 해제", t2, null, incheon)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 해제", t2, null, seoulDongbu)).isFalse();
    }

    @Test
    void 인천남부만_나오면_남부에_속한_미추홀구만_매칭한다() {
        String title = "[특보] 제10-40호 : 2026.10.19.01:00 / 강풍주의보 발표 (*)";
        FacilityMasterRecord gyeyang = new FacilityMasterRecord("9999002", "계양가상시설", "인천광역시", "계양구", "55", "125");

        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 발표", "(1) 강풍주의보 발표 : 인천(인천남부)", null, incheon)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 발표", "(1) 강풍주의보 발표 : 인천(인천남부)", null, gyeyang)).isFalse();
    }

    @Test
    void 한_글자_키워드_때문에_다른_지역_통보문이_포항에_매칭되지_않는다() {
        String title = "[특보] 제10-129호 : 2026.10.22.00:00 / 강풍주의보 해제 (*)";
        String t1 = "강풍주의보 해제";
        String t6 = "o 강풍주의보 : 경상남도(거제)";

        for (String t2 : new String[] {
            "(1) 강풍주의보 해제 : 전북자치도(부안위도면, 고창)",
            "(1) 강풍주의보 해제 : 충청북도(청주, 충주)",
            "(1) 강풍주의보 해제 : 안동북부, 영주",
        }) {
            assertThat(KmaWarningAreaMatcher.matches(title, t1, t2, t6, pohang)).as(t2).isFalse();
        }
        // 경상북도 전체를 발표하는 통보문은 그대로 매칭된다.
        assertThat(KmaWarningAreaMatcher.matches(title, t1, "(1) 강풍주의보 해제 : 경상북도", t6, pohang)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, "(1) 강풍주의보 해제 : 경상북도(포항, 경주)", t6, pohang)).isTrue();
    }

    @Test
    void 중구_북구처럼_흔한_구_이름은_시도가_같을_때만_매칭한다() {
        String title = "[특보] 제10-50호 : 2026.10.20.10:00 / 호우주의보 발표 (*)";

        assertThat(KmaWarningAreaMatcher.matches(title, "호우주의보 발표", "(1) 호우주의보 발표 : 부산광역시(중구, 북구)", null, daejeonJung)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, "호우주의보 발표", "(1) 호우주의보 발표 : 부산광역시(중구, 북구)", null, gwangjuBuk)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, "호우주의보 발표", "(1) 호우주의보 발표 : 대전광역시(중구, 서구)", null, daejeonJung)).isTrue();
    }

    // --- 2026-10-26 실데이터 점검: 구조 해석, 키워드, 해상 표기, 통합 시도 ---

    private final FacilityMasterRecord cheonan = new FacilityMasterRecord("1270394", "천안교도소", "충청남도", "천안시서북구", "63", "110");
    private final FacilityMasterRecord haenam = new FacilityMasterRecord("1272045", "해남교도소", "전남광주통합특별시", "해남군", "52", "38");
    private final FacilityMasterRecord mokpo = new FacilityMasterRecord("1270453", "목포교도소", "전남광주통합특별시", "무안군", "50", "67");
    private final FacilityMasterRecord jeju = new FacilityMasterRecord("1270510", "제주교도소", "제주특별자치도", "제주시", "53", "38");
    private final FacilityMasterRecord namyangju = new FacilityMasterRecord("9999003", "양주가상시설", "경기도", "양주시", "61", "129");

    private static final String LAND_TITLE = "[특보] 제10-1호 : 2026.10.18.06:00 / 강풍주의보 발표 (*)";
    private static final String COMBO_TITLE = "[특보] 제10-71호 : 2026.10.18.06:00 / 강풍주의보·풍랑주의보 발표 (*)";

    @Test
    void 중첩_괄호가_있는_토큰은_괄호_안_항목_단위로_비교한다() {
        // 실제 통보문 형태 - 신안(흑산면제외), 영광(낙월면 제외) 같은 부가정보가 항목 뒤에 붙는다(256회 관측).
        String t2 = "(1) 강풍주의보 발표 : 전라남도(여수, 영암, 신안(흑산면제외), 진도, 영광(낙월면 제외), 완도(여서도 제외), 무안북부, 무안남부)";

        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", t2, null, mokpo)).isTrue();   // 무안북부
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", t2, null, haenam)).isFalse();  // 목록에 없음
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", t2, null, gwangjuBuk)).isFalse(); // 광주는 전라남도가 아니다
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", t2, null, seoulDongbu)).isFalse();
    }

    @Test
    void 중첩_괄호_뒤에_나오는_항목도_놓치지_않는다() {
        String t2 = "(1) 강풍주의보 발표 : 전라남도(신안(흑산면제외), 진도, 해남북부)";

        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", t2, null, haenam)).isTrue();
    }

    @Test
    void 방향_구_이름에서_만든_키워드가_다른_지역_구역에_걸리지_않는다() {
        String t2 = "(1) 강풍주의보 발표 : 경기도(용인서북부, 파주서북부), 전라남도(나주서북부)";

        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", t2, null, cheonan)).isFalse();
        // 충청남도 전체나 천안이 명시되면 매칭된다
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", "(1) 강풍주의보 발표 : 충청남도(천안, 아산)", null, cheonan)).isTrue();
    }

    @Test
    void 항목은_시군구_이름으로_시작해야_하므로_남양주는_양주에_걸리지_않는다() {
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", "(1) 강풍주의보 발표 : 경기도(남양주, 구리)", null, namyangju)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, "강풍주의보 발표", "(1) 강풍주의보 발표 : 경기도(양주, 구리)", null, namyangju)).isTrue();
    }

    @Test
    void 전해상은_해상_구역이라_육상_시설에_걸리지_않는다() {
        String t1 = "강풍주의보·풍랑주의보 발표";

        // "서해남부전해상"에는 "해남", "제주도전해상"에는 "제주"가 들어 있다
        String t2 = "(1) 풍랑주의보 발표 : 서해남부전해상, 제주도전해상, 동해중부전해상\n(2) 강풍주의보 발표 : 울릉도.독도";
        assertThat(KmaWarningAreaMatcher.matches(COMBO_TITLE, t1, t2, null, haenam)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(COMBO_TITLE, t1, t2, null, jeju)).isFalse();
    }

    @Test
    void 풍랑_줄의_구역은_강풍_줄과_섞이지_않는다() {
        // 풍랑 줄에 육상 이름처럼 보이는 것이 있어도(서해5도 등) 풍랑 줄은 바다 구역이다
        String t2 = "(1) 풍랑주의보 발표 : 인천(옹진)\n(2) 강풍주의보 발표 : 울릉도.독도";

        assertThat(KmaWarningAreaMatcher.matches(COMBO_TITLE, "강풍주의보·풍랑주의보 발표", t2, null, incheon)).isFalse();
    }

    @Test
    void 전남광주통합특별시는_광주_자치구와_전남_시군을_구분한다() {
        String jeonnamOnly = "(1) 호우주의보 발표 : 전라남도";
        String gwangjuOnly = "(1) 호우주의보 발표 : 광주";
        String t1 = "호우주의보 발표";
        String title = "[특보] 제10-2호 / 호우주의보 발표 (*)";

        assertThat(KmaWarningAreaMatcher.matches(title, t1, jeonnamOnly, null, mokpo)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, jeonnamOnly, null, gwangjuBuk)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, gwangjuOnly, null, gwangjuBuk)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, gwangjuOnly, null, mokpo)).isFalse();
    }

    @Test
    void 알_수_없는_최상위_구역_이름을_알려준다() {
        // 기상청이 시·도 표기를 바꾸면 시설이 조용히 매칭에서 빠지므로 정제기가 경고를 남길 수 있어야 한다
        assertThat(KmaWarningAreaMatcher.unrecognizedAreas("(1) 호우주의보 발표 : 서울특별시, 경기도(수원), 서해전해상, 울릉도.독도", null)).isEmpty();
        assertThat(KmaWarningAreaMatcher.unrecognizedAreas("(1) 호우주의보 발표 : 신설광역시(포항), 경기도(수원)", null))
            .containsExactly("신설광역시");
    }

    @Test
    void 인천영종은_별도_구역이라_미추홀구_시설에_매칭되지_않는다() {
        String title = "[특보] 제10-41호 : 2026.10.19.02:00 / 강풍주의보 발표 (*)";

        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 발표", "(1) 강풍주의보 발표 : 인천(인천영종, 강화)", null, incheon)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, "강풍주의보 발표", "(1) 강풍주의보 발표 : 인천(인천영종, 인천남부)", null, incheon)).isTrue();
    }

    // --- 2026-10-27 시설 위치로 계산한 특보 세분 구역(KmaWarningZone) ---

    private final FacilityMasterRecord hongseong = new FacilityMasterRecord("1270524", "홍성교도소", "충청남도", "홍성군", "55", "106");
    private final FacilityMasterRecord gunsan = new FacilityMasterRecord("1270460", "군산교도소", "전북특별자치도", "군산시", "56", "92");
    private static final KmaWarningZone HONGSEONG_EAST = new KmaWarningZone("L1032010", "홍성동부", "홍성", 8.3, "홍성서부", 12.9);
    private static final KmaWarningZone GUNSAN_MAINLAND = new KmaWarningZone("L1061610", "군산(옥도면 제외)", "군산", 9.3, null, null);
    private static final KmaWarningZone SEOUL_SOUTHEAST = new KmaWarningZone("L1100100", "서울동남권", "서울", 1.2, "서울동북권", 6.7);

    @Test
    void 세분_구역을_알면_같은_시군의_다른_구역에는_매칭하지_않는다() {
        String t1 = "강풍주의보 발표";

        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, t1, "(1) 강풍주의보 발표 : 충청남도(서산, 홍성서부)", null, hongseong, HONGSEONG_EAST)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, t1, "(1) 강풍주의보 발표 : 충청남도(서산, 홍성동부)", null, hongseong, HONGSEONG_EAST)).isTrue();
        // 시군 전체가 나오면 매칭
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, t1, "(1) 강풍주의보 발표 : 충청남도(홍성)", null, hongseong, HONGSEONG_EAST)).isTrue();
        // 구역을 모르면 예전처럼 시군 단위(놓치지 않는 쪽)
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, t1, "(1) 강풍주의보 발표 : 충청남도(서산, 홍성서부)", null, hongseong, null)).isTrue();
    }

    @Test
    void 육지_시설은_섬_구역_특보에_매칭되지_않는다() {
        // 2026-10-27 실제 통보문: 군산교도소가 군산어청도·군산옥도면 특보에 매칭됐다
        String t1 = "강풍주의보 발표";

        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, t1, "(1) 강풍주의보 발표 : 전북자치도(군산어청도)", null, gunsan, GUNSAN_MAINLAND)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, t1, "(1) 강풍주의보 발표 : 전북자치도(군산옥도면(어청도 제외))", null, gunsan, GUNSAN_MAINLAND)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, t1, "(1) 강풍주의보 발표 : 전북자치도(군산(옥도면 제외), 김제)", null, gunsan, GUNSAN_MAINLAND)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(LAND_TITLE, t1, "(1) 강풍주의보 발표 : 전북자치도(군산, 김제)", null, gunsan, GUNSAN_MAINLAND)).isTrue();
    }

    @Test
    void 광역시_세분_구역도_시설_구역으로_가른다() {
        // 예전에는 "서울(서울동남권)"처럼 광역시 안의 세분 구역이 오면 구 이름이 없어 서울 시설이 매칭되지 않았다
        String t1 = "호우주의보 발표";
        String title = "[특보] 제10-3호 / 호우주의보 발표 (*)";

        assertThat(KmaWarningAreaMatcher.matches(title, t1, "(1) 호우주의보 발표 : 서울(서울동남권, 서울동북권)", null, seoulDongbu, SEOUL_SOUTHEAST)).isTrue();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, "(1) 호우주의보 발표 : 서울(서울서북권)", null, seoulDongbu, SEOUL_SOUTHEAST)).isFalse();
        assertThat(KmaWarningAreaMatcher.matches(title, t1, "(1) 호우주의보 발표 : 서울특별시", null, seoulDongbu, SEOUL_SOUTHEAST)).isTrue();
    }
}
