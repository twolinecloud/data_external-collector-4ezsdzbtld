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
import java.util.ArrayList;
import java.util.List;

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
 *
 * <p><b>DB 쓰기 실패가 앱 부팅 자체를 막으면 안 됨(2026-08-21)</b> - {@code @PostConstruct}에서
 * 예외가 전파되면 Spring 컨텍스트 초기화가 실패해서 앱이 아예 안 뜬다(CrashLoopBackOff).
 * admin-db 커넥션 슬롯 고갈처럼 일시적인 연결 문제가 배포 시점과 겹치면 앱 전체를 못 띄우게
 * 되므로, 파일 읽기 실패(패키징 버그 - 즉시 알아채야 함)와 DB 쓰기 실패(일시적일 수 있음 -
 * 로그만 남기고 넘어감)를 구분해서 처리한다. 시딩이 실패해도 CLEANSED 행의 개별 적재
 * 자체는(FK 위반으로) 계속 LOAD_FAILED로 남을 뿐 파이프라인은 안 죽는다 - 다음 재배포/재기동
 * 때 다시 시도된다.</p>
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
        List<String> lines = readCsvLines(); // 패키징 버그(리소스 자체가 없음)는 그대로 부팅을 막음 - 즉시 알아채야 함

        int count = 0;
        try {
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                // facilityId,facilityName,sido,sigungu,nx,ny,lat,lon
                String[] c = line.split(",", -1);
                mapper.upsert(c[0], c[1], parseDoubleOrNull(c[6]), parseDoubleOrNull(c[7]),
                    c[2], c[3], Integer.parseInt(c[4]), Integer.parseInt(c[5]));
                count++;
            }
            logger.info("[LOAD] tb_ext_weather_facility 마스터 시딩 완료: {}건", count);
        } catch (Exception e) {
            // admin-db 연결 실패 등 - 부팅을 막지 않는다(위 클래스 주석 참고). 시딩이 하나도
            // 안 된 채로 뜨면 이후 개별 Load 시도가 FK 위반으로 계속 실패하겠지만, 그 실패는
            // PublicDataLoadService가 이미 안전하게 격리해서 LOAD_FAILED로만 남긴다.
            logger.warn("[LOAD] tb_ext_weather_facility 마스터 시딩 실패 ({}건 성공 후 중단) - "
                + "admin-db 연결 확인 필요: {}", count, e.getMessage());
        }
    }

    private List<String> readCsvLines() {
        List<String> lines = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new IllegalStateException("기관 마스터 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        return lines;
    }

    private Double parseDoubleOrNull(String s) {
        return (s == null || s.isBlank()) ? null : Double.parseDouble(s);
    }
}
