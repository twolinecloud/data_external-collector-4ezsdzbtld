package egovframework.external.publicdata.cleanser;

import egovframework.external.publicdata.collector.FacilityMasterSource;
import egovframework.external.publicdata.collector.KmaWarningStationLoader;
import egovframework.external.publicdata.collector.KmaWarningZoneDirectory;
import egovframework.external.publicdata.collector.OpenTopoDataElevationLookup;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기상특보 정제기와 특보구역 자료가 스프링에서 실제로 만들어지고 서로 연결되는지 확인한다. 정제기는 생성자가 둘이라
 * {@code @Autowired}가 빠지면 배포 시 기동이 실패한다(2026-10-26 전송 빈에서 실제로 겪음).
 */
class WarningZoneBeanWiringTest {

    @Configuration
    @Import({KmaWeatherWarningListCleanser.class, KmaWarningZoneDirectory.class, KmaWarningStationLoader.class,
        OpenTopoDataElevationLookup.class})
    static class Config {
        @Bean
        FacilityMasterSource facilityMasterSource() {
            FacilityMasterSource source = Mockito.mock(FacilityMasterSource.class);
            Mockito.when(source.current()).thenReturn(List.of());
            return source;
        }

        @Bean(name = "kmaApiHubRestTemplate")
        RestTemplate kmaApiHubRestTemplate() {
            return Mockito.mock(RestTemplate.class);
        }
    }

    @Test
    void 정제기가_특보구역_자료와_함께_만들어진다() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            ctx.register(Config.class);
            ctx.refresh();

            KmaWeatherWarningListCleanser cleanser = ctx.getBean(KmaWeatherWarningListCleanser.class);
            var field = KmaWeatherWarningListCleanser.class.getDeclaredField("zoneDirectory");
            field.setAccessible(true);
            assertThat(field.get(cleanser)).isSameAs(ctx.getBean(KmaWarningZoneDirectory.class));
        }
    }
}
