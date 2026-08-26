package egovframework.external.health;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 스케줄러(@Scheduled) 스레드풀이 살아있는지 확인하기 위한 하트비트.
 *
 * <p>하는 일은 마지막 실행 시각을 갱신하는 것뿐 - 일부러 다른 {@code @Scheduled} 메서드들과
 * <b>같은 스레드풀</b>({@link egovframework.external.config.PublicDataSchedulingConfig})에서
 * 돈다. 별도 전용 스레드로 빼면 정작 감지하려는 상황("그 풀이 뭔가에 물려서 막혔다")을
 * 못 잡기 때문 - 실측(2026-08-25) 스케줄러 전체가 에러 로그 하나 없이 9시간 멈췄던 사고
 * 이후 추가(2026-08-26). {@link SchedulerHeartbeatHealthIndicator}가 이 값을 읽어서
 * liveness에 반영한다.</p>
 */
@Component
public class SchedulerHeartbeat {

    private final AtomicReference<Instant> lastBeat = new AtomicReference<>(Instant.now());

    @Scheduled(fixedRate = 30_000)
    public void beat() {
        lastBeat.set(Instant.now());
    }

    public Instant lastBeat() {
        return lastBeat.get();
    }
}
