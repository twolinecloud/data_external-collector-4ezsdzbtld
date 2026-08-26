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
 * <p><b>스레드풀을 10개로 늘린 이유(2026-08-26)</b>: 스레드 1개뿐이면 그 하나가 블로킹
 * 호출에 걸려 멈출 때(실측 2026-08-25 23:20경, 에러 로그 없이 9시간 정지) 앱의 <b>모든</b>
 * {@code @Scheduled}가 같이 멈춘다. 풀을 늘려두면 특정 오퍼레이션 하나가 블로킹돼도
 * 나머지(다른 컬렉터/Cleanse/Load/Purge/FacilitySync)는 계속 돈다.</p>
 *
 * <p><b>동시 호출 부작용과 대응</b>: 처음 10개로 늘렸을 때 로그 컬렉터 API 호출이 진짜
 * 동시에 여러 건 나가면서, 로그 컬렉터 쪽 {@code exec_id} 채번 로직이 동시성에 안전하지
 * 않아 {@code duplicate key} 충돌로 100% 실패하는 부작용이 있었다 - 그쪽 채번 구조를
 * 우리가 손댈 수 없어서, 스레드풀을 줄이는 대신 {@code LogCollectorClient}의 실제 HTTP
 * 호출부를 전용 단일 스레드로 직렬화하는 쪽으로 해결(그 클래스 주석 참고) - 그래서 이
 * 스레드풀은 10개로 유지. "스레드 하나가 영원히 블로킹되는 상황"은
 * {@link egovframework.external.service.PublicDataCollectionAttemptService}의 개별 수집
 * 실행 타임아웃(별도 워커 스레드 + {@code Future.get(timeout)})으로 막고, 그래도 못 막는
 * 미지의 블로킹은 {@link SchedulerHeartbeat} 기반 liveness가 최후 안전망으로 잡는다.</p>
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
