package egovframework.external.service;

import egovframework.external.dto.ScheduleRefreshRequest;
import egovframework.external.exception.InvalidParameterException;
import egovframework.external.exception.NotFoundException;
import egovframework.external.publicdata.scheduler.ScheduleActivation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleRefreshServiceTest {

    private final ScheduleActivation activation = new ScheduleActivation();
    private final ScheduleRefreshService service = new ScheduleRefreshService(activation);

    private static ScheduleRefreshRequest request(String dataTypeCd, String schdCd, String activeYn) {
        return new ScheduleRefreshRequest(dataTypeCd, schdCd, activeYn, "INTERVAL_BASED", "01:00");
    }

    @Test
    void 대표_행_요청은_데이터_구분_전체의_사용_여부를_바꾼다() {
        service.apply(request("EXTERNAL_PUBLIC", null, "N"));

        assertThat(activation.isActive("kma-asos-hourly")).isFalse();
        assertThat(activation.isActive("moleg-criminal-law")).isTrue();
    }

    @Test
    void 세부_스케줄_요청은_그_오퍼레이션만_바꾼다() {
        service.apply(request("EXTERNAL_PUBLIC", "KMA_ASOS_HOURLY", "N"));

        assertThat(activation.isActive("kma-asos-hourly")).isFalse();
        assertThat(activation.isActive("kma-living-uv-idx")).isTrue();
    }

    @Test
    void 다시_Y로_바꾸면_켜진다() {
        service.apply(request("EXTERNAL_LAW", null, "N"));
        service.apply(request("EXTERNAL_LAW", " ", "Y"));

        assertThat(activation.isActive("moleg-criminal-law")).isTrue();
    }

    @Test
    void 담당하지_않는_데이터_구분은_거절한다() {
        assertThatThrownBy(() -> service.apply(request("STRUCTURED", null, "Y")))
            .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.apply(null)).isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void activeYn이_Y_N이_아니면_거절한다() {
        assertThatThrownBy(() -> service.apply(request("EXTERNAL_PUBLIC", null, "X")))
            .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.apply(request("EXTERNAL_PUBLIC", null, null)))
            .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void 모르는_스케줄_코드는_404이고_다른_구분의_코드는_거절한다() {
        assertThatThrownBy(() -> service.apply(request("EXTERNAL_PUBLIC", "NOPE", "N")))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.apply(request("EXTERNAL_LAW", "KMA_ASOS_HOURLY", "N")))
            .isInstanceOf(InvalidParameterException.class);
        assertThat(activation.isActive("kma-asos-hourly")).isTrue();
    }
}
