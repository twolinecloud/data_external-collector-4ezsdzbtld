package egovframework.external.publicdata.source;

import egovframework.external.publicdata.collector.AirKoreaApiClient;
import egovframework.external.publicdata.collector.DisasterMsgCollector;
import egovframework.external.publicdata.collector.KmaApiClient;
import egovframework.external.publicdata.collector.KmaApiHubClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 실제 앱의 빈 구성(RestTemplate 3개, 클라이언트들의 주입 대상)이 새 포트로 연결되는지 확인한다. */
class ExternalApiPortWiringTest {

    @Configuration
    @Import({DirectExternalApiAdapter.class, RoutingExternalApiPort.class, KmaApiClient.class,
        AirKoreaApiClient.class, KmaApiHubClient.class, DisasterMsgCollector.class})
    static class Config {
        @Bean RestTemplate restTemplate() { return new RestTemplate(); }
        @Bean RestTemplate kmaApiHubRestTemplate() { return new RestTemplate(); }
        @Bean RestTemplate logCollectorRestTemplate() { return new RestTemplate(); }
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> props) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("t", props));
        ctx.register(Config.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    void 설정이_없으면_클라이언트들이_모두_기동되고_포트는_라우터다() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of())) {
            assertInstanceOf(RoutingExternalApiPort.class, ctx.getBean(ExternalApiPort.class));
            assertNotNull(ctx.getBean(KmaApiClient.class));
            assertNotNull(ctx.getBean(AirKoreaApiClient.class));
            assertNotNull(ctx.getBean(KmaApiHubClient.class));
            assertNotNull(ctx.getBean(DisasterMsgCollector.class));
        }
    }

    @Test
    void esb를_설정하면_구현체가_없어_기동에_실패한다() {
        Exception e = assertThrows(Exception.class,
            () -> context(Map.of("public-data.source.mode.default", "esb")).close());
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertInstanceOf(IllegalStateException.class, root);
        assertEquals(true, root.getMessage().contains("EsbExternalApiPort"));
    }
}
