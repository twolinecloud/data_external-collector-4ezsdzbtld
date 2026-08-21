package egovframework.external.publicdata.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PublicDataLoaderRegistryTest {

    @Mock
    private PublicDataLoader vilageFcstLoader;

    @Mock
    private PublicDataLoader ncstLoader;

    @Test
    void operationKey를_지원하는_첫_적재기를_반환한다() {
        lenient().when(vilageFcstLoader.supports("kma-village-forecast-vilage-fcst")).thenReturn(true);
        lenient().when(ncstLoader.supports("kma-village-forecast-vilage-fcst")).thenReturn(false);
        PublicDataLoaderRegistry registry = new PublicDataLoaderRegistry(List.of(ncstLoader, vilageFcstLoader));

        Optional<PublicDataLoader> found = registry.find("kma-village-forecast-vilage-fcst");

        assertThat(found).contains(vilageFcstLoader);
    }

    @Test
    void 아무도_지원하지_않으면_빈_Optional을_반환한다() {
        lenient().when(vilageFcstLoader.supports("unknown-key")).thenReturn(false);
        lenient().when(ncstLoader.supports("unknown-key")).thenReturn(false);
        PublicDataLoaderRegistry registry = new PublicDataLoaderRegistry(List.of(vilageFcstLoader, ncstLoader));

        Optional<PublicDataLoader> found = registry.find("unknown-key");

        assertThat(found).isEmpty();
    }

    @Test
    void 빈_리스트로도_생성_가능하다() {
        PublicDataLoaderRegistry registry = new PublicDataLoaderRegistry(List.of());

        assertThat(registry.find("아무거나")).isEmpty();
    }
}
