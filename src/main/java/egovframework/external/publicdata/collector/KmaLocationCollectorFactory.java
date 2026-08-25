package egovframework.external.publicdata.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 위치의존 오퍼레이션(초단기실황/초단기예보/단기예보) 3종 × {@link Location}(전국 교정기관
 * 59개소) 조합만큼 {@link PublicDataCollector} 인스턴스를 생성한다.
 *
 * <p>59×3=177개를 개별 {@code @Component} 빈으로 등록하는 대신, 이 팩토리 하나가 목록을
 * 만들어 {@code PublicDataCollectorRegistry}/{@code PublicDataCollectorScheduler}에
 * 공급한다. 지역이 추가/변경되면 {@code kma-facility-locations.csv}만 고치면 됨 - 코드
 * 변경 불필요(db 소스일 때는 CSV 대신 승인된 admin-db 시설이 자동 반영됨, Phase C
 * 2026-08-24).</p>
 *
 * <p><b>목록을 캐시하지 않는다</b> - {@code FacilityLocationLoader#all()}을 세 메서드가
 * 호출될 때마다 매번 새로 부른다({@code MolegLawCollectorFactory}와 동일 원칙).
 * {@code PublicDataCollectorScheduler}가 스케줄 틱마다 이 메서드들을 다시 호출하므로,
 * db 소스일 때 승인된 신규 시설이 앱 재시작 없이 다음 틱부터 바로 수집 대상에 들어간다.</p>
 */
@Component
public class KmaLocationCollectorFactory {

    private final KmaApiClient apiClient;
    private final String villageForecastEndpoint;
    private final String villageForecastServiceKey;
    private final FacilityLocationLoader facilityLocationLoader;

    public KmaLocationCollectorFactory(
        KmaApiClient apiClient,
        @Value("${public-data.kma.village-forecast.endpoint}") String villageForecastEndpoint,
        @Value("${public-data.kma.village-forecast.service-key:}") String villageForecastServiceKey,
        FacilityLocationLoader facilityLocationLoader
    ) {
        this.apiClient = apiClient;
        this.villageForecastEndpoint = villageForecastEndpoint;
        this.villageForecastServiceKey = villageForecastServiceKey;
        this.facilityLocationLoader = facilityLocationLoader;
    }

    public List<PublicDataCollector> ultraSrtNcstCollectors() {
        return facilityLocationLoader.all().stream()
            .<PublicDataCollector>map(loc -> new KmaUltraSrtNcstCollector(apiClient, villageForecastEndpoint, villageForecastServiceKey, loc))
            .toList();
    }

    public List<PublicDataCollector> ultraSrtFcstCollectors() {
        return facilityLocationLoader.all().stream()
            .<PublicDataCollector>map(loc -> new KmaUltraSrtFcstCollector(apiClient, villageForecastEndpoint, villageForecastServiceKey, loc))
            .toList();
    }

    public List<PublicDataCollector> vilageFcstCollectors() {
        return facilityLocationLoader.all().stream()
            .<PublicDataCollector>map(loc -> new KmaVilageFcstCollector(apiClient, villageForecastEndpoint, villageForecastServiceKey, loc))
            .toList();
    }

    /** 세 오퍼레이션을 합친 전체 목록 - 레지스트리가 사용. */
    public List<PublicDataCollector> allLocationBasedCollectors() {
        return List.of(ultraSrtNcstCollectors(), ultraSrtFcstCollectors(), vilageFcstCollectors())
            .stream().flatMap(List::stream).toList();
    }
}
