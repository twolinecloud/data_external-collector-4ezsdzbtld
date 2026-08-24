package egovframework.external.publicdata.collector;

import egovframework.external.publicdata.loader.mapper.WeatherFacilityMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code tb_ext_weather_facility}(admin-db) 조회 - Phase A/B(자동 동기화/지오코딩)로 승인된
 * 신규 시설이 <b>앱 재시작 없이</b> 다음 스케줄 틱부터 바로 반영되게 하는 게 목적(Phase C
 * 핵심). {@code current()}를 캐시하지 않고 매번 새로 조회 - 소비자(
 * {@code KmaLocationCollectorFactory} 등)도 매 스케줄 틱마다 다시 호출하므로, 이 소스가
 * 조회 시점의 admin-db 최신 상태를 그대로 반영한다.
 *
 * <p>조회 실패 시(admin-db 장애 등) 빈 목록을 반환하고 예외를 전파하지 않는다 - 그날
 * 수집만 0건으로 건너뛰고 앱/스케줄러는 안 죽는다({@code DbMolegLawTargetSource}와 동일
 * 원칙, admin-db 커넥션 슬롯 고갈이 반복됐던 걸 고려한 설계).</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.facility", name = "master-source", havingValue = "db")
public class DbFacilityMasterSource implements FacilityMasterSource {

    private static final Logger logger = LogManager.getLogger(DbFacilityMasterSource.class);

    private final WeatherFacilityMapper mapper;

    public DbFacilityMasterSource(WeatherFacilityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<FacilityMasterRecord> current() {
        try {
            return mapper.selectAll().stream()
                .map(DbFacilityMasterSource::toRecord)
                .toList();
        } catch (Exception e) {
            logger.warn("[FACILITY] tb_ext_weather_facility 조회 실패 - 빈 목록으로 대체: {}", e.getMessage());
            return List.of();
        }
    }

    private static FacilityMasterRecord toRecord(Map<String, Object> row) {
        return new FacilityMasterRecord(
            (String) row.get("facilityId"),
            (String) row.get("facilityNm"),
            (String) row.get("sidoNm"),
            (String) row.get("sigunguNm"),
            String.valueOf(row.get("nx")),
            String.valueOf(row.get("ny")));
    }
}
