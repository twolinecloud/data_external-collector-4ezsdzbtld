package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KmaAsosHourlyCollector}의 계약 검증 - 실제 API 호출은 하지 않는다
 * ({@link KmaApiHubClientTest}가 응답 파싱을 담당). 여기서는 필드명 정의가 실측 응답과
 * 맞는지, 그리고 다른 기상 수집기와 같은 보관 규칙을 따르는지만 본다.
 */
class KmaAsosHourlyCollectorTest {

    private final KmaAsosHourlyCollector collector =
        new KmaAsosHourlyCollector(new KmaApiHubClient(null), "https://example.invalid", "test-key");

    @Test
    void 필드명은_실측_응답의_컬럼_수와_같은_46개다() {
        // 2026-09-03 실측: kma_sfctm2.php 데이터 줄이 전 지점 46컬럼이었다. 이 수가 어긋나면
        // KmaApiHubClient가 수집을 실패시키므로, 정의 자체를 여기서 고정해둔다.
        assertThat(KmaAsosHourlyCollector.FIELD_NAMES).hasSize(46);
    }

    @Test
    void 안개_유도에_쓰는_시정과_습도_필드가_정의에_있다() {
        assertThat(KmaAsosHourlyCollector.FIELD_NAMES).contains("VS", "HM");
    }

    @Test
    void 일기_관련_필드도_함께_담는다() {
        // 지점이 적어 판정에는 못 쓰지만(97개 중 9개), 시정·습도 유도 결과를 검증할 때
        // 정답지 역할을 하므로 원본에 남겨둔다.
        assertThat(KmaAsosHourlyCollector.FIELD_NAMES).contains("WW", "WC", "WP", "IX");
    }

    @Test
    void 필드명에_중복이_없다() {
        // JSONObject는 같은 키를 덮어쓰므로 중복이 있으면 컬럼 수 검사를 통과하고도 값이 사라진다.
        assertThat(KmaAsosHourlyCollector.FIELD_NAMES).doesNotHaveDuplicates();
    }

    @Test
    void 전국을_한_번에_받으므로_지역별_인스턴스가_없다() {
        assertThat(collector.key()).isEqualTo("kma-asos-hourly");
        assertThat(collector.facilityId()).isNull();
        assertThat(collector.operationKey()).isEqualTo(collector.key());
    }

    @Test
    void 다른_기상값과_같이_날짜_기준_유효기간을_따른다() {
        assertThat(collector.stagingExpiresAt(LocalDate.of(2026, 9, 1)))
            .isEqualTo(LocalDateTime.of(2026, 9, 3, 0, 0));
    }

    @Test
    void 필드명_순서가_응답_컬럼_순서와_일치한다() {
        List<String> names = KmaAsosHourlyCollector.FIELD_NAMES;

        // 앞뒤 경계와, 유도에 쓰는 두 필드의 위치를 실측 응답 기준으로 고정한다.
        assertThat(names.get(0)).isEqualTo("TM");
        assertThat(names.get(1)).isEqualTo("STN");
        assertThat(names.get(13)).isEqualTo("HM");
        assertThat(names.get(32)).isEqualTo("VS");
        assertThat(names.get(45)).isEqualTo("IX");
    }
}
