package egovframework.external.publicdata.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleActivationTest {

    private static final String PUBLIC_OP = "kma-asos-hourly";     // KMA_ASOS_HOURLY
    private static final String OTHER_PUBLIC_OP = "kma-living-uv-idx";
    private static final String LAW_OP = "moleg-criminal-law";     // MOLEG_LAW

    private final ScheduleActivation activation = new ScheduleActivation();

    @Test
    void 설정이_없으면_사용으로_본다() {
        assertThat(activation.isActive(PUBLIC_OP)).isTrue();
        assertThat(activation.isActive(LAW_OP)).isTrue();
    }

    @Test
    void 데이터_구분_대표_행을_끄면_그_구분의_모든_오퍼레이션이_꺼진다() {
        activation.apply("EXTERNAL_PUBLIC", null, false);

        assertThat(activation.isActive(PUBLIC_OP)).isFalse();
        assertThat(activation.isActive(OTHER_PUBLIC_OP)).isFalse();
        assertThat(activation.isActive(LAW_OP)).isTrue(); // 다른 구분은 영향 없음
    }

    @Test
    void 오퍼레이션_자기_행이_있으면_대표_행보다_우선한다() {
        activation.apply("EXTERNAL_PUBLIC", null, false);
        activation.apply("EXTERNAL_PUBLIC", "KMA_ASOS_HOURLY", true);

        assertThat(activation.isActive(PUBLIC_OP)).isTrue();
        assertThat(activation.isActive(OTHER_PUBLIC_OP)).isFalse();
    }

    @Test
    void 오퍼레이션만_끄면_그_오퍼레이션만_꺼진다() {
        activation.apply("EXTERNAL_PUBLIC", "KMA_ASOS_HOURLY", false);

        assertThat(activation.isActive(PUBLIC_OP)).isFalse();
        assertThat(activation.isActive(OTHER_PUBLIC_OP)).isTrue();
    }

    @Test
    void 전체_교체하면_DB에서_사라진_행의_오래된_값은_남지_않는다() {
        activation.apply("EXTERNAL_PUBLIC", "KMA_ASOS_HOURLY", false);
        activation.apply("EXTERNAL_LAW", null, false);

        activation.replaceAll(List.of(new ScheduleActivation.Setting("EXTERNAL_PUBLIC", "KMA_LIVING_UV", false)));

        assertThat(activation.isActive(PUBLIC_OP)).isTrue();
        assertThat(activation.isActive(LAW_OP)).isTrue();
        assertThat(activation.isActive(OTHER_PUBLIC_OP)).isFalse();
    }
}
