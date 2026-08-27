package egovframework.external;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import java.net.URI;
import java.util.TimeZone;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

// DataSource/MyBatis 자동설정을 통째로 제외 - AdminDbConfig가 public-data.load.enabled=true일
// 때만 수동으로 DataSource/SqlSessionFactory 빈을 만든다. 자동설정에 맡기면 postgresql
// 드라이버가 classpath에 있다는 이유만으로 datasource 설정을 시도해서, DB 연결 정보가 없는
// 개발자(Load 기능 안 쓰는 경우)의 로컬 기동이 깨질 수 있음 - 2026-08-21.
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    MybatisAutoConfiguration.class
})
public class Main {

    @Value("${service-path}")
    private String servicePath;

    @Bean
    public CorsFilter corsFilter() {
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        final CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedMethod("HEAD");
        config.addAllowedMethod("GET");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("PATCH");
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    // 2026-08-27: 스켈레톤 컨벤션이 UTC -> Asia/Seoul(KST)로 변경됨 - 그동안 KMA 발표시각/
    // 로그 컬렉터 타임스탬프 등 곳곳에서 UTC 기본값을 상쇄하려고 명시적으로 붙였던
    // ZoneId.of("Asia/Seoul") 보정 코드는 전부 제거함(각 클래스 참고). DB 세션 타임존도
    // application.yml의 spring.datasource.hikari.connection-init-sql로 맞춤.
    @PostConstruct
    void started() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }


    private static final String PROPERTIES =
    "spring.config.location="
    + "classpath:/application.yml";

    public static void main(String[] args) {
        new SpringApplicationBuilder(Main.class)
        .properties(PROPERTIES)
        .run(args);
    }

    @Bean
    RouterFunction<ServerResponse> routerFunction() {
        return route(GET("/swagger"), req ->
        ServerResponse.temporaryRedirect(URI.create(servicePath + "/swagger-ui.html")).build()
        );
    }

    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
