package egovframework.external.logcollector;

import java.time.LocalDateTime;

/**
 * {@link LogCollectorBatchService}가 배치 시작 시 돌려주는 핸들. {@code active=false}면
 * (로그 컬렉터가 꺼져있거나, 배치/단계 생성 자체가 실패했을 때) 이후 finish 계열 메서드가
 * 전부 조용히 no-op이 된다 - 호출자가 매번 null/실패를 체크할 필요 없게 하기 위함.
 */
public record BatchHandle(String execId, String stepLogId, LocalDateTime startedAt, boolean active) {

    public static BatchHandle inactive() {
        return new BatchHandle(null, null, LocalDateTime.now(), false);
    }
}
