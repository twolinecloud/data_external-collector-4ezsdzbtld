package egovframework.external.publicdata.cleanser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PublicDataCleanserRegistryTest {

    @Mock
    private PublicDataCleanser vilageFcstCleanser;

    @Mock
    private PublicDataCleanser ncstCleanser;

    @Test
    void operationKey를_지원하는_첫_정제기를_반환한다() {
        lenient().when(vilageFcstCleanser.supports("kma-village-forecast-vilage-fcst")).thenReturn(true);
        lenient().when(ncstCleanser.supports("kma-village-forecast-vilage-fcst")).thenReturn(false);
        PublicDataCleanserRegistry registry = new PublicDataCleanserRegistry(List.of(ncstCleanser, vilageFcstCleanser));

        Optional<PublicDataCleanser> found = registry.find("kma-village-forecast-vilage-fcst");

        assertThat(found).contains(vilageFcstCleanser);
    }

    @Test
    void 아무도_지원하지_않으면_빈_Optional을_반환한다() {
        lenient().when(vilageFcstCleanser.supports("unknown-key")).thenReturn(false);
        lenient().when(ncstCleanser.supports("unknown-key")).thenReturn(false);
        PublicDataCleanserRegistry registry = new PublicDataCleanserRegistry(List.of(vilageFcstCleanser, ncstCleanser));

        Optional<PublicDataCleanser> found = registry.find("unknown-key");

        assertThat(found).isEmpty();
    }
}
