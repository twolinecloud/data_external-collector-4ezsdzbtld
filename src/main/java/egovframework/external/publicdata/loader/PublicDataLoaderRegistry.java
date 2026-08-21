package egovframework.external.publicdata.loader;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@code operationKey}로 알맞은 {@link PublicDataLoader}를 찾는 레지스트리.
 * {@code PublicDataCleanserRegistry}와 동일한 패턴 - {@code public-data.load.enabled=false}면
 * {@code PublicDataLoader} 빈이 하나도 없어(각 구현체가 조건부 등록) {@code loaders}가 빈
 * 리스트로 주입되는데, 그래도 이 레지스트리 자체는 정상 생성된다(문제 없음 - find()가 항상
 * empty를 반환할 뿐).
 */
@Component
public class PublicDataLoaderRegistry {

    private final List<PublicDataLoader> loaders;

    public PublicDataLoaderRegistry(List<PublicDataLoader> loaders) {
        this.loaders = loaders;
    }

    public Optional<PublicDataLoader> find(String operationKey) {
        return loaders.stream().filter(l -> l.supports(operationKey)).findFirst();
    }
}
