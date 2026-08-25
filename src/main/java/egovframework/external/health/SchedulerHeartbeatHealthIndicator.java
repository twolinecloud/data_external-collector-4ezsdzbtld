package egovframework.external.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * {@link SchedulerHeartbeat}의 마지막 실행 시각이 임계값보다 오래됐으면 DOWN을 리턴 -
 * {@code management.endpoint.health.group.liveness.include}에 포함시켜서
 * 쿠버네티스 livenessProbe(/actuator/health/liveness)가 "스케줄러 스레드풀 행" 상황을
 * 직접 감지해 파드를 재시작하게 하는 목적(2026-08-26, 9시간 무감지 정지 사고 이후 추가).
 *
 * <p>임계값(3분)은 하트비트 주기(30초)의 6배 - 스케줄러 스레드가 일시적으로 바쁘거나
 * GC pause 등으로 몇 십 초 늦어지는 정상 범위에서는 false positive가 안 나게 넉넉히 잡음.</p>
 */
@Component
public class SchedulerHeartbeatHealthIndicator implements HealthIndicator {

    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(3);

    private final SchedulerHeartbeat heartbeat;

    public SchedulerHeartbeatHealthIndicator(SchedulerHeartbeat heartbeat) {
        this.heartbeat = heartbeat;
    }

    @Override
    public Health health() {
        Instant lastBeat = heartbeat.lastBeat();
        Duration sinceLastBeat = Duration.between(lastBeat, Instant.now());

        if (sinceLastBeat.compareTo(STALE_THRESHOLD) > 0) {
            return Health.down()
                .withDetail("lastBeat", lastBeat)
                .withDetail("sinceLastBeatSeconds", sinceLastBeat.getSeconds())
                .withDetail("staleThresholdSeconds", STALE_THRESHOLD.getSeconds())
                .build();
        }
        return Health.up()
            .withDetail("lastBeat", lastBeat)
            .withDetail("sinceLastBeatSeconds", sinceLastBeat.getSeconds())
            .build();
    }
}
