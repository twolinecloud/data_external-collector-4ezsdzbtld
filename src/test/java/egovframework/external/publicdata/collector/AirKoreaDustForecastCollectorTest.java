package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AirKoreaDustForecastCollectorTest {

    private final AirKoreaDustForecastCollector collector =
        new AirKoreaDustForecastCollector(new AirKoreaApiClient(null), "https://example.invalid", "test-key");

    @Test
    void 전국을_한_번에_받으므로_지역별_인스턴스가_없다() {
        assertThat(collector.key()).isEqualTo("airkorea-dust-forecast");
        assertThat(collector.facilityId()).isNull();
        assertThat(collector.operationKey()).isEqualTo(collector.key());
    }

    @Test
    void 매일_자정에_값이_전부_갈아끼워지므로_유효기간이_하루뿐이다() {
        // searchDate가 항상 "오늘"로 고정이라 어제 수집분은 오늘 재조회해도 다시 안 나온다 -
        // 다른 기상값(어제 0시까지 보존)과 달리 짧게 잡는다.
        assertThat(collector.stagingExpiresAt(LocalDate.of(2026, 9, 4)))
            .isEqualTo(LocalDateTime.of(2026, 9, 5, 0, 0));
    }
}
