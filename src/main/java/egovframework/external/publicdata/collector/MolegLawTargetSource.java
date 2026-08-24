package egovframework.external.publicdata.collector;

import java.util.List;

/**
 * "어떤 법령을 수집 대상으로 삼을지" 목록의 출처. {@code public-data.moleg.law-target-source}
 * 설정값(csv/db)에 따라 {@link CsvMolegLawTargetSource}/{@link DbMolegLawTargetSource} 둘 중
 * 하나만 빈으로 활성화된다(2026-08-21).
 *
 * <p><b>이 목록(대상)과 실제 수집된 법령 본문은 완전히 다른 저장소를 쓴다</b> - 목록은
 * admin-db({@code tb_ext_law_target}, 관리자 콘솔에서 편집 예정)로 옮기지만, 수집된 법령
 * 본문/이력은 제논DB로 갈 예정이라(아직 미구현) admin-db와 무관하다. {@code current()}는
 * 호출할 때마다 최신 목록을 다시 조회한다 - DB 소스일 때 관리자 콘솔에서 바꾼 게 앱 재시작
 * 없이 다음 수집 틱부터 바로 반영되게 하기 위함.</p>
 */
public interface MolegLawTargetSource {

    /** @return 현재 수집 대상 법령 목록. 조회 자체가 실패하면(DB 소스에서 DB 장애 등) 빈 리스트. */
    List<MolegLaw> current();
}
