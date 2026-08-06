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
 * <p>두 종류를 합쳐서 관리한다: (1) Spring이 관리하는 빈 - 새 소스를 추가할 때
 * {@link PublicDataCollector} 구현체에 {@code @Component}만 붙이면 자동으로 잡힘.
 * (2) {@link KmaLocationCollectorFactory}가 지역(59개소)×오퍼레이션 조합으로 만들어내는
 * 인스턴스 - Bean 177개를 등록하는 대신 팩토리 하나로 관리 (private-doc 25번 항목).</p>
 */
@Component
public class PublicDataCollectorRegistry {

    private static final Logger logger = LogManager.getLogger(PublicDataCollectorRegistry.class);

    private final Map<String, PublicDataCollector> collectorsByKey;

    public PublicDataCollectorRegistry(List<PublicDataCollector> springManagedCollectors,
                                        KmaLocationCollectorFactory locationCollectorFactory) {
        List<PublicDataCollector> all = new ArrayList<>(springManagedCollectors);
        all.addAll(locationCollectorFactory.allLocationBasedCollectors());
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
