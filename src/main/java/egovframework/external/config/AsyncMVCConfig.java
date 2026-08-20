package egovframework.external.config;

import egovframework.external.interceptor.AuthInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@EnableAsync
public class AsyncMVCConfig implements WebMvcConfigurer {
    @Bean
    public AuthInterceptor interceptor() {
        return new AuthInterceptor();
    }

    @Bean
    public AsyncTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(1000);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("task-");
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(asyncTaskExecutor());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor())
        .addPathPatterns("/**")
        .excludePathPatterns(
            new String[] {
                "/static/**",
                "/swagger*/**",
                "/webjars/**",
                "/v3/api-docs*/**",
                "/configuration*/**",
                "/_test/**",
                "/error"
            }
        );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger*/**").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }


    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
        .requestFactory(() -> new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
        .setConnectTimeout(Duration.ofMillis(50000)) // connection-timeout
        .setReadTimeout(Duration.ofMillis(150000)) // read-timeout
        .additionalMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
        .build();
    }

    /**
     * 로그 컬렉터(Log Collector) 전용 RestTemplate - 위 공용 {@link #restTemplate}과 별도로 둠.
     * 공용 빈은 {@code SimpleClientHttpRequestFactory}(= JDK {@code HttpURLConnection}) 기반인데,
     * {@code HttpURLConnection}은 PATCH 메서드를 지원하지 않는다("Invalid HTTP method: PATCH"
     * 예외 발생, 실측 2026-08-20) - 로그 컬렉터 API는 배치/단계 종료에 PATCH를 쓰므로
     * {@link JdkClientHttpRequestFactory}(java.net.http.HttpClient 기반, PATCH 네이티브 지원)로
     * 별도 구성. 공용 빈을 쓰는 기존 컬렉터들(GET/POST만 사용)에는 영향 없도록 분리함.
     */
    @Bean
    public RestTemplate logCollectorRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        // JdkClientHttpRequestFactory엔 setConnectTimeout이 없어(RestTemplateBuilder가 리플렉션으로
        // 찾다가 실패) connect-timeout은 java.net.http.HttpClient.Builder 쪽에서 미리 잡아준다.
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(50000))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(150000));

        return restTemplateBuilder
        .requestFactory(() -> factory)
        .additionalMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
        .build();
    }
}
