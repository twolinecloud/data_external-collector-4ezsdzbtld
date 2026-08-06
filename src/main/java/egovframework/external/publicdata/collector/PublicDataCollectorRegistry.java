package egovframework.external.publicdata.collector;

import egovframework.external.exception.NotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring이 관리하는 모든 {@link PublicDataCollector} 빈을 key로 조회할 수 있게 모아둔 레지스트리.
 * 새 소스를 추가할 때는 {@link PublicDataCollector} 구현체에 {@code @Component}만 붙이면
 * 이 레지스트리와 수동 트리거 API에 자동으로 잡힌다.
 */
@Component
public class PublicDataCollectorRegistry {

    private static final Logger logger = LogManager.getLogger(PublicDataCollectorRegistry.class);

    private final Map<String, PublicDataCollector> collectorsByKey;

    public PublicDataCollectorRegistry(List<PublicDataCollector> collectors) {
        this.collectorsByKey = collectors.stream()
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
