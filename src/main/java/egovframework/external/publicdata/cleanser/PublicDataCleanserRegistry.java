package egovframework.external.publicdata.cleanser;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@code operationKey}로 알맞은 {@link PublicDataCleanser}를 찾는 레지스트리.
 * {@link egovframework.external.publicdata.collector.PublicDataCollectorRegistry}와 달리
 * 정제기는 지역별로 늘어나지 않고 오퍼레이션당 1개뿐이라(4개) 팩토리 없이 Spring 빈
 * 목록만으로 충분하다.
 */
@Component
public class PublicDataCleanserRegistry {

    private final List<PublicDataCleanser> cleansers;

    public PublicDataCleanserRegistry(List<PublicDataCleanser> cleansers) {
        this.cleansers = cleansers;
    }

    public Optional<PublicDataCleanser> find(String operationKey) {
        return cleansers.stream().filter(c -> c.supports(operationKey)).findFirst();
    }
}
