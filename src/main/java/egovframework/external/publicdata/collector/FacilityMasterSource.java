package egovframework.external.publicdata.collector;

import java.util.List;

/**
 * 교정기관 목록(기준정보) 소스 - csv(기본값)/db 전환 가능. {@code MolegLawTargetSource}와
 * 동일한 패턴(Phase C, 2026-08-24) - {@code public-data.facility.master-source}로 전환한다.
 *
 * <p>db 소스일 때는 {@code Location}/{@code FacilitySido}/{@code FacilityRegion} 등 하위
 * 소비자가 {@code current()}를 매번 새로 호출해서, 승인된 신규 시설이 앱 재시작 없이 다음
 * 스케줄 틱부터 바로 반영되게 한다(Phase A/B의 검토 큐 승인이 실제 수집으로 이어지는
 * 마지막 연결고리) - db 조회 실패 시 빈 목록을 반환하고 예외를 전파하지 않는다
 * (그날 수집만 건너뜀, fail-isolation 원칙).</p>
 */
public interface FacilityMasterSource {

    List<FacilityMasterRecord> current();
}
