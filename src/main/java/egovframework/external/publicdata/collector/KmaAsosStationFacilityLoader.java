package egovframework.external.publicdata.collector;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code classpath:kma-asos-station-facility.csv}(교정기관 59개소 - 최근접 ASOS 지점 매핑,
 * {@link AsosStationFacility} 클래스 주석 참고)를 읽어 제공.
 *
 * <p>매핑은 교정기관 좌표({@code kma-facility-locations.csv})와 ASOS 지점 좌표(기상청 API허브
 * {@code stn_inf.php}, 2026-09-04 활용신청 승인)로 최근접 거리(Haversine) 계산해서 생성했다 -
 * 실측 결과 평균 9.7km, 최대 29.2km(평택지소 → 수원). 다른 위치기반 CSV(facility-locations 등)와
 * 달리 이 파일은 수집기가 아니라 지점 좌표 API에서 나온 파생 데이터라, ASOS 지점이 폐쇄/이전되면
 * 재계산이 필요하다 - 자동 갱신 없음(hot-reload 아님, 다른 CSV들과 동일 원칙).</p>
 */
@Component
public class KmaAsosStationFacilityLoader {

    private static final String RESOURCE_PATH = "kma-asos-station-facility.csv";

    private final List<AsosStationFacility> mappings;

    public KmaAsosStationFacilityLoader() {
        this.mappings = Collections.unmodifiableList(load());
    }

    public List<AsosStationFacility> all() {
        return mappings;
    }

    private List<AsosStationFacility> load() {
        List<AsosStationFacility> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header: facilityId,stnId,stnName,distanceKm
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                result.add(new AsosStationFacility(cols[0], cols[1], cols[2], Double.parseDouble(cols[3])));
            }
        } catch (IOException e) {
            throw new IllegalStateException("ASOS 지점-기관 매핑 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("ASOS 지점-기관 매핑 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
