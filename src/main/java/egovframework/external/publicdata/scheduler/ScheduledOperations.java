package egovframework.external.publicdata.scheduler;

import egovframework.external.publicdata.collector.AirKoreaDustForecastCollector;
import egovframework.external.publicdata.collector.AirKoreaRealtimeCollector;
import egovframework.external.publicdata.collector.DisasterMsgCollector;
import egovframework.external.publicdata.collector.KmaAsosHourlyCollector;
import egovframework.external.publicdata.collector.KmaLocationCollectorFactory;
import egovframework.external.publicdata.collector.KmaWeatherWarningListCollector;
import egovframework.external.publicdata.collector.LivingWthrIdxCollectorFactory;
import egovframework.external.publicdata.collector.MolegLawCollectorFactory;
import egovframework.external.publicdata.collector.PublicDataCollector;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 스케줄 틱 1개가 실행하는 오퍼레이션(operationKey)과 그 컬렉터 묶음의 단일 출처.
 *
 * <p>{@link PublicDataCollectorScheduler}(자동 스케줄)와 바로 실행 API가 <b>같은 컬렉터 묶음</b>을 쓰도록 여기서만
 * 정의한다. 위치의존 오퍼레이션 3종은 지역(59개소) 전체, 생활기상지수 2종은 16개 시도 전체, 법령은 법령+행정규칙
 * 전체가 한 묶음이다 - 컬렉터 1개만 실행하는 {@code /public-data/collect/{key}/run}과 다르다.
 *
 * <p>컬렉터 목록은 호출 시점에 만든다(대상 목록을 DB/CSV에서 새로 읽는 팩토리가 있어서 미리 고정하지 않는다).
 */
@Component
public class ScheduledOperations {

    private final Map<String, Supplier<List<PublicDataCollector>>> byOperation;

    public ScheduledOperations(KmaLocationCollectorFactory locationCollectorFactory,
            KmaWeatherWarningListCollector kmaWeatherWarningListCollector,
            KmaAsosHourlyCollector kmaAsosHourlyCollector,
            AirKoreaRealtimeCollector airKoreaRealtimeCollector,
            AirKoreaDustForecastCollector airKoreaDustForecastCollector,
            MolegLawCollectorFactory lawCollectorFactory,
            DisasterMsgCollector disasterMsgCollector,
            LivingWthrIdxCollectorFactory livingWthrIdxCollectorFactory) {
        Map<String, Supplier<List<PublicDataCollector>>> m = new LinkedHashMap<>();
        m.put("kma-village-forecast-ultra-srt-ncst", locationCollectorFactory::ultraSrtNcstCollectors);
        m.put("kma-village-forecast-ultra-srt-fcst", locationCollectorFactory::ultraSrtFcstCollectors);
        m.put("kma-village-forecast-vilage-fcst", locationCollectorFactory::vilageFcstCollectors);
        m.put("kma-weather-warning-list", () -> List.of(kmaWeatherWarningListCollector));
        m.put("kma-asos-hourly", () -> List.of(kmaAsosHourlyCollector));
        m.put("airkorea-realtime-measure", () -> List.of(airKoreaRealtimeCollector));
        m.put("airkorea-dust-forecast", () -> List.of(airKoreaDustForecastCollector));
        // 법령+행정규칙 전체를 한 틱(operationKey moleg-criminal-law)이 함께 처리한다
        m.put("moleg-criminal-law", lawCollectorFactory::allLawCollectors);
        m.put("safetydata-disaster-msg-list", () -> List.of(disasterMsgCollector));
        m.put("kma-living-uv-idx", livingWthrIdxCollectorFactory::uvIdxCollectors);
        m.put("kma-living-air-diffusion-idx", livingWthrIdxCollectorFactory::airDiffusionIdxCollectors);
        this.byOperation = Collections.unmodifiableMap(m);
    }

    public Set<String> operationKeys() {
        return byOperation.keySet();
    }

    /** @return 그 오퍼레이션의 컬렉터 묶음. 등록되지 않은 오퍼레이션이면 비어 있다 */
    public Optional<List<PublicDataCollector>> collectors(String operationKey) {
        return Optional.ofNullable(byOperation.get(operationKey)).map(Supplier::get);
    }
}
