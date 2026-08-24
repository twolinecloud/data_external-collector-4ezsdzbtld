package egovframework.external.publicdata.collector;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 시설의 시도명을 {@link FacilitySido} 목록으로 제공 - 기상특보 관할구역
 * ({@link KmaWarningStation}) 매칭, 생활기상지수 시도 매칭 전용.
 * {@link FacilityMasterSource}(csv/db, Phase C 2026-08-24)의 결과를 매번 새로 매핑한다
 * (캐시 안 함 - {@link FacilityLocationLoader}와 동일 이유).
 */
@Component
public class FacilitySidoLoader {

    private final FacilityMasterSource facilityMasterSource;

    public FacilitySidoLoader(FacilityMasterSource facilityMasterSource) {
        this.facilityMasterSource = facilityMasterSource;
    }

    public List<FacilitySido> all() {
        return facilityMasterSource.current().stream()
            .map(r -> new FacilitySido(r.facilityId(), r.sido()))
            .toList();
    }
}
