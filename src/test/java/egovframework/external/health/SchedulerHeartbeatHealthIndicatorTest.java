package egovframework.external.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerHeartbeatHealthIndicatorTest {

    @Test
    void 최근에_하트비트가_있었으면_UP이다() {
        SchedulerHeartbeat heartbeat = new SchedulerHeartbeat();
        SchedulerHeartbeatHealthIndicator indicator = new SchedulerHeartbeatHealthIndicator(heartbeat);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void 임계값_3분을_넘게_하트비트가_없으면_DOWN이다() throws Exception {
        SchedulerHeartbeat heartbeat = new SchedulerHeartbeat();
        setLastBeat(heartbeat, Instant.now().minus(4, ChronoUnit.MINUTES));
        SchedulerHeartbeatHealthIndicator indicator = new SchedulerHeartbeatHealthIndicator(heartbeat);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKeys("lastBeat", "sinceLastBeatSeconds", "staleThresholdSeconds");
    }

    @Test
    void 임계값_3분_이내면_경계값에서도_UP이다() throws Exception {
        SchedulerHeartbeat heartbeat = new SchedulerHeartbeat();
        setLastBeat(heartbeat, Instant.now().minus(2, ChronoUnit.MINUTES));
        SchedulerHeartbeatHealthIndicator indicator = new SchedulerHeartbeatHealthIndicator(heartbeat);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @SuppressWarnings("unchecked")
    private void setLastBeat(SchedulerHeartbeat heartbeat, Instant value) throws Exception {
        Field field = SchedulerHeartbeat.class.getDeclaredField("lastBeat");
        field.setAccessible(true);
        ((AtomicReference<Instant>) field.get(heartbeat)).set(value);
    }
}
