package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndOfRunRetryTest {

    @Test
    void 기상청_데이터_없음과_통신_오류는_다시_해_볼_실패다() {
        assertThat(EndOfRunRetry.isTransient("KMA API 오류 - resultCode=03 (NODATA_ERROR-데이터없음) resultMsg=NO_DATA")).isTrue();
        assertThat(EndOfRunRetry.isTransient("API 호출 실패: I/O error on GET request")).isTrue();
        assertThat(EndOfRunRetry.isTransient("타임아웃(180000ms 초과)")).isTrue();
    }

    @Test
    void 응답은_정상인데_내용이_문제인_실패와_빈_값은_다시_하지_않는다() {
        assertThat(EndOfRunRetry.isTransient("법령 조회 실패: 이름 없음")).isFalse();
        assertThat(EndOfRunRetry.isTransient("KMA API 오류 - resultCode=30 (SERVICE_KEY_IS_NOT_REGISTERED_ERROR)")).isFalse();
        assertThat(EndOfRunRetry.isTransient(null)).isFalse();
    }

    @Test
    void 초단기_수집기는_데이터_없음을_끝에서_다시_시도한다() {
        FacilityLocationLoader loader = new FacilityLocationLoader(new CsvFacilityMasterSource(new FacilityMasterCsvLoader()));
        KmaLocationCollectorFactory factory = new KmaLocationCollectorFactory(
            new KmaApiClient(new org.springframework.web.client.RestTemplate()), "https://example.invalid", "k", loader);

        assertThat(factory.ultraSrtNcstCollectors().get(0).endOfRunRetry()).isEqualTo(EndOfRunRetry.KMA_NODATA);
        assertThat(factory.ultraSrtFcstCollectors().get(0).endOfRunRetry()).isEqualTo(EndOfRunRetry.KMA_NODATA);
        // 단기예보는 하루 8회 발표라 건드리지 않는다.
        assertThat(factory.vilageFcstCollectors().get(0).endOfRunRetry()).isEqualTo(EndOfRunRetry.NONE);
    }
}
