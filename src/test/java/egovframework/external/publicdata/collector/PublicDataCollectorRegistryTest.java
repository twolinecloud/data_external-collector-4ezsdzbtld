package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import egovframework.external.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicDataCollectorRegistryTest {

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
        FakeCollector a = new FakeCollector("a");
        FakeCollector b = new FakeCollector("b");
        PublicDataCollectorRegistry registry = new PublicDataCollectorRegistry(List.of(a, b));

        assertThat(registry.get("a")).isSameAs(a);
        assertThat(registry.get("b")).isSameAs(b);
        assertThat(registry.all()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void 등록되지_않은_키로_조회하면_NotFoundException을_던진다_HTTP_404로_매핑됨() {
        PublicDataCollectorRegistry registry = new PublicDataCollectorRegistry(List.of(new FakeCollector("a")));

        assertThatThrownBy(() -> registry.get("no-such-key"))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("no-such-key");
    }
}
