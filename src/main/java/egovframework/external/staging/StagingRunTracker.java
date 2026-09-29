package egovframework.external.staging;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 수집 실행(collectRunId) 하나를 동시에 한 단계만 만지게 하는 인메모리 잠금.
 *
 * <p>스케줄러 스레드풀이 여러 개라 수집·정제·적재가 동시에 돈다. 59개소 수집이 끝나기 전에 정제가
 * 먼저 온 행만 처리하거나, 정제 도중 적재가 STORE를 붙이고 배치를 닫으면 한 수집이 여러 로그 컬렉터
 * 배치로 쪼개지고 배치 합계가 덮어써진다(로그 컬렉터 서버는 하류 단계 뒤의 CLEANSE 재진입을
 * 거절하고, finishBatch는 합계를 덮어쓴다). 수집이 잠금을 쥔 채 행을 넣고, 끝나야 풀기 때문에
 * 잠금 해제가 곧 "이 run은 다음 단계로 넘어가도 된다"는 완료 신호가 된다.</p>
 *
 * <p>raw_staging과 마찬가지로 단일 인스턴스 전제다({@link InMemoryRawStagingStore} 참고).</p>
 */
@Component
public class StagingRunTracker {

    private final Set<String> busy = ConcurrentHashMap.newKeySet();

    /** @return 잠금을 얻었으면 true, 다른 단계가 이미 쥐고 있으면 false(이번 틱은 건너뛸 것) */
    public boolean tryAcquire(String collectRunId) {
        return busy.add(collectRunId);
    }

    public void release(String collectRunId) {
        busy.remove(collectRunId);
    }
}
