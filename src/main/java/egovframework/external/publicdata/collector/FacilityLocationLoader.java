package egovframework.external.publicdata.collector;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 전국 교정기관 격자좌표를 {@link Location} 목록으로 제공 - {@link FacilityMasterSource}
 * (csv/db, Phase C 2026-08-24)의 결과를 매번 새로 매핑한다(캐시 안 함 - db 소스일 때
 * 승인된 신규 시설이 재시작 없이 다음 조회부터 바로 반영되게 하기 위함).
 *
 * <p>격자좌표는 <b>시설 실좌표</b>를 기상청 격자변환식(dfs_xy_conv)에 넣어 산출한 값이다.
 * 이전에는 기상청 공식 매핑표의 읍면동 대표점 격자를 썼는데, 교정시설이 읍면동 중심에서
 * 멀리 떨어진 경우가 많아(평균 3km, 최대 8km) 59개소 중 36개소가 시설이 실제로 속하지 않는
 * 격자를 조회하고 있었다 - 대전교도소는 11km 떨어진 격자였다. 시설 단위 알림이 목적이라
 * 실좌표 기준으로 교체함(2026-08-18).</p>
 *
 * <p>시설 실좌표 확정 근거와 지형특성은 private-doc/facility-terrain.csv 참고.</p>
 */
@Component
public class FacilityLocationLoader {

    private final FacilityMasterSource facilityMasterSource;

    public FacilityLocationLoader(FacilityMasterSource facilityMasterSource) {
        this.facilityMasterSource = facilityMasterSource;
    }

    public List<Location> all() {
        return facilityMasterSource.current().stream()
            .map(r -> new Location(r.facilityId(), r.facilityName(), r.nx(), r.ny()))
            .toList();
    }
}
