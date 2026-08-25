package egovframework.external.publicdata.loader;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaDateTimeSupportTest {

    @Test
    void 날짜와_시각을_합쳐_LocalDateTime을_만든다() {
        LocalDateTime result = KmaDateTimeSupport.combine("20260821", "1100");

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 8, 21, 11, 0));
    }

    @Test
    void 자정_시각도_정상_파싱된다() {
        LocalDateTime result = KmaDateTimeSupport.combine("20260101", "0000");

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void 분_없는_yyyyMMddHH_형식도_파싱한다() {
        LocalDateTime result = KmaDateTimeSupport.parseYyyyMMddHH("2026082412");

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 8, 24, 12, 0));
    }
}
