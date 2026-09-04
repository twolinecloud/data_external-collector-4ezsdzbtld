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
 * {@code classpath:airkorea-station-facility.csv}(교정기관 59개소 - 최근접 에어코리아 측정소
 * 매핑, {@link AirKoreaStationFacility} 클래스 주석 참고)를 읽어 제공.
 *
 * <p>매핑은 교정기관 좌표({@code kma-facility-locations.csv})와 에어코리아 측정소 좌표
 * (측정소정보 조회서비스 {@code getMsrstnList}, 2026-09-04 활용신청 승인 - {@code dmX}=경도,
 * {@code dmY}=위도, 전국 범위 실측으로 순서 확인 완료)로 최근접 거리(Haversine) 계산해서
 * 생성했다 - 실측 결과 평균 4.1km, 최대 13.6km. 측정소 밀도가 ASOS(97개)보다 훨씬 높아서
 * ({@link KmaAsosStationFacilityLoader} 평균 9.7km) 최근접 거리도 더 가깝다. ASOS 매핑과
 * 마찬가지로 지점 변동 시 재계산이 필요하다(자동 갱신 없음).</p>
 */
@Component
public class AirKoreaStationFacilityLoader {

    private static final String RESOURCE_PATH = "airkorea-station-facility.csv";

    private final List<AirKoreaStationFacility> mappings;

    public AirKoreaStationFacilityLoader() {
        this.mappings = Collections.unmodifiableList(load());
    }

    public List<AirKoreaStationFacility> all() {
        return mappings;
    }

    private List<AirKoreaStationFacility> load() {
        List<AirKoreaStationFacility> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header: facilityId,stationName,distanceKm
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                result.add(new AirKoreaStationFacility(cols[0], cols[1], Double.parseDouble(cols[2])));
            }
        } catch (IOException e) {
            throw new IllegalStateException("에어코리아 측정소-기관 매핑 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("에어코리아 측정소-기관 매핑 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
