package egovframework.external.publicdata.collector;

import egovframework.external.exception.NotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 모든 {@link PublicDataCollector}를 key로 조회할 수 있게 모아둔 레지스트리.
 *
 * <p>
 * 세 종류를 합쳐서 관리한다: (1) Spring이 관리하는 빈 - 새 소스를 추가할 때
 * {@link PublicDataCollector} 구현체에 {@code @Component}만 붙이면 자동으로 잡힘.
 * (2) {@link KmaLocationCollectorFactory}가 지역(59개소)×오퍼레이션 조합으로 만들어내는
 * 인스턴스 - Bean 177개를 등록하는 대신 팩토리 하나로 관리
 * 교정기관이 변경되는 경우 초기설계로는 resource의 csv를 변경하고 서비스를 다시 로딩함으로서 업데이트하도록 함.
 * (hot-reload 아님)
 * (3) {@link MolegLawCollectorFactory}가 형사법령 44건만큼 만들어내는 인스턴스 - 마찬가지로
 * 개별 빈 대신 팩토리로 관리, 법령 추가/제외도 CSV만 고치면 됨(hot-reload 아님, 재기동 필요).
 * (4) {@link LivingWthrIdxCollectorFactory}가 생활기상지수 2개 오퍼레이션 × 16개 시도만큼
 * 만들어내는 인스턴스(2026-08-24 추가) - 동일한 팩토리 관리 원칙.
 * </p>
 */
@Component
public class PublicDataCollectorRegistry {

    private static final Logger logger = LogManager.getLogger(PublicDataCollectorRegistry.class);

    private final Map<String, PublicDataCollector> collectorsByKey;

    public PublicDataCollectorRegistry(List<PublicDataCollector> springManagedCollectors,
            KmaLocationCollectorFactory locationCollectorFactory,
            MolegLawCollectorFactory lawCollectorFactory,
            LivingWthrIdxCollectorFactory livingWthrIdxCollectorFactory) {
        List<PublicDataCollector> all = new ArrayList<>(springManagedCollectors);
        all.addAll(locationCollectorFactory.allLocationBasedCollectors());
        all.addAll(lawCollectorFactory.allLawCollectors());
        all.addAll(livingWthrIdxCollectorFactory.uvIdxCollectors());
        all.addAll(livingWthrIdxCollectorFactory.airDiffusionIdxCollectors());
        this.collectorsByKey = all.stream()
                .collect(Collectors.toUnmodifiableMap(PublicDataCollector::key, Function.identity()));
    }

    /** @throws NotFoundException key에 해당하는 수집기가 없는 경우 (HTTP 404로 매핑됨) */
    public PublicDataCollector get(String key) {
        PublicDataCollector collector = collectorsByKey.get(key);
        if (collector == null) {
            throw new NotFoundException(logger, "Unknown collector key: " + key);
        }
        return collector;
    }

    public Collection<PublicDataCollector> all() {
        return collectorsByKey.values();
    }
}
