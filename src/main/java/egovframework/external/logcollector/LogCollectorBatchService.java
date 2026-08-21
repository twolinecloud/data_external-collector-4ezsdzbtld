package egovframework.external.logcollector;

import egovframework.external.model.AttemptStatus;
import egovframework.external.model.CleanseResult;
import egovframework.external.model.CollectResult;
import egovframework.external.model.ExecutionType;
import egovframework.external.model.LoadResult;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 로그 컬렉터(Log Collector) 배치/단계 생명주기 오케스트레이션. 실행 설계 확정본은
 * private-doc/log-collector-api-spec.md §8 참고 - 이 클래스는 그 설계를 코드로 옮긴 것.
 *
 * <p><b>배치 경계</b>: Collect 스케줄러(또는 수동 트리거)의 오퍼레이션 1틱 = 배치 1개.
 * Cleanse 스케줄러(또는 수동 트리거) 1틱 = 별도의 배치 1개. 우리 구조상 Cleanse 한 번이
 * 여러 Collect 배치의 결과를 오퍼레이션 구분 없이 한꺼번에 처리하기 때문에 서로 연결하지
 * 않는다(§8 근거 참고).</p>
 *
 * <p><b>건수 집계 방식이 T6(항목)와 T1/T2(배치/단계)에서 다르다</b> - 사용자 확정(2026-08-20)은
 * "T6의 targetCnt/successCnt = 레코드 건수"였다. 이걸 그대로 배치/단계 레벨에도 적용하면
 * 서로 다른 컬렉터의 레코드 수(예: 재난문자 27건 vs 법령 1건)가 뒤섞여 "배치 전체 성공/실패
 * 몇 건" 의미가 흐려지므로, 배치/단계 레벨은 <b>컬렉터 실행 시도 건수</b>(성공 N개/실패 M개)로
 * 집계한다 - 사용자가 명시적으로 확인한 부분은 아니라 필요하면 조정 가능.</p>
 */
@Component
@RequiredArgsConstructor
public class LogCollectorBatchService {

    private static final Logger logger = LogManager.getLogger(LogCollectorBatchService.class);
    private static final DateTimeFormatter DTM = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final String JOB_ID = "EXTERNAL_API";
    private static final String DATA_TYPE_CD = "EXTERNAL";
    private static final String STEP_COLLECT = "COLLECT";
    private static final String STEP_CLEANSE = "CLEANSE";
    // C05 공통코드 stepTypeCd(COLLECT/CLEANSE/ANALYZE/DEIDENT/STORE/SEND) 중 적재는 STORE에 대응.
    private static final String STEP_STORE = "STORE";

    /** operationKey -> jobNm에 쓸 한글 라벨 (private-doc/log-collector-api-spec.md §8). */
    private static final Map<String, String> OPERATION_LABEL = Map.of(
        "kma-village-forecast-ultra-srt-ncst", "초단기실황조회(전 지역)",
        "kma-village-forecast-ultra-srt-fcst", "초단기예보조회(전 지역)",
        "kma-village-forecast-vilage-fcst", "단기예보조회(전 지역)",
        "kma-weather-warning-list", "기상특보목록조회",
        "moleg-criminal-law", "형사법령 본문조회(전체)",
        "safetydata-disaster-msg-list", "긴급재난문자 목록조회"
    );

    private final LogCollectorClient client;

    /**
     * Collect 배치 시작 (operationKey 1틱 = 배치 1개).
     *
     * @param triggerBy 스케줄러는 {@code "scheduler:" + operationKey}, 컨트롤러 수동실행은
     *                  {@code "manual-api:" + collector.key()}(개별 컬렉터까지 식별 가능하게) -
     *                  호출자가 직접 조립해서 넘긴다(§8 원 설계 기준으로 컨트롤러 쪽이 더
     *                  구체적인 값을 줄 수 있어 이렇게 뺐음)
     */
    public BatchHandle startCollectBatch(String operationKey, ExecutionType executionType, String triggerBy) {
        String jobNm = "외부연계 수집 - " + OPERATION_LABEL.getOrDefault(operationKey, operationKey);
        return start(jobNm, executionType, triggerBy, STEP_COLLECT);
    }

    /** Cleanse 배치 시작 (스케줄 1틱 = 배치 1개, Collect와 연결 안 함). */
    public BatchHandle startCleanseBatch(ExecutionType executionType, String triggerBy) {
        return start("외부연계 정제", executionType, triggerBy, STEP_CLEANSE);
    }

    /** Load(admin-db 적재) 배치 시작 (스케줄 1틱 = 배치 1개, Collect/Cleanse와 연결 안 함 - 동일 원칙). */
    public BatchHandle startLoadBatch(ExecutionType executionType, String triggerBy) {
        return start("외부연계 적재", executionType, triggerBy, STEP_STORE);
    }

    /** Collect 배치 종료 - T6(컬렉터별 실적) bulk 적재 후 단계/배치 종료. */
    public void finishCollectBatch(BatchHandle handle, List<CollectResult> results) {
        if (!handle.active()) {
            return;
        }
        if (!results.isEmpty()) {
            client.postExternalCollects(handle.execId(), toExternalCollects(results));
        }
        int successCount = (int) results.stream().filter(r -> r.status() == AttemptStatus.SUCCESS).count();
        finish(handle, results.size(), successCount, results.size() - successCount);
    }

    /** Cleanse 배치 종료. */
    public void finishCleanseBatch(BatchHandle handle, CleanseResult result) {
        if (!handle.active()) {
            return;
        }
        finish(handle, result.totalProcessed(), result.successCount(), result.failCount());
    }

    /** Load 배치 종료. */
    public void finishLoadBatch(BatchHandle handle, LoadResult result) {
        if (!handle.active()) {
            return;
        }
        finish(handle, result.totalProcessed(), result.successCount(), result.failCount());
    }

    private BatchHandle start(String jobNm, ExecutionType executionType, String triggerBy, String stepTypeCd) {
        if (!client.isEnabled()) {
            return BatchHandle.inactive();
        }
        LocalDateTime now = LocalDateTime.now();
        String startDtm = DTM.format(now);

        JSONObject batchBody = new JSONObject()
            .put("jobId", JOB_ID)
            .put("jobNm", jobNm)
            .put("dataTypeCd", DATA_TYPE_CD)
            .put("execTypeCd", execTypeCd(executionType))
            .put("startDtm", startDtm)
            .put("triggerBy", triggerBy);

        Optional<String> execId = client.createBatch(batchBody);
        if (execId.isEmpty()) {
            return BatchHandle.inactive();
        }

        JSONObject stepBody = new JSONObject()
            .put("stepSeq", 1)
            .put("stepTypeCd", stepTypeCd)
            .put("startDtm", startDtm);
        Optional<String> stepLogId = client.createStep(execId.get(), stepBody);
        if (stepLogId.isEmpty()) {
            // 배치는 만들어졌는데 스텝 생성에 실패한 경우 - 그냥 두면 플랫폼에 RUNNING 상태
            // 배치가 영원히 남으므로, 즉시 FAIL로 마감해서 dangling 배치를 남기지 않는다.
            logger.warn("[LOG-COLLECTOR] step 생성 실패 - 배치(execId={})를 즉시 FAIL로 종료", execId.get());
            abortBatch(execId.get(), now);
            return BatchHandle.inactive();
        }

        return new BatchHandle(execId.get(), stepLogId.get(), now, true);
    }

    private void abortBatch(String execId, LocalDateTime startedAt) {
        String endDtm = DTM.format(LocalDateTime.now());
        JSONObject body = new JSONObject()
            .put("execStsCd", LogCollectorStatus.FAIL.name())
            .put("endDtm", endDtm)
            .put("elapsedSec", elapsedSeconds(startedAt))
            .put("targetCnt", 0)
            .put("successCnt", 0)
            .put("failCnt", 0)
            .put("errTypeCd", "SYSTEM")
            .put("errMsg", "step 생성 실패로 배치 중단");
        client.finishBatch(execId, body);
    }

    private void finish(BatchHandle handle, int targetCnt, int successCnt, int failCnt) {
        String status = LogCollectorStatus.aggregate(successCnt, failCnt).name();
        String endDtm = DTM.format(LocalDateTime.now());
        long elapsedSec = elapsedSeconds(handle.startedAt());

        JSONObject stepFinish = new JSONObject()
            .put("stepStsCd", status)
            .put("endDtm", endDtm)
            .put("elapsedSec", elapsedSec)
            .put("inCnt", targetCnt)
            .put("outCnt", successCnt)
            .put("errCnt", failCnt);
        client.finishStep(handle.stepLogId(), stepFinish);

        JSONObject batchFinish = new JSONObject()
            .put("execStsCd", status)
            .put("endDtm", endDtm)
            .put("elapsedSec", elapsedSec)
            .put("targetCnt", targetCnt)
            .put("successCnt", successCnt)
            .put("failCnt", failCnt);
        client.finishBatch(handle.execId(), batchFinish);
    }

    private JSONArray toExternalCollects(List<CollectResult> results) {
        JSONArray items = new JSONArray();
        for (CollectResult r : results) {
            boolean success = r.status() == AttemptStatus.SUCCESS;
            String stsCd = success ? LogCollectorStatus.SUCCESS.name() : LogCollectorStatus.FAIL.name();
            JSONObject item = new JSONObject()
                .put("srcNm", r.sourceName())
                .put("apiNm", r.apiName())
                // 레코드 건수(사용자 확정) - 실패 시엔 가져온 레코드가 0이라 "시도 1건"으로 대체
                .put("targetCnt", success ? r.recordCount() : 1)
                .put("successCnt", success ? r.recordCount() : 0)
                .put("failCnt", success ? 0 : 1)
                .put("collectStsCd", stsCd)
                // 우리 구조엔 별도 "전송" 단계가 없어 수집상태와 동일값(임시, PL 확인 전 - §7)
                .put("sendStsCd", stsCd);
            if (r.failureLog() != null) {
                item.put("errStack", r.failureLog());
            }
            items.put(item);
        }
        return items;
    }

    /** 우리 ExecutionType.SCHEDULE -> 플랫폼 "SCHEDULED" (철자가 다름, §8 주의사항). */
    private String execTypeCd(ExecutionType executionType) {
        return executionType == ExecutionType.SCHEDULE ? "SCHEDULED" : "MANUAL";
    }

    private long elapsedSeconds(LocalDateTime startedAt) {
        return Math.max(0, Duration.between(startedAt, LocalDateTime.now()).getSeconds());
    }
}
