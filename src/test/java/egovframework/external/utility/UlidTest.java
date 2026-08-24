package egovframework.external.utility;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UlidTest {

    @Test
    void 생성된_ULID는_항상_26자다() {
        for (int i = 0; i < 100; i++) {
            assertThat(Ulid.generate()).hasSize(26);
        }
    }

    @Test
    void VARCHAR_30_컬럼에_여유있게_들어간다() {
        assertThat(Ulid.generate().length()).isLessThanOrEqualTo(30);
    }

    @Test
    void 대량_생성해도_충돌이_없다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(Ulid.generate())).as("중복 발생").isTrue();
        }
    }

    @Test
    void Crockford_Base32_알파벳만_사용한다() {
        String ulid = Ulid.generate();
        assertThat(ulid).matches("[0-9A-HJKMNP-TV-Z]{26}"); // I, L, O, U 제외
    }

    @Test
    void 같은_밀리초_타임스탬프면_앞_10자가_동일하다() {
        long fixedMillis = 1_755_000_000_000L;
        String a = Ulid.generate(fixedMillis);
        String b = Ulid.generate(fixedMillis);

        assertThat(a.substring(0, 10)).isEqualTo(b.substring(0, 10));
        assertThat(a.substring(10)).isNotEqualTo(b.substring(10)); // 난수부는 다름(높은 확률)
    }

    @Test
    void 타임스탬프가_커질수록_앞부분_정렬순서도_커진다() {
        String earlier = Ulid.generate(1_755_000_000_000L);
        String later = Ulid.generate(1_755_000_001_000L);

        assertThat(later.substring(0, 10)).isGreaterThan(earlier.substring(0, 10));
    }
}
