package egovframework.external.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * admin-db(kcais, PostgreSQL) 연결 설정. {@code public-data.load.enabled=true}일 때만
 * 빈이 만들어진다 - 꺼져있으면(기본값) DataSource/MyBatis 관련 빈이 전혀 생성되지 않아
 * DB 연결정보 없이도 앱이 정상 기동한다(로그 컬렉터와 동일한 원칙).
 *
 * <p>{@link egovframework.external.Main}에서 {@code DataSourceAutoConfiguration}/
 * {@code MybatisAutoConfiguration}을 통째로 제외해뒀으므로, 여기서 수동으로 등록하는
 * 빈들이 유일한 DataSource/SqlSessionFactory 출처다.</p>
 *
 * <p>로컬 개발 시 admin-db는 K8s 클러스터 내부에만 열려있어 직접 접속이 안 되고
 * {@code kubectl port-forward svc/admin-db-fy9tjq4tsk -n service-core 15432:5432}로
 * 우회해야 한다 - private-doc/cleanse-db-schema-spec.md §6 참고.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "public-data.load", name = "enabled", havingValue = "true")
@MapperScan("egovframework.external.publicdata.loader.mapper")
public class AdminDbConfig {

    // DataSourceBuilder로 HikariDataSource에 spring.datasource.*를 바로 바인딩하면
    // Hikari 고유 프로퍼티명(jdbcUrl)과 안 맞아 "jdbcUrl is required with driverClassName"
    // 오류가 남(실측, 2026-08-21) - DataSourceAutoConfiguration이 평소 해주던 url->jdbcUrl
    // 이름 매핑을 여기서 직접 안 하고 DataSourceProperties를 경유해서 처리한다(표준 매핑 로직 재사용).
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    // Hikari 기본값(maximumPoolSize=10, minimumIdle=maximumPoolSize와 동일)을 그대로 두면
    // 앱을 켤 때마다 실제 필요와 무관하게 10개를 미리 열어버린다(2026-08-21 실측 - 로컬 개발 중
    // 반복 재기동으로 admin-db 커넥션 슬롯을 33개나 소진해서 다른 서비스까지 접속 불가하게 만든
    // 사고의 직접 원인). 우리 Load 로직은 raw_staging 행을 한 줄씩 순차 처리하는 구조라
    // 동시 커넥션이 여러 개 필요한 지점이 없음 - 최대 3개, 평소엔 1개만 열어두면 충분.
    // spring.datasource.hikari.* 로 오버라이드 가능(application.yml 참고).
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource adminDataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource adminDataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(adminDataSource);
        return factoryBean.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    public PlatformTransactionManager adminDbTransactionManager(DataSource adminDataSource) {
        return new DataSourceTransactionManager(adminDataSource);
    }
}
