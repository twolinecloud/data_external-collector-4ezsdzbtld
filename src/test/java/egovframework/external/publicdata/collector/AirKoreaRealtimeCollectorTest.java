package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AirKoreaRealtimeCollector}의 계약 검증 - 실제 호출은 하지 않는다
 * ({@link AirKoreaApiClientTest}가 응답 처리를 담당).
 */
class AirKoreaRealtimeCollectorTest {

    private final AirKoreaRealtimeCollector collector =
        new AirKoreaRealtimeCollector(new AirKoreaApiClient(null), "https://example.invalid", "test-key");

    @Test
    void 전국을_한_번에_받으므로_시도별_인스턴스가_없다() {
        assertThat(collector.key()).isEqualTo("airkorea-realtime-measure");
        assertThat(collector.facilityId()).isNull();
        assertThat(collector.operationKey()).isEqualTo(collector.key());
    }

    @Test
    void 법령이_아니므로_EXTERNAL_PUBLIC_배치로_분류된다() {
        // DataTypeClassifier가 "법령 목록"만 관리하고 나머지를 공공데이터로 떨구므로 별도
        // 등록이 필요 없다 - 새 오퍼레이션이 조용히 누락되지 않는지 확인.
        assertThat(egovframework.external.logcollector.DataTypeClassifier.dataTypeCd(collector.operationKey()))
            .isEqualTo(egovframework.external.logcollector.DataTypeClassifier.EXTERNAL_PUBLIC);
    }

    @Test
    void 다른_기상값과_같이_날짜_기준_유효기간을_따른다() {
        // 같은 화면(어제/오늘/내일)에 쓰이므로 보관 규칙도 같아야 한다.
        assertThat(collector.stagingExpiresAt(LocalDate.of(2026, 9, 1)))
            .isEqualTo(LocalDateTime.of(2026, 9, 3, 0, 0));
    }
}
