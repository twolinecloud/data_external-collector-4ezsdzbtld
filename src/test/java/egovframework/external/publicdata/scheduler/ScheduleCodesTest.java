package egovframework.external.publicdata.scheduler;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 스케줄 코드는 관리자 DB 행의 키라 형식 제약(대문자 시작, 최대 30자)과 유일성을 지켜야 한다. */
class ScheduleCodesTest {

    @Test
    void 모든_코드는_관리자_DB_제약_형식이고_유일하다() {
        assertThat(ScheduleCodes.all().values())
            .allSatisfy(code -> {
                assertThat(code).matches("^[A-Z][A-Z0-9_]*$");
                assertThat(code.length()).isLessThanOrEqualTo(30);
            })
            .doesNotHaveDuplicates();
    }

    @Test
    void 오퍼레이션_키와_스케줄_코드_어느_쪽으로_불러도_같은_오퍼레이션이다() {
        assertThat(ScheduleCodes.resolveOperation("kma-asos-hourly")).contains("kma-asos-hourly");
        assertThat(ScheduleCodes.resolveOperation("KMA_ASOS_HOURLY")).contains("kma-asos-hourly");
        assertThat(ScheduleCodes.resolveOperation("  kma_asos_hourly ")).contains("kma-asos-hourly");
    }

    @Test
    void 모르는_값은_비어_있다() {
        assertThat(ScheduleCodes.resolveOperation("nope")).isEqualTo(Optional.empty());
        assertThat(ScheduleCodes.resolveOperation(" ")).isEqualTo(Optional.empty());
        assertThat(ScheduleCodes.resolveOperation(null)).isEqualTo(Optional.empty());
        assertThat(ScheduleCodes.operationOf("NOPE")).isEqualTo(Optional.empty());
        assertThat(ScheduleCodes.schdCdOf("nope")).isNull();
    }

    @Test
    void 코드_왕복_변환이_된다() {
        ScheduleCodes.all().forEach((operation, code) -> {
            assertThat(ScheduleCodes.schdCdOf(operation)).isEqualTo(code);
            assertThat(ScheduleCodes.operationOf(code)).contains(operation);
        });
        assertThat(new HashSet<>(ScheduleCodes.all().keySet())).hasSize(11);
    }
}
