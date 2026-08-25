package egovframework.external.health;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerHeartbeatTest {

    @Test
    void 생성_직후에도_lastBeat이_null이_아니다() {
        SchedulerHeartbeat heartbeat = new SchedulerHeartbeat();

        assertThat(heartbeat.lastBeat()).isNotNull();
    }

    @Test
    void beat_호출하면_lastBeat이_갱신된다() throws InterruptedException {
        SchedulerHeartbeat heartbeat = new SchedulerHeartbeat();
        Instant before = heartbeat.lastBeat();

        Thread.sleep(5);
        heartbeat.beat();

        assertThat(heartbeat.lastBeat()).isAfter(before);
    }
}
