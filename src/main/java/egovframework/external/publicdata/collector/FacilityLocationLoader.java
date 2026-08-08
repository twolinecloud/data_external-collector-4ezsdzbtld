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
 * <p>CSV 컬럼: facilityId,facilityName,sido,sigungu,nx,ny. 격자좌표는 기상청 공식 매핑표
 * (2026-02 갱신본) 기준, 읍면동 단위 정밀매칭 실패 시 시/군/구 대표좌표로 폴백한 값 - 격자가
 * 5km 단위라 동 단위 오차는 실용적으로 무시 가능하다고 판단.</p>
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
