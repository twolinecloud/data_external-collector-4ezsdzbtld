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
 * {@code classpath:kma-facility-locations.csv}(전국 교정기관 59개소)를 읽어
 * {@link FacilityMasterRecord} 목록으로 제공. {@link Location}/{@link FacilitySido}/
 * {@link FacilityRegion}이 각자 이 CSV를 따로 파싱하던 걸(2026-08-24 이전) 한 곳으로
 * 모았다 - {@link CsvFacilityMasterSource}가 이 결과를 그대로 씀.
 *
 * <p>CSV 컬럼: facilityId,facilityName,sido,sigungu,nx,ny,lat,lon (lat/lon은 이 클래스에서는
 * 안 씀 - 필요하면 admin-db {@code tb_ext_weather_facility}를 참고).</p>
 */
@Component
public class FacilityMasterCsvLoader {

    private static final String RESOURCE_PATH = "kma-facility-locations.csv";

    private final List<FacilityMasterRecord> records;

    public FacilityMasterCsvLoader() {
        this.records = Collections.unmodifiableList(load());
    }

    public List<FacilityMasterRecord> all() {
        return records;
    }

    private List<FacilityMasterRecord> load() {
        List<FacilityMasterRecord> result = new ArrayList<>();
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
                result.add(new FacilityMasterRecord(cols[0], cols[1], cols[2], cols[3], cols[4], cols[5]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("기관 기준정보 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("기관 기준정보 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
