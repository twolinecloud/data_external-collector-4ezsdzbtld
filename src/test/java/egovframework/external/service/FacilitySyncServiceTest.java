package egovframework.external.service;

import egovframework.external.model.FacilitySyncResult;
import egovframework.external.publicdata.loader.mapper.FacilityReviewQueueMapper;
import egovframework.external.publicdata.loader.mapper.InstitutionDimMapper;
import egovframework.external.publicdata.loader.mapper.WeatherFacilityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FacilitySyncService}의 tb_dim_instt ↔ tb_ext_weather_facility 대조 로직 검증.
 * enabled=false 전면 no-op 가드, 신규/제외 탐지, 중복 큐 등록 방지를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class FacilitySyncServiceTest {

    @Mock
    private InstitutionDimMapper institutionDimMapper;

    @Mock
    private WeatherFacilityMapper weatherFacilityMapper;

    @Mock
    private FacilityReviewQueueMapper reviewQueueMapper;

    private FacilitySyncService service(boolean enabled) {
        return new FacilitySyncService(institutionDimMapper, weatherFacilityMapper, reviewQueueMapper, enabled);
    }

    @Test
    void enabled가_false면_아무_매퍼도_호출하지_않고_빈_결과를_반환한다() {
        FacilitySyncResult result = service(false).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(0, 0));
        verify(institutionDimMapper, never()).selectActiveCorrectionalFacilities();
    }

    @Test
    void tb_dim_instt에만_있는_시설은_NEW로_큐에_등록한다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "1270254", "corrInsttNm", "서울지방교정청"),
            row("corrInsttCd", "9999999", "corrInsttNm", "신규교도소")
        ));
        when(weatherFacilityMapper.selectAll()).thenReturn(List.of(
            row("facilityId", "1270254", "facilityNm", "서울지방교정청")
        ));
        when(reviewQueueMapper.countPending(anyString(), anyString())).thenReturn(0);

        FacilitySyncResult result = service(true).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(1, 0));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(reviewQueueMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().get("facilityId")).isEqualTo("9999999");
        assertThat(captor.getValue().get("changeType")).isEqualTo("NEW");
    }

    @Test
    void 우리에게만_있는_시설은_REMOVED로_큐에_등록한다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "1270254", "corrInsttNm", "서울지방교정청")
        ));
        when(weatherFacilityMapper.selectAll()).thenReturn(List.of(
            row("facilityId", "1270254", "facilityNm", "서울지방교정청"),
            row("facilityId", "1270401", "facilityNm", "천안지소")
        ));
        when(reviewQueueMapper.countPending(anyString(), anyString())).thenReturn(0);

        FacilitySyncResult result = service(true).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(0, 1));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(reviewQueueMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().get("facilityId")).isEqualTo("1270401");
        assertThat(captor.getValue().get("changeType")).isEqualTo("REMOVED");
    }

    @Test
    void 이미_PENDING인_변경분은_다시_등록하지_않는다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "9999999", "corrInsttNm", "신규교도소")
        ));
        when(weatherFacilityMapper.selectAll()).thenReturn(List.of());
        when(reviewQueueMapper.countPending("9999999", "NEW")).thenReturn(1);

        FacilitySyncResult result = service(true).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(0, 0));
        verify(reviewQueueMapper, never()).insert(any());
    }

    @Test
    void 양쪽에_다_있는_시설은_아무_변화도_없다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "1270254", "corrInsttNm", "서울지방교정청")
        ));
        when(weatherFacilityMapper.selectAll()).thenReturn(List.of(
            row("facilityId", "1270254", "facilityNm", "서울지방교정청")
        ));

        FacilitySyncResult result = service(true).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(0, 0));
        verify(reviewQueueMapper, never()).insert(any());
    }

    @Test
    void pendingQueue는_매퍼_결과를_그대로_반환한다() {
        List<Map<String, Object>> pending = List.of(row("facilityId", "9999999", "changeType", "NEW"));
        when(reviewQueueMapper.selectPending()).thenReturn(pending);

        assertThat(service(true).pendingQueue()).isSameAs(pending);
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
