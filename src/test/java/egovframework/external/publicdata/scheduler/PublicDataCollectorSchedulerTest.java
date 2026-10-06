package egovframework.external.publicdata.scheduler;

import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.AirKoreaDustForecastCollector;
import egovframework.external.publicdata.collector.AirKoreaRealtimeCollector;
import egovframework.external.publicdata.collector.DisasterMsgCollector;
import egovframework.external.publicdata.collector.KmaAsosHourlyCollector;
import egovframework.external.publicdata.collector.KmaLocationCollectorFactory;
import egovframework.external.publicdata.collector.KmaWeatherWarningListCollector;
import egovframework.external.publicdata.collector.LivingWthrIdxCollectorFactory;
import egovframework.external.publicdata.collector.MolegLawCollectorFactory;
import egovframework.external.service.PublicDataPipelineRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 스케줄 메서드가 쓰는 operationKey가 {@link ScheduledOperations}·{@link ScheduleCodes}와 어긋나지 않는지,
 * 사용 중지한 스케줄은 틱을 건너뛰는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PublicDataCollectorSchedulerTest {

    @Mock
    private PublicDataPipelineRunner pipelineRunner;
    @Mock
    private KmaLocationCollectorFactory locationCollectorFactory;
    @Mock
    private KmaWeatherWarningListCollector kmaWeatherWarningListCollector;
    @Mock
    private KmaAsosHourlyCollector kmaAsosHourlyCollector;
    @Mock
    private AirKoreaRealtimeCollector airKoreaRealtimeCollector;
    @Mock
    private AirKoreaDustForecastCollector airKoreaDustForecastCollector;
    @Mock
    private MolegLawCollectorFactory lawCollectorFactory;
    @Mock
    private DisasterMsgCollector disasterMsgCollector;
    @Mock
    private LivingWthrIdxCollectorFactory livingWthrIdxCollectorFactory;

    private final ScheduleActivation activation = new ScheduleActivation();

    private ScheduledOperations operations() {
        return new ScheduledOperations(locationCollectorFactory, kmaWeatherWarningListCollector, kmaAsosHourlyCollector,
            airKoreaRealtimeCollector, airKoreaDustForecastCollector, lawCollectorFactory, disasterMsgCollector,
            livingWthrIdxCollectorFactory);
    }

    private PublicDataCollectorScheduler scheduler() {
        return new PublicDataCollectorScheduler(pipelineRunner, operations(), activation);
    }

    private List<Method> scheduledMethods() {
        return Arrays.stream(PublicDataCollectorScheduler.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(Scheduled.class))
            .toList();
    }

    private void runAllScheduledMethods(PublicDataCollectorScheduler scheduler) throws Exception {
        for (Method method : scheduledMethods()) {
            method.invoke(scheduler);
        }
    }

    @Test
    void 스케줄_메서드가_쓰는_오퍼레이션은_전부_등록돼_있고_스케줄_코드가_있다() throws Exception {
        runAllScheduledMethods(scheduler());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(pipelineRunner, atLeastOnce()).collectAndAdvance(keys.capture(), any(), eq(ExecutionType.SCHEDULE), any());

        Set<String> ran = keys.getAllValues().stream().collect(Collectors.toSet());
        assertThat(scheduledMethods()).hasSize(11);
        assertThat(ran).isEqualTo(operations().operationKeys()).isEqualTo(ScheduleCodes.all().keySet());
    }

    @Test
    void 사용_중지한_오퍼레이션은_틱을_건너뛴다() throws Exception {
        activation.apply("EXTERNAL_PUBLIC", null, false);

        runAllScheduledMethods(scheduler());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(pipelineRunner, atLeastOnce()).collectAndAdvance(keys.capture(), any(), any(), any());
        assertThat(keys.getAllValues()).containsExactly("moleg-criminal-law");
        verify(pipelineRunner, never()).collectAndAdvance(eq("kma-asos-hourly"), any(), any(), any());
    }
}
