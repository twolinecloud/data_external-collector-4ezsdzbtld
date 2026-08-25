package egovframework.external.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 공공데이터 수집 스케줄러(@Scheduled) 활성화 + 스레드풀 구성.
 *
 * <p><b>스레드풀을 10개로 명시한 이유(2026-08-26)</b>: {@code @EnableScheduling}만 붙이면
 * 스프링 기본값인 스레드 1개짜리 풀을 쓰는데, 그 하나뿐인 스레드가 어떤 이유로든(타임아웃
 * 없는 블로킹 호출, DNS 조회 행 등) 멈춰버리면 이 앱의 <b>모든</b> {@code @Scheduled} 메서드가
 * 같이 멈춘다 - 실측(2026-08-25 23:20경, 에러 로그 하나 없이 스케줄러 전체가 9시간 조용히
 * 정지, {@code /actuator/health}는 계속 UP이라 쿠버네티스 헬스체크로도 못 잡음).
 * 풀을 여러 개로 늘려두면 특정 오퍼레이션 하나가 블로킹돼도 나머지(다른 컬렉터/Cleanse/
 * Load/Purge/FacilitySync)는 계속 돈다 - 장애 범위를 줄이는 목적. {@link SchedulerHeartbeat}의
 * 하트비트도 이 풀을 공유해서 돌기 때문에, 풀이 완전히 고갈됐을 때만 진짜로 liveness가
 * DOWN되게 하려는 의도(부분 블로킹은 하트비트가 버텨줌).</p>
 */
@Configuration
@EnableScheduling
public class PublicDataSchedulingConfig implements SchedulingConfigurer {

    private static final int POOL_SIZE = 10;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.initialize();
        return scheduler;
    }
}
