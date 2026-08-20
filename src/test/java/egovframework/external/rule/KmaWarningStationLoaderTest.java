package egovframework.external.rule;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기상청 활용가이드 "첨부. 지점코드"(2026-06-01판) 실측 표를 그대로 고정.
 * terrain-rule-base-spec.md §7-1 참고.
 */
class KmaWarningStationLoaderTest {

    @Test
    void 지점코드_10개가_모두_로딩된다() {
        List<KmaWarningStation> all = new KmaWarningStationLoader().all();

        assertThat(all).hasSize(10);
    }

    @Test
    void stnId_108은_전국이다() {
        KmaWarningStation seoul = find("108");

        assertThat(seoul.nationwide()).isTrue();
        assertThat(seoul.covers("아무시도")).isTrue(); // 전국이므로 어떤 시도든 covers=true
    }

    @Test
    void stnId_105는_강원특별자치도만_관할한다() {
        KmaWarningStation gangneung = find("105");

        assertThat(gangneung.covers("강원특별자치도")).isTrue();
        assertThat(gangneung.covers("경기도")).isFalse();
    }

    @Test
    void stnId_156은_전남광주통합특별시_하나로_매핑된다() {
        // 원본 표는 "광주, 전라남도"로 분리돼있지만 우리 시설 CSV는 이미 통합 표기를 쓰므로 하나로 합쳐둠
        KmaWarningStation gwangju = find("156");

        assertThat(gwangju.jurisdictionSido()).containsExactly("전남광주통합특별시");
    }

    @Test
    void stnId_159는_세개_시도를_관할한다() {
        KmaWarningStation busan = find("159");

        assertThat(busan.jurisdictionSido())
            .containsExactlyInAnyOrder("부산광역시", "울산광역시", "경상남도");
    }

    private KmaWarningStation find(String stnId) {
        return new KmaWarningStationLoader().all().stream()
            .filter(s -> s.stnId().equals(stnId))
            .findFirst()
            .orElseThrow();
    }
}
