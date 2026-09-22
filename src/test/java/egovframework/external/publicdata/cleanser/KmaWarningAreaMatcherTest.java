// src/test/java/egovframework/external/publicdata/cleanser/KmaWarningAreaMatcherTest.java
package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.FacilityMasterRecord;
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
}
