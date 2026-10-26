package egovframework.external.publicdata.transfer;

import egovframework.external.staging.RawStagingStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 전송 패키지의 빈이 스프링에서 실제로 만들어지는지 확인한다. 단위 테스트는 생성자를 직접 호출해서, 생성자가 둘인데
 * {@code @Autowired}가 없는 실수(배포 시 "No default constructor found"로 기동 실패)를 잡지 못한다.
 */
class TransferBeanWiringTest {

    @Configuration
    @ComponentScan(basePackageClasses = LawTransferService.class,
        useDefaultFilters = true,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test.*"))
    static class Config {
        @Bean
        RawStagingStore rawStagingStore() {
            return Mockito.mock(RawStagingStore.class);
        }
    }

    @Test
    void 전송_빈이_모두_생성된다() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("t", Map.of(
                "public-data.law-transfer.xenon.base-url", "http://127.0.0.1:1/",
                "public-data.law-transfer.xenon.api-key", "x")));
            // 실제 앱(SpringApplication)과 같게 "60s" 같은 값을 Duration 으로 바꾸는 변환기를 붙인다.
            ctx.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            ctx.register(Config.class);
            ctx.refresh();
            assertNotNull(ctx.getBean(LawTransferService.class));
            assertNotNull(ctx.getBean(LawFileFetcher.class));
            assertNotNull(ctx.getBean(LawIngestPort.class));
        }
    }
}
