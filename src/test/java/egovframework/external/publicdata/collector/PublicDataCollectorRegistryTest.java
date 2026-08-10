package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import egovframework.external.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicDataCollectorRegistryTest {

    @Mock
    private KmaLocationCollectorFactory locationCollectorFactory;

    @Mock
    private MolegLawCollectorFactory lawCollectorFactory;

    private static class FakeCollector implements PublicDataCollector {
        private final String key;

        FakeCollector(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String sourceName() {
            return "테스트 소스";
        }

        @Override
        public String apiName() {
            return "테스트 API";
        }

        @Override
        public List<String> collect() throws CollectException {
            return List.of();
        }
    }

    @Test
    void 등록된_키로_조회하면_해당_수집기를_반환한다() {
        lenient().when(locationCollectorFactory.allLocationBasedCollectors()).thenReturn(List.of());
        lenient().when(lawCollectorFactory.allLawCollectors()).thenReturn(List.of());
        FakeCollector a = new FakeCollector("a");
        FakeCollector b = new FakeCollector("b");
        PublicDataCollectorRegistry registry = new PublicDataCollectorRegistry(List.of(a, b), locationCollectorFactory, lawCollectorFactory);

        assertThat(registry.get("a")).isSameAs(a);
        assertThat(registry.get("b")).isSameAs(b);
        assertThat(registry.all()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void 등록되지_않은_키로_조회하면_NotFoundException을_던진다_HTTP_404로_매핑됨() {
        lenient().when(locationCollectorFactory.allLocationBasedCollectors()).thenReturn(List.of());
        lenient().when(lawCollectorFactory.allLawCollectors()).thenReturn(List.of());
        PublicDataCollectorRegistry registry = new PublicDataCollectorRegistry(List.of(new FakeCollector("a")), locationCollectorFactory, lawCollectorFactory);

        assertThatThrownBy(() -> registry.get("no-such-key"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("no-such-key");
    }

    @Test
    void 위치기반_팩토리가_만든_수집기도_함께_조회된다() {
        FakeCollector locationBased = new FakeCollector("kma-village-forecast-ultra-srt-ncst--f01");
        when(locationCollectorFactory.allLocationBasedCollectors()).thenReturn(List.of(locationBased));
        lenient().when(lawCollectorFactory.allLawCollectors()).thenReturn(List.of());

        PublicDataCollectorRegistry registry = new PublicDataCollectorRegistry(List.of(), locationCollectorFactory, lawCollectorFactory);

        assertThat(registry.get("kma-village-forecast-ultra-srt-ncst--f01")).isSameAs(locationBased);
        assertThat(registry.all()).containsExactly(locationBased);
    }

    @Test
    void 법령_팩토리가_만든_수집기도_함께_조회된다() {
        FakeCollector lawBased = new FakeCollector("moleg-criminal-law--001692");
        lenient().when(locationCollectorFactory.allLocationBasedCollectors()).thenReturn(List.of());
        when(lawCollectorFactory.allLawCollectors()).thenReturn(List.of(lawBased));

        PublicDataCollectorRegistry registry = new PublicDataCollectorRegistry(List.of(), locationCollectorFactory, lawCollectorFactory);

        assertThat(registry.get("moleg-criminal-law--001692")).isSameAs(lawBased);
        assertThat(registry.all()).containsExactly(lawBased);
    }
}
