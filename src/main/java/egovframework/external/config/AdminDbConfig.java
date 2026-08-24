package egovframework.external.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * admin-db(kcais, PostgreSQL) 연결 설정. 다음 중 하나라도 켜져있을 때만 빈이 만들어진다
 * (2026-08-21 OR 조건으로 확장, 2026-08-24 purge/facility-sync 추가) - 꺼져있으면
 * DataSource/MyBatis 관련 빈이 전혀 생성되지 않아 DB 연결정보 없이도 앱이 정상 기동한다
 * (로그 컬렉터와 동일한 원칙):
 * <ul>
 *   <li>{@code public-data.load.enabled=true} - 수집/정제 결과를 admin-db 최종 테이블에 적재</li>
 *   <li>{@code public-data.moleg.law-target-source=db} - 법령 수집 대상 목록을 admin-db에서 읽음
 *       (Load와는 별개 관심사 - 목록 "읽기"만 하고 "쓰기"는 없음, {@code MolegLawTargetSource} 참고)</li>
 *   <li>{@code public-data.purge.enabled=true} - admin-db 최종 테이블의 보존기간 초과 데이터를
 *       주기적으로 삭제({@code PublicDataPurgeService} 참고)</li>
 *   <li>{@code public-data.facility-sync.enabled=true} - 교정기관 목록을 {@code tb_dim_instt}
 *       (대시보드 관리 기관 마스터)와 대조해서 변경분을 검토 큐에 쌓음
 *       ({@code FacilitySyncService} 참고)</li>
 * </ul>
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
@ConditionalOnExpression(
    "${public-data.load.enabled:false} or ${public-data.purge.enabled:false}"
        + " or ${public-data.facility-sync.enabled:false}"
        + " or '${public-data.moleg.law-target-source:csv}' == 'db'")
// annotationClass 지정 필수 - 안 걸면 MyBatis MapperScan이 스캔 범위 내 "모든 인터페이스"를
// 매퍼로 오인해서 프록시 빈을 만들어버림(실측, 2026-08-21). MolegLawTargetSource 같은 순수
// 도메인 전략 인터페이스가 "molegLawTargetSource"라는 이름의 가짜 매퍼 빈으로 등록되면서
// 진짜 구현체(CsvMolegLawTargetSource)와 타입이 충돌해 부팅이 실패했었음(2개 빈 발견 에러) -
// @Mapper 붙은 것만 스캔하도록 제한해서 해결.
@MapperScan(basePackages = "egovframework.external.publicdata", annotationClass = Mapper.class)
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
