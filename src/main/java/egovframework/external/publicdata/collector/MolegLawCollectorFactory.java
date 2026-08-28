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
