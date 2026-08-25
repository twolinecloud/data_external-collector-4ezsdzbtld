package egovframework.external.publicdata.collector;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 재난문자 지역매칭에 필요한 부분만 {@link FacilityRegion} 목록으로 제공. 위경도/격자좌표를
 * 다루는 {@link FacilityLocationLoader}/{@link Location}과는 용도가 달라 분리했다 - 날씨
 * 수집 경로에는 영향 없이 재난문자 정제(DisasterMsgCleanser)에서만 쓴다.
 * {@link FacilityMasterSource}(csv/db, Phase C 2026-08-24)의 결과를 매번 새로 매핑한다
 * (캐시 안 함 - {@link FacilityLocationLoader}와 동일 이유).
 *
 * <p>{@code sido+sigungu}는 이미 공백 없이 붙여쓴 형태이고(예: {@code "안양시동안구"}),
 * 행정구역 개편 반영본(전남광주통합특별시/강원특별자치도/전북특별자치도 등, 2026-08-18 확인)
 * 이라 재난문자 응답의 {@code RCPTN_RGN_NM} 표기와 그대로 대조 가능하다 - 별도 변환 불필요.</p>
 */
@Component
public class FacilityRegionLoader {

    private final FacilityMasterSource facilityMasterSource;

    public FacilityRegionLoader(FacilityMasterSource facilityMasterSource) {
        this.facilityMasterSource = facilityMasterSource;
    }

    public List<FacilityRegion> all() {
        return facilityMasterSource.current().stream()
            .map(r -> new FacilityRegion(r.facilityId(), r.sido() + r.sigungu()))
            .toList();
    }
}
