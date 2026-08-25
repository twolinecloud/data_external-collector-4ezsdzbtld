package egovframework.external.service;

import egovframework.external.model.FacilitySyncResult;
import egovframework.external.publicdata.collector.GeocodeResult;
import egovframework.external.publicdata.collector.VWorldGeocoder;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FacilitySyncService}의 tb_dim_instt ↔ tb_ext_weather_facility 대조 로직 +
 * 자동 지오코딩(Phase B) + 승인/거부 워크플로 검증.
 */
@ExtendWith(MockitoExtension.class)
class FacilitySyncServiceTest {

    @Mock
    private InstitutionDimMapper institutionDimMapper;

    @Mock
    private WeatherFacilityMapper weatherFacilityMapper;

    @Mock
    private FacilityReviewQueueMapper reviewQueueMapper;

    @Mock
    private VWorldGeocoder vWorldGeocoder;

    private FacilitySyncService service(boolean enabled) {
        return new FacilitySyncService(institutionDimMapper, weatherFacilityMapper, reviewQueueMapper, vWorldGeocoder, enabled);
    }

    @Test
    void enabled가_false면_아무_매퍼도_호출하지_않고_빈_결과를_반환한다() {
        FacilitySyncResult result = service(false).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(0, 0));
        verify(institutionDimMapper, never()).selectActiveCorrectionalFacilities();
    }

    @Test
    void 신규_시설은_지오코딩_성공하면_제안좌표까지_채워서_큐에_등록한다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "9999999", "corrInsttNm", "신규교도소", "dtladr", "경기도 과천시 관문로 47")
        ));
        when(weatherFacilityMapper.selectAll()).thenReturn(List.of());
        when(reviewQueueMapper.countPending(anyString(), anyString())).thenReturn(0);
        when(vWorldGeocoder.geocode("경기도 과천시 관문로 47"))
            .thenReturn(new GeocodeResult(GeocodeResult.SUCCESS, 37.4266, 126.9835, "경기도", "과천시"));

        FacilitySyncResult result = service(true).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(1, 0));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(reviewQueueMapper, times(1)).insert(captor.capture());
        Map<String, Object> p = captor.getValue();
        assertThat(p.get("facilityId")).isEqualTo("9999999");
        assertThat(p.get("changeType")).isEqualTo("NEW");
        assertThat(p.get("geocodeStatus")).isEqualTo(GeocodeResult.SUCCESS);
        assertThat(p.get("proposedLat")).isEqualTo(37.4266);
        assertThat(p.get("proposedLon")).isEqualTo(126.9835);
        assertThat(p.get("proposedNx")).isNotNull();
        assertThat(p.get("proposedNy")).isNotNull();
        assertThat(p.get("proposedSidoNm")).isEqualTo("경기도");
    }

    @Test
    void 신규_시설은_지오코딩_실패해도_제안좌표_없이_큐에는_등록된다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "9999999", "corrInsttNm", "신규교도소", "dtladr", "충청북도 청주시 서원구 청남로0000번길 00")
        ));
        when(weatherFacilityMapper.selectAll()).thenReturn(List.of());
        when(reviewQueueMapper.countPending(anyString(), anyString())).thenReturn(0);
        when(vWorldGeocoder.geocode(anyString())).thenReturn(GeocodeResult.notFound());

        FacilitySyncResult result = service(true).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(1, 0));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(reviewQueueMapper, times(1)).insert(captor.capture());
        Map<String, Object> p = captor.getValue();
        assertThat(p.get("geocodeStatus")).isEqualTo(GeocodeResult.NOT_FOUND);
        assertThat(p.get("proposedLat")).isNull();
    }

    @Test
    void 우리에게만_있는_시설은_REMOVED로_큐에_등록한다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "1270254", "corrInsttNm", "서울지방교정청", "dtladr", "경기도 과천시 관문로 47")
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
        verify(vWorldGeocoder, never()).geocode(anyString());
    }

    @Test
    void 이미_PENDING인_변경분은_다시_등록하지_않는다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "9999999", "corrInsttNm", "신규교도소", "dtladr", "아무 주소")
        ));
        when(weatherFacilityMapper.selectAll()).thenReturn(List.of());
        when(reviewQueueMapper.countPending("9999999", "NEW")).thenReturn(1);

        FacilitySyncResult result = service(true).sync();

        assertThat(result).isEqualTo(new FacilitySyncResult(0, 0));
        verify(reviewQueueMapper, never()).insert(any());
        verify(vWorldGeocoder, never()).geocode(anyString());
    }

    @Test
    void 양쪽에_다_있는_시설은_아무_변화도_없다() {
        when(institutionDimMapper.selectActiveCorrectionalFacilities()).thenReturn(List.of(
            row("corrInsttCd", "1270254", "corrInsttNm", "서울지방교정청", "dtladr", "경기도 과천시 관문로 47")
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

    @Test
    void approve는_제안좌표를_그대로_써서_tb_ext_weather_facility에_반영한다() {
        when(reviewQueueMapper.selectById("r1")).thenReturn(row(
            "facilityId", "9999999", "facilityNm", "신규교도소",
            "proposedLat", 37.4266, "proposedLon", 126.9835,
            "proposedSidoNm", "경기도", "proposedSigunguNm", "과천시"
        ));

        service(true).approve("r1", null, null);

        verify(weatherFacilityMapper, times(1)).upsert(
            eq("9999999"), eq("신규교도소"), eq(37.4266), eq(126.9835),
            eq("경기도"), eq("과천시"), anyInt(), anyInt());
        verify(reviewQueueMapper, times(1)).updateStatus("r1", "RESOLVED");
    }

    @Test
    void approve에_좌표를_직접_넘기면_그_값을_우선한다() {
        when(reviewQueueMapper.selectById("r1")).thenReturn(row(
            "facilityId", "9999999", "facilityNm", "신규교도소",
            "proposedLat", null, "proposedLon", null
        ));

        service(true).approve("r1", 37.1, 127.1);

        verify(weatherFacilityMapper, times(1)).upsert(
            eq("9999999"), eq("신규교도소"), eq(37.1), eq(127.1),
            eq(null), eq(null), anyInt(), anyInt());
    }

    @Test
    void approve시_좌표가_아예_없으면_예외를_던진다() {
        when(reviewQueueMapper.selectById("r1")).thenReturn(row(
            "facilityId", "9999999", "facilityNm", "신규교도소",
            "proposedLat", null, "proposedLon", null
        ));

        assertThatThrownBy(() -> service(true).approve("r1", null, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(weatherFacilityMapper, never()).upsert(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void approve시_큐_항목이_없으면_예외를_던진다() {
        when(reviewQueueMapper.selectById("no-such")).thenReturn(null);

        assertThatThrownBy(() -> service(true).approve("no-such", 37.0, 127.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reject는_IGNORED로_상태만_바꾸고_DB_반영은_안_한다() {
        service(true).reject("r1");

        verify(reviewQueueMapper, times(1)).updateStatus("r1", "IGNORED");
        verify(weatherFacilityMapper, never()).upsert(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
