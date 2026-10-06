package egovframework.external.publicdata.scheduler;

import egovframework.external.publicdata.scheduler.mapper.ScheduleSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleSettingSyncTest {

    @Mock
    private ObjectProvider<ScheduleSettingMapper> mapperProvider;
    @Mock
    private ScheduleSettingMapper mapper;

    private final ScheduleActivation activation = new ScheduleActivation();

    private ScheduleSettingSync sync() {
        return new ScheduleSettingSync(mapperProvider, activation);
    }

    private static Map<String, Object> row(String dataTypeCd, String schdCd, String activeYn) {
        Map<String, Object> row = new HashMap<>();
        row.put("dataTypeCd", dataTypeCd);
        row.put("schdCd", schdCd);
        row.put("activeYn", activeYn);
        return row;
    }

    @Test
    void DB_행을_읽어_사용_여부를_반영한다() {
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);
        when(mapper.selectExternalSettings()).thenReturn(List.of(
            row("EXTERNAL_PUBLIC", null, "N"),
            row("EXTERNAL_PUBLIC", "KMA_ASOS_HOURLY", "Y"),
            row("EXTERNAL_LAW", null, "Y")));

        assertThat(sync().sync()).isEqualTo(3);

        assertThat(activation.isActive("kma-living-uv-idx")).isFalse();
        assertThat(activation.isActive("kma-asos-hourly")).isTrue();
        assertThat(activation.isActive("moleg-criminal-law")).isTrue();
    }

    @Test
    void admin_db를_쓸_수_없으면_아무것도_하지_않는다() {
        when(mapperProvider.getIfAvailable()).thenReturn(null);

        assertThat(sync().sync()).isEqualTo(-1);
        assertThat(activation.isActive("kma-asos-hourly")).isTrue();
    }

    @Test
    void 조회에_실패하면_기존_값을_유지한다() {
        activation.apply("EXTERNAL_PUBLIC", null, false);
        when(mapperProvider.getIfAvailable()).thenReturn(mapper);
        when(mapper.selectExternalSettings()).thenThrow(new IllegalStateException("db down"));

        assertThat(sync().sync()).isEqualTo(-1);
        assertThat(activation.isActive("kma-asos-hourly")).isFalse();
    }
}
