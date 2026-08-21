package egovframework.external.publicdata.loader;

import egovframework.external.publicdata.loader.mapper.WeatherFacilityMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 앱 기동 시 {@code classpath:kma-facility-locations.csv}(교정기관 59개소)를
 * {@code kcais.tb_ext_weather_facility}(기준정보)에 upsert. 날씨/재난문자 적재 테이블 전부
 * 이 테이블에 FK를 걸어두므로(facility_id) Load가 켜져있으면 반드시 먼저 채워져 있어야 한다 -
 * 매번 앱을 띄울 때마다 멱등하게 다시 맞춰두는 방식이라 별도 수동 시딩 스텝이 필요 없다.
 *
 * <p>{@code public-data.load.enabled=true}일 때만 동작 - {@code KmaUltraSrtNcstLoader}와
 * 동일한 조건. {@link FacilityLocationLoader}/{@link FacilityRegionLoader}와 달리 이
 * CSV를 직접 다시 읽는다 - 그 두 로더는 각자 다른 부분집합(nx/ny만, sido+sigungu만)만
 * 노출해서 admin-db 시딩에 필요한 전체 컬럼(lat/lon 포함)을 못 채운다.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FacilityMasterSeeder {

    private static final Logger logger = LogManager.getLogger(FacilityMasterSeeder.class);
    private static final String RESOURCE_PATH = "kma-facility-locations.csv";

    private final WeatherFacilityMapper mapper;

    @PostConstruct
    void seed() {
        int count = 0;
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                // facilityId,facilityName,sido,sigungu,nx,ny,lat,lon
                String[] c = line.split(",", -1);
                mapper.upsert(c[0], c[1], parseDoubleOrNull(c[6]), parseDoubleOrNull(c[7]),
                    c[2], c[3], Integer.parseInt(c[4]), Integer.parseInt(c[5]));
                count++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("기관 마스터 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        logger.info("[LOAD] tb_ext_weather_facility 마스터 시딩 완료: {}건", count);
    }

    private Double parseDoubleOrNull(String s) {
        return (s == null || s.isBlank()) ? null : Double.parseDouble(s);
    }
}
