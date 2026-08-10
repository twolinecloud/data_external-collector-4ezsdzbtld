package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * classpath:moleg-criminal-laws.csv(형사법령 44건)가 정상 로딩되는지 검증. 이 목록은
 * 사람이 직접 선정하고 실 API로 lawId/mst까지 검증한 실제 운영 데이터라(private-doc 31번
 * 항목 참고), 개수/형식이 깨지면 수집 자체가 안 되므로 기본 무결성을 테스트로 고정해둔다.
 */
class MolegLawListLoaderTest {

    @Test
    void 형사법령_44건이_모두_로딩된다() {
        List<MolegLaw> laws = new MolegLawListLoader().all();

        assertThat(laws).hasSize(44);
    }

    @Test
    void lawId는_전부_고유하다() {
        List<MolegLaw> laws = new MolegLawListLoader().all();

        Set<String> ids = laws.stream().map(MolegLaw::lawId).collect(Collectors.toSet());

        assertThat(ids).hasSameSizeAs(laws);
    }

    @Test
    void mst는_전부_고유하다() {
        // mst(법령일련번호)는 버전별 식별자라 lawId보다도 더 고유해야 함
        List<MolegLaw> laws = new MolegLawListLoader().all();

        Set<String> msts = laws.stream().map(MolegLaw::mst).collect(Collectors.toSet());

        assertThat(msts).hasSameSizeAs(laws);
    }

    @Test
    void 형법이_기대한_값으로_로딩된다() {
        List<MolegLaw> laws = new MolegLawListLoader().all();

        MolegLaw criminalAct = laws.stream()
            .filter(l -> l.lawName().equals("형법"))
            .findFirst()
            .orElseThrow();

        assertThat(criminalAct.lawId()).isEqualTo("001692");
        assertThat(criminalAct.mst()).isEqualTo("284025");
        assertThat(criminalAct.lawType()).isEqualTo("법률");
        assertThat(criminalAct.ministry()).isEqualTo("법무부");
    }

    @Test
    void 공동소관_법령의_ministry는_가운뎃점으로_구분된다() {
        // 원본 CSV엔 ","로 구분돼있던 걸 CSV 파싱과 충돌하지 않게 "·"로 치환해둠
        List<MolegLaw> laws = new MolegLawListLoader().all();

        MolegLaw wiretapAct = laws.stream()
            .filter(l -> l.lawName().equals("통신비밀보호법"))
            .findFirst()
            .orElseThrow();

        assertThat(wiretapAct.ministry()).contains("·").doesNotContain(",");
    }
}
