package egovframework.external.rule;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 실측 title 형식(2026-08-14~18) 기반 검증. */
class WarningTitleParserTest {

    @Test
    void 발표문에서_현상_단계_상태를_뽑는다() {
        Optional<WarningTitleParser.ParsedWarning> r = WarningTitleParser.parse(
            "[특보] 제08-24호 : 2026.08.14.10:00 / 호우주의보 발표 (*)");

        assertThat(r).isPresent();
        assertThat(r.get().phenomenon()).isEqualTo("호우");
        assertThat(r.get().level()).isEqualTo("주의보");
        assertThat(r.get().status()).isEqualTo("발표");
        assertThat(r.get().isActive()).isTrue();
    }

    @Test
    void 해제문도_파싱되고_isActive는_false다() {
        Optional<WarningTitleParser.ParsedWarning> r = WarningTitleParser.parse(
            "[특보] 제08-47호 : 2026.08.14.11:00 / 폭염주의보 해제 (*)");

        assertThat(r).isPresent();
        assertThat(r.get().phenomenon()).isEqualTo("폭염");
        assertThat(r.get().isActive()).isFalse();
    }

    @Test
    void 경보문도_파싱된다() {
        Optional<WarningTitleParser.ParsedWarning> r = WarningTitleParser.parse(
            "[특보] 제08-99호 : 2026.08.14.11:00 / 풍랑경보 발표 (*)");

        assertThat(r).isPresent();
        assertThat(r.get().level()).isEqualTo("경보");
    }

    @Test
    void 형식이_안맞으면_빈값이다() {
        assertThat(WarningTitleParser.parse("전혀 다른 형식의 텍스트")).isEmpty();
        assertThat(WarningTitleParser.parse(null)).isEmpty();
    }
}
