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
 * {@code classpath:kma-facility-locations.csv}(전국 교정기관 59개소 격자좌표)를 읽어
 * {@link Location} 목록으로 제공.
 *
 * <p>CSV 컬럼: facilityId,facilityName,sido,sigungu,nx,ny,lat,lon. 이 로더는 nx,ny만 읽는다
 * (sido/sigungu/lat/lon은 참고용 컬럼).</p>
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

    private static final String RESOURCE_PATH = "kma-facility-locations.csv";

    private final List<Location> locations;

    public FacilityLocationLoader() {
        this.locations = Collections.unmodifiableList(load());
    }

    public List<Location> all() {
        return locations;
    }

    private List<Location> load() {
        List<Location> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                // facilityId,facilityName,sido,sigungu,nx,ny
                result.add(new Location(cols[0], cols[1], cols[4], cols[5]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("기관 격자좌표 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("기관 격자좌표 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
