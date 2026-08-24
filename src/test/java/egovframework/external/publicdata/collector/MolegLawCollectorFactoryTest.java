package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MolegLawCollectorFactory}가 형사법령+교정법령 60건만큼 컬렉터를 올바르게 생성하는지
 * 검증. 실제 API 호출은 하지 않음 - 생성/키 유일성만 확인.
 */
@ExtendWith(MockitoExtension.class)
class MolegLawCollectorFactoryTest {

    private final LawSourcePort lawSourcePort = new DirectLawSourceAdapter(
        new org.springframework.web.client.RestTemplate(), "https://example.invalid", "test-oc");
    private final MolegLawTargetSource lawTargetSource = new CsvMolegLawTargetSource(new MolegLawListLoader());
    private final MolegLawCollectorFactory factory = new MolegLawCollectorFactory(lawSourcePort, lawTargetSource);

    @Mock
    private MolegLawTargetSource mockSource;

    @Test
    void 법령_60건만큼_컬렉터가_생성된다() {
        assertThat(factory.allLawCollectors()).hasSize(60);
    }

    @Test
    void 모든_컬렉터의_key는_서로_고유하다() {
        List<PublicDataCollector> all = factory.allLawCollectors();

        Set<String> keys = all.stream().map(PublicDataCollector::key).collect(Collectors.toSet());

        assertThat(keys).hasSameSizeAs(all);
    }

    @Test
    void 모든_컬렉터가_같은_operationKey를_공유한다() {
        // 기상청의 지역기반 컬렉터와 달리 법령은 오퍼레이션이 하나뿐(법령 본문조회) -
        // 정제 단계가 법령 종류와 무관하게 같은 정제기를 쓸 수 있어야 함
        List<PublicDataCollector> all = factory.allLawCollectors();

        Set<String> operationKeys = all.stream().map(PublicDataCollector::operationKey).collect(Collectors.toSet());

        assertThat(operationKeys).containsExactly("moleg-criminal-law");
    }

    @Test
    void apiName에_법령명이_포함되어_구분된다() {
        PublicDataCollector first = factory.allLawCollectors().get(0);

        assertThat(first.apiName()).contains("법령 본문조회").contains("(");
        assertThat(first.sourceName()).isEqualTo("국가법령정보센터 (법제처)");
        assertThat(first.facilityId()).isNull();
    }

    @Test
    void allLawCollectors를_호출할_때마다_목록을_다시_조회한다() {
        // DB 소스일 때 관리자 콘솔에서 바꾼 게 앱 재시작 없이 다음 틱부터 반영되려면
        // 캐싱하지 않고 매번 다시 조회해야 함
        when(mockSource.current()).thenReturn(List.of());
        MolegLawCollectorFactory f = new MolegLawCollectorFactory(lawSourcePort, mockSource);

        f.allLawCollectors();
        f.allLawCollectors();

        verify(mockSource, times(2)).current();
    }
}
