package egovframework.external.rule;

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
 * {@code classpath:kma-facility-locations.csv}에서 시설의 시도명만 뽑아 제공 - 기상특보
 * 관할구역({@link KmaWarningStation}) 매칭 전용. 좌표/격자를 다루는
 * {@link egovframework.external.publicdata.collector.FacilityLocationLoader}나 재난문자
 * 지역매칭용 {@link egovframework.external.publicdata.collector.FacilityRegionLoader}
 * (시도+시군구 통짜 키)와는 용도가 달라 분리했다 - 여기선 시도 단위 비교만 필요하다.
 */
@Component
public class FacilitySidoLoader {

    private static final String RESOURCE_PATH = "kma-facility-locations.csv";

    private final List<FacilitySido> facilities;

    public FacilitySidoLoader() {
        this.facilities = Collections.unmodifiableList(load());
    }

    public List<FacilitySido> all() {
        return facilities;
    }

    private List<FacilitySido> load() {
        List<FacilitySido> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                // facilityId,facilityName,sido,sigungu,nx,ny,lat,lon
                result.add(new FacilitySido(cols[0], cols[2]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("기관 시도 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("기관 시도 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
