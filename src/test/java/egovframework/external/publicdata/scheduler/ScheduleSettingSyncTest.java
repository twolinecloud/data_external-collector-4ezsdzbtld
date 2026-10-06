package egovframework.external.publicdata.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleSettingSyncTest {

    @Mock
    private AdminBatchScheduleClient client;

    private final ScheduleActivation activation = new ScheduleActivation();

    private ScheduleSettingSync sync() {
        return new ScheduleSettingSync(client, activation);
    }

    private static AdminBatchScheduleClient.Row row(String dataTypeCd, String schdCd, String activeYn) {
        return new AdminBatchScheduleClient.Row(dataTypeCd, schdCd, activeYn);
    }

    @Test
    void 이_수집기가_담당하는_행만_골라_사용_여부를_반영한다() {
        when(client.isConfigured()).thenReturn(true);
        when(client.fetchAll()).thenReturn(List.of(
            row("STRUCTURED", null, "N"),
            row("UNSTRUCTURED", null, "N"),
            row("EXTERNAL_PUBLIC", null, "N"),
            row("EXTERNAL_PUBLIC", "KMA_ASOS_HOURLY", "Y"),
            row("EXTERNAL_LAW", " ", "Y")));

        assertThat(sync().sync()).isEqualTo(3);

        assertThat(activation.isActive("kma-living-uv-idx")).isFalse();
        assertThat(activation.isActive("kma-asos-hourly")).isTrue();
        assertThat(activation.isActive("moleg-criminal-law")).isTrue();
    }

    @Test
    void 관리자_서비스_주소가_없으면_호출하지_않는다() {
        when(client.isConfigured()).thenReturn(false);

        assertThat(sync().sync()).isEqualTo(-1);

        verify(client, never()).fetchAll();
        assertThat(activation.isActive("kma-asos-hourly")).isTrue();
    }

    @Test
    void 조회에_실패하면_기존_값을_유지한다() {
        activation.apply("EXTERNAL_PUBLIC", null, false);
        when(client.isConfigured()).thenReturn(true);
        when(client.fetchAll()).thenThrow(new RestClientException("down"));

        assertThat(sync().sync()).isEqualTo(-1);
        assertThat(activation.isActive("kma-asos-hourly")).isFalse();
    }

    @Test
    void 응답_형식이_이상해도_기존_값을_유지한다() {
        activation.apply("EXTERNAL_LAW", null, false);
        when(client.isConfigured()).thenReturn(true);
        when(client.fetchAll()).thenThrow(new IllegalStateException("result 없음"));

        assertThat(sync().sync()).isEqualTo(-1);
        assertThat(activation.isActive("moleg-criminal-law")).isFalse();
    }
}
