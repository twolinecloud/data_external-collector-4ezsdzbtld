package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code kma-facility-locations.csv}에 이미 기록된 lat/lon → nx/ny 쌍(사람이 검증한 값)과
 * 대조 - 59개소 전수 대조는 2026-08-24 스크립트로 별도 검증 완료(0건 불일치), 여기선 대표
 * 몇 건만 회귀 테스트로 고정.
 */
class KmaGridConverterTest {

    @Test
    void 서울지방교정청_좌표는_격자_60_124로_변환된다() {
        KmaGridConverter.Grid grid = KmaGridConverter.toGrid(37.4268286, 126.9847533);

        assertThat(grid.nx()).isEqualTo(60);
        assertThat(grid.ny()).isEqualTo(124);
    }

    @Test
    void 제주교도소_좌표는_격자_52_37로_변환된다() {
        // 제주는 본토와 격자 원점 방향이 크게 달라 회귀 검증 가치가 높음
        KmaGridConverter.Grid grid = KmaGridConverter.toGrid(33.462787, 126.514998);

        assertThat(grid.nx()).isEqualTo(52);
        assertThat(grid.ny()).isEqualTo(37);
    }
}
