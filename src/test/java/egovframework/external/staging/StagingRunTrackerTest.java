package egovframework.external.staging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StagingRunTrackerTest {

    private final StagingRunTracker tracker = new StagingRunTracker();

    @Test
    void 이미_잡힌_run은_다시_잡을_수_없고_풀면_다시_잡힌다() {
        assertThat(tracker.tryAcquire("run-A")).isTrue();
        assertThat(tracker.tryAcquire("run-A")).isFalse();
        assertThat(tracker.tryAcquire("run-B")).isTrue();

        tracker.release("run-A");

        assertThat(tracker.tryAcquire("run-A")).isTrue();
    }
}
