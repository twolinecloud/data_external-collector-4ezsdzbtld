package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MolegLawCollectorFactory}가 대상 목록(법령 433건 + 행정규칙 58건, 2026-08-28 확대)만큼
 * 컬렉터를 올바르게 생성하는지 검증. 실제 API 호출은 하지 않음 - 생성/키 유일성만 확인.
 *
 * <p>대상 목록 중 시행일이 아직 안 지난 건(2026-09-09 기준 "공소청법"/"친일반민족행위자
 * 재산의 국가귀속 등에 관한 특별법" 2건)은 {@link MolegLawCollectorFactory}가 제외하므로,
 * 실제 CSV를 쓰는 테스트는 그 개수를 직접 다시 계산해서 뺀다({@link #notYetEffectiveCount})
 * - 시행일이 지나면 자동으로 491건 그대로 돌아오게, 날짜에 종속되지 않게 하기 위함.</p>
 */
@ExtendWith(MockitoExtension.class)
class MolegLawCollectorFactoryTest {

    private static final int LAW_COUNT = 433;
    private static final int ADMIN_RULE_COUNT = 58;
    private static final DateTimeFormatter EFFECTIVE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LawSourcePort lawSourcePort = new DirectLawSourceAdapter(
        new org.springframework.web.client.RestTemplate(), "https://example.invalid", "test-oc");
    private final MolegLawTargetSource lawTargetSource = new CsvMolegLawTargetSource(new MolegLawListLoader());
    private final MolegLawCollectorFactory factory = new MolegLawCollectorFactory(lawSourcePort, lawTargetSource);

    @Mock
    private MolegLawTargetSource mockSource;

    @Test
    void 법령_행정규칙_합계에서_시행일_미도래_건을_제외한_만큼_컬렉터가_생성된다() {
        int expected = LAW_COUNT + ADMIN_RULE_COUNT - notYetEffectiveCount();

        assertThat(factory.allLawCollectors()).hasSize(expected);
    }

    /** 실제 CSV를 훑어 시행일이 아직 안 지난 건수를 센다 - {@link MolegLaw#isEffectiveAsOf}와
     * 별개로 직접 계산해서, 필터링 로직 자체의 버그를 이 테스트가 가려버리지 않게 한다. */
    private static int notYetEffectiveCount() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        return (int) new MolegLawListLoader().all().stream()
            .filter(law -> {
                try {
                    return LocalDate.parse(law.effectiveDate(), EFFECTIVE_DATE_FORMAT).isAfter(today);
                } catch (Exception e) {
                    return false;
                }
            })
            .count();
    }

    @Test
    void 시행일이_아직_안_지난_항목은_컬렉터가_생성되지_않는다() {
        MolegLaw notYetEffective = new MolegLaw("999999", "미래법", "1", "법률", "20260101", "29991231", "법무부", MolegLaw.DOC_TYPE_LAW);
        MolegLaw effective = new MolegLaw("999998", "현행법", "2", "법률", "20260101", "20260101", "법무부", MolegLaw.DOC_TYPE_LAW);
        when(mockSource.current()).thenReturn(List.of(notYetEffective, effective));
        MolegLawCollectorFactory f = new MolegLawCollectorFactory(lawSourcePort, mockSource);

        List<PublicDataCollector> result = f.allLawCollectors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).apiName()).contains("현행법");
    }

    @Test
    void 모든_컬렉터의_key는_서로_고유하다() {
        List<PublicDataCollector> all = factory.allLawCollectors();

        Set<String> keys = all.stream().map(PublicDataCollector::key).collect(Collectors.toSet());

        assertThat(keys).hasSameSizeAs(all);
    }

    @Test
    void operationKey로_법령과_행정규칙_컬렉터가_구분된다() {
        // docType(LAW/ADMIN_RULE)에 따라 서로 다른 컬렉터(MolegCriminalLawCollector/
        // MolegAdminRuleCollector)가 생성되고, 각자 자기 operationKey를 가져야 Cleanse 단계가
        // 알맞은 정제기를 고를 수 있다.
        List<PublicDataCollector> all = factory.allLawCollectors();

        Set<String> operationKeys = all.stream().map(PublicDataCollector::operationKey).collect(Collectors.toSet());
        assertThat(operationKeys).containsExactlyInAnyOrder("moleg-criminal-law", "moleg-admin-rule");

        long lawCollectors = all.stream().filter(c -> c instanceof MolegCriminalLawCollector).count();
        long adminRuleCollectors = all.stream().filter(c -> c instanceof MolegAdminRuleCollector).count();
        // 시행일 미도래 제외 대상 2건은 전부 docType=LAW라 LAW_COUNT 쪽에서만 뺀다(위
        // notYetEffectiveCount와 달리 여기선 법령/행정규칙 구분까지 나눠야 해서 재사용 안 함).
        assertThat(lawCollectors).isEqualTo(LAW_COUNT - notYetEffectiveCount());
        assertThat(adminRuleCollectors).isEqualTo(ADMIN_RULE_COUNT);
    }

    @Test
    void docType이_비어있으면_기본값_LAW로_취급해_MolegCriminalLawCollector를_생성한다() {
        // 기존 csv 60건/db 소스는 docType 컬럼이 없던 시절 데이터 - 하위호환 확인
        MolegLaw noDocType = new MolegLaw("999999", "임시법령", "1", "법률", "20260101", "20260101", "법무부", null);
        when(mockSource.current()).thenReturn(List.of(noDocType));
        MolegLawCollectorFactory f = new MolegLawCollectorFactory(lawSourcePort, mockSource);

        List<PublicDataCollector> result = f.allLawCollectors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(MolegCriminalLawCollector.class);
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
