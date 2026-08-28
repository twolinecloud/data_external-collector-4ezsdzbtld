package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * classpath:moleg-criminal-laws.csv(형사법령 44건 + 교정 관련 법령 16건 = 60건에서 출발해,
 * 2026-08-28 여러 출처 교차참조 목록 반영으로 법령 433건 + 행정규칙 58건 = 491건으로 확대)가
 * 정상 로딩되는지 검증. 이 목록은 사람이 직접 선정하고 실 API로 lawId/mst까지 검증한 실제
 * 운영 데이터라(private-doc 31/36/39번 항목 참고), 개수/형식이 깨지면 수집 자체가 안 되므로
 * 기본 무결성을 테스트로 고정해둔다.
 */
class MolegLawListLoaderTest {

    @Test
    void 법령_및_행정규칙_491건이_모두_로딩된다() {
        List<MolegLaw> laws = new MolegLawListLoader().all();

        assertThat(laws).hasSize(491);
    }

    @Test
    void docType별_건수가_법령_433_행정규칙_58이다() {
        List<MolegLaw> laws = new MolegLawListLoader().all();

        long lawCount = laws.stream().filter(l -> MolegLaw.DOC_TYPE_LAW.equals(l.docTypeOrDefault())).count();
        long adminRuleCount = laws.stream().filter(l -> MolegLaw.DOC_TYPE_ADMIN_RULE.equals(l.docTypeOrDefault())).count();

        assertThat(lawCount).isEqualTo(433);
        assertThat(adminRuleCount).isEqualTo(58);
    }

    @Test
    void 쉼표가_포함된_법령명과_공동소관_부처도_그대로_로딩된다() {
        // 목록 확대 과정에서 발견된 두 quoted-CSV 케이스(2026-08-28) - 값 훼손(치환) 없이
        // parseCsvLine이 원문 그대로 읽어내는지 확인
        List<MolegLaw> laws = new MolegLawListLoader().all();

        MolegLaw commaInName = laws.stream()
            .filter(l -> l.lawId().equals("001170"))
            .findFirst()
            .orElseThrow();
        assertThat(commaInName.lawName())
            .isEqualTo("재외국민의 가족관계등록 창설, 가족관계등록부 정정 및 가족관계등록부 정리에 관한 특례법");

        MolegLaw commaInMinistry = laws.stream()
            .filter(l -> l.lawId().equals("001159"))
            .findFirst()
            .orElseThrow();
        assertThat(commaInMinistry.ministry()).isEqualTo("국방부,법무부");
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
    void 신규_추가된_교정_관련_법령도_로딩된다() {
        // 36번 항목에서 추가된 16건 중 하나 - "·"/"ㆍ" 표기 혼동(4번 항목에서 정정된 사례) 포함해서 확인
        List<MolegLaw> laws = new MolegLawListLoader().all();

        MolegLaw corrMutualAidAct = laws.stream()
            .filter(l -> l.lawName().equals("교정공제회법"))
            .findFirst()
            .orElseThrow();

        assertThat(corrMutualAidAct.lawId()).isEqualTo("012347");
        assertThat(corrMutualAidAct.mst()).isEqualTo("222423");

        MolegLaw privatePrisonAct = laws.stream()
            .filter(l -> l.lawId().equals("002027"))
            .findFirst()
            .orElseThrow();
        assertThat(privatePrisonAct.lawName()).isEqualTo("민영교도소 등의 설치ㆍ운영에 관한 법률");
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
