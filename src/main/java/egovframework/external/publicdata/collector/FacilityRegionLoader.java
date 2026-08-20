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
 * {@code classpath:kma-facility-locations.csv}에서 재난문자 지역매칭에 필요한 부분만
 * ({@link FacilityRegion} 목록으로) 뽑아 제공한다. 위경도/격자좌표를 다루는
 * {@link FacilityLocationLoader}/{@link Location}과는 용도가 달라 분리했다 - 날씨 수집 경로에는
 * 영향 없이 재난문자 정제(DisasterMsgCleanser)에서만 쓴다.
 *
 * <p>CSV 컬럼: facilityId,facilityName,sido,sigungu,nx,ny,lat,lon. sido/sigungu는 이미 공백 없이
 * 붙여쓴 형태이고(예: {@code "안양시동안구"}), 행정구역 개편 반영본(전남광주통합특별시/강원특별자치도/
 * 전북특별자치도 등, 2026-08-18 확인)이라 재난문자 응답의 {@code RCPTN_RGN_NM} 표기와 그대로
 * 대조 가능하다 - 별도 변환 불필요.</p>
 */
@Component
public class FacilityRegionLoader {

    private static final String RESOURCE_PATH = "kma-facility-locations.csv";

    private final List<FacilityRegion> regions;

    public FacilityRegionLoader() {
        this.regions = Collections.unmodifiableList(load());
    }

    public List<FacilityRegion> all() {
        return regions;
    }

    private List<FacilityRegion> load() {
        List<FacilityRegion> result = new ArrayList<>();
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
                result.add(new FacilityRegion(cols[0], cols[2] + cols[3]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("기관 지역 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("기관 지역 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
