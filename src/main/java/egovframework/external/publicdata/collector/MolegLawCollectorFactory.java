package egovframework.external.publicdata.collector;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 수집 대상 법령 각각에 대한 {@link MolegCriminalLawCollector} 인스턴스를 생성한다.
 * {@link KmaLocationCollectorFactory}와 동일한 이유로 팩토리 패턴 사용 - 개별
 * {@code @Component} 빈으로 등록하는 대신 팩토리 하나로 관리.
 *
 * <p>목록 출처는 {@link MolegLawTargetSource}(csv/db, 2026-08-21) - {@code allLawCollectors()}를
 * 호출할 때마다 {@code source.current()}로 다시 조회한다(캐싱 안 함). DB 소스일 때 관리자
 * 콘솔에서 목록을 바꾸면 앱 재시작 없이 다음 수집 틱부터 반영되게 하기 위함 - CSV 소스일
 * 때는(파일이 앱 기동 후 안 바뀌므로) 사실상 매번 같은 목록이라 성능 영향 없음.</p>
 *
 * <p><b>법령/행정규칙 분기(2026-08-28 추가)</b>: {@link MolegLaw#docTypeOrDefault()}로
 * {@link MolegLaw#DOC_TYPE_ADMIN_RULE}이면 {@link MolegAdminRuleCollector}, 그 외(기본값
 * {@link MolegLaw#DOC_TYPE_LAW})면 기존 {@link MolegCriminalLawCollector}를 생성한다 - 스케줄
 * 자체는 하나({@code PublicDataCollectorScheduler#collectMolegCriminalLaws}) 그대로 두고,
 * 개별 컬렉터가 자기 {@code operationKey()}로 법령/행정규칙을 구분해 raw_staging에 적재하므로
 * Cleanse 단계에서 알맞은 정제기({@code MolegCriminalLawCleanser}/{@code MolegAdminRuleCleanser})가
 * 선택된다.</p>
 *
 * <p><b>시행일 미도래 건, 여기서 미리 거르지 않는다(2026-09-10 정정)</b>: 처음엔 {@link
 * MolegLaw#isEffectiveAsOf}가 {@code false}인 항목을 이 팩토리가 컬렉터 자체를 안 만들도록
 * 걸러내려 했는데(2026-09-09, 커밋 59dbbf3), 이게 "상법"/"상법 시행령"(개정본 시행일은
 * 2027-01-01이지만, "상법"이라는 이름 자체는 이미 수십 년째 시행 중인 법)을 잘못 걸러내는
 * 회귀를 만들었다 - CSV의 {@code effectiveDate}는 "그 법령명이 API에 존재하는 시점"이 아니라
 * "대상 목록에 올린 이 특정 개정본이 시행되는 시점"이라 서로 다르다. {@code eflaw}는 이름으로
 * "현재 시행 중인 버전"을 찾기 때문에, 개정 예정인 기존 법(상법)은 개정일 전에도 호출하면
 * 여전히 성공한다(현행 버전을 돌려줌) - 반면 아예 새로 만들어진 법(공소청법 등)은 그 이름이
 * 시행 전엔 존재 자체가 없어 실패한다. 이 둘을 이름만으로는 미리 구분할 수 없으므로, 여기서는
 * 거르지 않고 항상 시도한다 - "이름이 아직 없어서" 실패하는 예상된 경우의 처리는
 * {@link MolegCriminalLawCollector}/{@link MolegAdminRuleCollector}가 실제 실패 응답을 받은
 * 뒤(즉 무슨 이유로 실패했는지 안 다음)에 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class MolegLawCollectorFactory {

    private final LawSourcePort lawSourcePort;
    private final MolegLawTargetSource lawTargetSource;

    public List<PublicDataCollector> allLawCollectors() {
        return lawTargetSource.current().stream()
            .<PublicDataCollector>map(this::toCollector)
            .toList();
    }

    private PublicDataCollector toCollector(MolegLaw law) {
        return MolegLaw.DOC_TYPE_ADMIN_RULE.equals(law.docTypeOrDefault())
            ? new MolegAdminRuleCollector(lawSourcePort, law)
            : new MolegCriminalLawCollector(lawSourcePort, law);
    }
}
