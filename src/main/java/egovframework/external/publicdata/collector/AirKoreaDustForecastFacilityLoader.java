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
 * {@code classpath:airkorea-dust-forecast-facility.csv}(교정기관 59개소 - 대기질 예보권역
 * 매핑, {@link AirKoreaDustForecastFacility} 클래스 주석 참고)를 읽어 제공.
 */
@Component
public class AirKoreaDustForecastFacilityLoader {

    private static final String RESOURCE_PATH = "airkorea-dust-forecast-facility.csv";

    private final List<AirKoreaDustForecastFacility> mappings;

    public AirKoreaDustForecastFacilityLoader() {
        this.mappings = Collections.unmodifiableList(load());
    }

    public List<AirKoreaDustForecastFacility> all() {
        return mappings;
    }

    private List<AirKoreaDustForecastFacility> load() {
        List<AirKoreaDustForecastFacility> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header: facilityId,informRegion
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                result.add(new AirKoreaDustForecastFacility(cols[0], cols[1]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("황사 예보권역 매핑 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("황사 예보권역 매핑 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
