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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 로그 컬렉터(Log Collector) 배치/단계 생명주기 오케스트레이션. 실행 설계 확정본은
 * private-doc/log-collector-api-spec.md §8 참고 - 이 클래스는 그 설계를 코드로 옮긴 것.
 *
 * <p><b>배치 경계(2026-09-29 변경)</b>: 수집 실행 1회 = 배치(execId) 1개이고, 그 수집의 정제·적재가
 * 같은 execId에 CLEANSE·STORE 스텝을 이어붙인다(법령은 적재 단계 없이 정제에서 닫음). 예전(§8)엔
 * 정제가 여러 수집 결과를 한꺼번에 처리해서 단계마다 별도 배치를 썼는데, 정제·적재를 수집 실행
 * 단위로 바꾸면서 연결했다 - 흐름 제어는 {@code PublicDataPipelineRunner}. 이어받기에 실패하면
 * 기존처럼 그 단계만의 새 배치({@link #startCleanseBatch}/{@link #startLoadBatch})로 남긴다.</p>
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
    private static final String STEP_COLLECT = "COLLECT";
    private static final String STEP_CLEANSE = "CLEANSE";
    // C05 공통코드 stepTypeCd(COLLECT/CLEANSE/ANALYZE/DEIDENT/STORE/SEND) 중 적재는 STORE에 대응.
    private static final String STEP_STORE = "STORE";

    private static final int STALE_LOOKBACK_DAYS = 7;
    private static final int STALE_PAGE_SIZE = 200;
    private static final int STALE_MAX_PAGES = 10;

    /** operationKey -> jobNm에 쓸 한글 라벨 (private-doc/log-collector-api-spec.md §8). */
    private static final Map<String, String> OPERATION_LABEL = Map.of(
        "kma-village-forecast-ultra-srt-ncst", "초단기실황조회(전 지역)",
        "kma-village-forecast-ultra-srt-fcst", "초단기예보조회(전 지역)",
        "kma-village-forecast-vilage-fcst", "단기예보조회(전 지역)",
        "kma-weather-warning-list", "기상특보목록조회",
        "moleg-criminal-law", "형사법령 본문조회(전체)",
        "safetydata-disaster-msg-list", "긴급재난문자 목록조회",
        "kma-living-uv-idx", "자외선지수조회(전 시도)",
        "kma-living-air-diffusion-idx", "대기정체지수조회(전 시도)"
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
        return start(jobNm, DataTypeClassifier.dataTypeCd(operationKey), executionType, triggerBy, STEP_COLLECT);
    }

    /**
     * Cleanse 배치 시작 (스케줄 1틱 = 배치 1개, Collect와 연결 안 함).
     *
     * @param dataTypeCd {@link DataTypeClassifier}의 EXTERNAL_PUBLIC/EXTERNAL_LAW 중 하나 -
     *                   Cleanse는 오퍼레이션 구분 없이 raw_staging 전체를 훑는 구조라, 호출부
     *                   ({@code PublicDataCleanseScheduler})가 카테고리별로 이 메서드를 두 번
     *                   불러서 배치를 나눈다(2026-08-27).
     */
    public BatchHandle startCleanseBatch(String dataTypeCd, ExecutionType executionType, String triggerBy) {
        String jobNm = "외부연계 정제" + categoryLabel(dataTypeCd);
        return start(jobNm, dataTypeCd, executionType, triggerBy, STEP_CLEANSE);
    }

    /**
     * Load(admin-db 적재) 배치 시작 (스케줄 1틱 = 배치 1개, Collect/Cleanse와 연결 안 함 - 동일 원칙).
     *
     * @param dataTypeCd {@link #startCleanseBatch} 참고 - Load도 동일한 이유로 카테고리별 분리.
     */
    public BatchHandle startLoadBatch(String dataTypeCd, ExecutionType executionType, String triggerBy) {
        String jobNm = "외부연계 적재" + categoryLabel(dataTypeCd);
        return start(jobNm, dataTypeCd, executionType, triggerBy, STEP_STORE);
    }

    private String categoryLabel(String dataTypeCd) {
        return DataTypeClassifier.EXTERNAL_LAW.equals(dataTypeCd) ? " (법령)" : " (공공데이터)";
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

    /**
     * 이미 열려있는 배치(execId)에 CLEANSE 스텝만 추가한다. collect가
     * {@link #finishCollectStepKeepBatchOpen}으로 배치를 안 닫고 남겨뒀을 때 정제가 같은 execId에
     * 이어붙이는 데 쓴다({@code PublicDataPipelineRunner} 참고). 보내는 stepSeq는 참고값일 뿐이고
     * 로그 컬렉터 서버가 dataType별 체인 위치로 순번을 다시 매긴다.
     *
     * @return 성공하면 같은 execId를 담은 새 {@link BatchHandle}. step 생성에 실패하면(플랫폼이
     *         거부했거나 네트워크 오류) 호출부가 새 배치로 안전하게 degrade할 수 있도록 빈 값.
     */
    public Optional<BatchHandle> continueBatchWithCleanseStep(String execId) {
        return continueBatchWithStep(execId, 2, STEP_CLEANSE);
    }

    /**
     * 이미 열려있는 배치(execId)에 STORE(적재) 스텝만 추가한다 - PUBLIC 10종 전용(2026-09-29).
     * cleanse가 {@link #finishCleanseStepKeepBatchOpen}으로 배치를 안 닫고 남겨뒀을 때, 뒤이은
     * load가 같은 execId 아래 stepSeq=3으로 이어붙이기 위해 쓴다. LAW는 cleanse에서 체인이
     * 끝나므로(로더 없음) 이 메서드를 쓰지 않는다.
     */
    public Optional<BatchHandle> continueBatchWithLoadStep(String execId) {
        return continueBatchWithStep(execId, 3, STEP_STORE);
    }

    private Optional<BatchHandle> continueBatchWithStep(String execId, int stepSeq, String stepTypeCd) {
        if (!client.isEnabled()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        JSONObject stepBody = new JSONObject()
            .put("stepSeq", stepSeq)
            .put("stepTypeCd", stepTypeCd)
            .put("startDtm", DTM.format(now));
        Optional<String> stepLogId = client.createStep(execId, stepBody);
        if (stepLogId.isEmpty()) {
            logger.warn("[LOG-COLLECTOR] 기존 배치(execId={})에 {} step 추가 실패 - 새 배치로 대체됨", execId, stepTypeCd);
            return Optional.empty();
        }
        return Optional.of(new BatchHandle(execId, stepLogId.get(), now, true));
    }

    /**
     * Collect 스텝만 종료하고 배치(execId) 자체는 RUNNING 상태로 열어둔다 - LAW 전용.
     * {@link #continueBatchWithCleanseStep}으로 이어붙인 cleanse가 {@link #finishCleanseBatch}를
     * 호출할 때 비로소 배치가 최종 종료된다.
     */
    public void finishCollectStepKeepBatchOpen(BatchHandle handle, List<CollectResult> results) {
        if (!handle.active()) {
            return;
        }
        if (!results.isEmpty()) {
            client.postExternalCollects(handle.execId(), toExternalCollects(results));
        }
        int successCount = (int) results.stream().filter(r -> r.status() == AttemptStatus.SUCCESS).count();
        finish(handle, results.size(), successCount, results.size() - successCount, false);
    }

    /**
     * Cleanse 스텝만 종료하고 배치(execId) 자체는 RUNNING 상태로 열어둔다 - PUBLIC 전용
     * (2026-09-29). 뒤이은 load가 {@link #continueBatchWithLoadStep}으로 이어붙이고
     * {@link #finishLoadBatch}를 호출할 때 비로소 배치가 최종 종료된다. LAW는 cleanse에서
     * 체인이 끝나므로 기존 {@link #finishCleanseBatch}(배치를 닫음)를 그대로 쓴다.
     */
    public void finishCleanseStepKeepBatchOpen(BatchHandle handle, CleanseResult result) {
        if (!handle.active()) {
            return;
        }
        finish(handle, result.totalProcessed(), result.successCount(), result.failCount(), false);
    }

    /**
     * Load(STORE) 스텝만 종료하고 배치는 열어둔다 - 적재 실패 행의 재시도가 끝날 때까지 배치를
     * 닫지 않기 위해 쓴다. 같은 execId로 다시 {@link #continueBatchWithLoadStep}을 부르면 서버가
     * 기존 STORE 행을 돌려주고, 이 메서드로 다시 마감하면 건수가 합산되고 상태가 병합된다
     * (로그 컬렉터 서버의 재진입 단계 규칙).
     */
    public void finishLoadStepKeepBatchOpen(BatchHandle handle, LoadResult result) {
        if (!handle.active()) {
            return;
        }
        finish(handle, result.totalProcessed(), result.successCount(), result.failCount(), false);
    }

    /**
     * 열어둔 배치를 최종 상태로 닫는다 - 단계별 결과를 합친 상태를 호출부가 정해서 넘긴다
     * ({@code PublicDataPipelineRunner}가 수집·정제·적재 중 가장 나쁜 결과로 계산). 소요 시간은
     * 수집 시작부터 잰다.
     */
    public void closeBatch(String execId, LocalDateTime batchStartedAt, LogCollectorStatus status,
            int targetCnt, int successCnt, int failCnt) {
        if (!client.isEnabled() || execId == null) {
            return;
        }
        JSONObject body = new JSONObject()
            .put("execStsCd", status.name())
            .put("endDtm", DTM.format(LocalDateTime.now()))
            .put("elapsedSec", elapsedSeconds(batchStartedAt))
            .put("targetCnt", targetCnt)
            .put("successCnt", successCnt)
            .put("failCnt", failCnt);
        client.finishBatch(execId, body);
    }

    /**
     * 우리(EXTERNAL_API)가 연 배치 중 {@code threshold}보다 오래 RUNNING인 것을 FAIL로 닫는다 -
     * 파드 재시작(배포 포함) 등으로 이어받을 주체가 사라진 배치를 정리하는 안전망.
     *
     * @return 닫은 배치 수
     */
    public int closeStaleBatches(Duration threshold) {
        if (!client.isEnabled()) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(threshold);
        String fromDate = cutoff.minusDays(STALE_LOOKBACK_DAYS).format(DateTimeFormatter.BASIC_ISO_DATE);

        List<JSONObject> stale = new ArrayList<>();
        for (int page = 0; page < STALE_MAX_PAGES; page++) {
            String query = "stsCd=RUNNING&dataTypeCd=" + DataTypeClassifier.EXTERNAL_PUBLIC
                + "&dataTypeCd=" + DataTypeClassifier.EXTERNAL_LAW
                + "&fromDate=" + fromDate + "&size=" + STALE_PAGE_SIZE + "&page=" + page;
            Optional<JSONObject> result = client.listBatches(query);
            JSONArray content = result.map(r -> r.optJSONArray("content")).orElse(null);
            if (content == null || content.isEmpty()) {
                break;
            }
            for (int i = 0; i < content.length(); i++) {
                JSONObject row = content.getJSONObject(i);
                if (JOB_ID.equals(row.optString("jobId")) && startedBefore(row, cutoff)) {
                    stale.add(row);
                }
            }
            if (page + 1 >= result.get().optInt("totalPages", 0)) {
                break;
            }
        }

        for (JSONObject row : stale) {
            String execId = row.getString("execId");
            LocalDateTime startDtm = LocalDateTime.parse(row.getString("startDtm"));
            JSONObject body = new JSONObject()
                .put("execStsCd", LogCollectorStatus.FAIL.name())
                .put("endDtm", DTM.format(LocalDateTime.now()))
                .put("elapsedSec", elapsedSeconds(startDtm))
                .put("errTypeCd", "SYSTEM")
                .put("errMsg", "외부연계 배치가 " + threshold.toHours() + "시간 넘게 종료되지 않아 자동 종료"
                    + " - 파드 재시작 등으로 이어받을 처리가 사라진 것으로 추정");
            client.finishBatch(execId, body);
            logger.warn("[LOG-COLLECTOR] 오래 열린 배치 자동 종료 execId={} startDtm={}", execId, startDtm);
        }
        return stale.size();
    }

    private static boolean startedBefore(JSONObject row, LocalDateTime cutoff) {
        try {
            return LocalDateTime.parse(row.getString("startDtm")).isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    private BatchHandle start(String jobNm, String dataTypeCd, ExecutionType executionType, String triggerBy, String stepTypeCd) {
        if (!client.isEnabled()) {
            return BatchHandle.inactive();
        }
        LocalDateTime now = LocalDateTime.now();
        String startDtm = DTM.format(now);

        JSONObject batchBody = new JSONObject()
            .put("jobId", JOB_ID)
            .put("jobNm", jobNm)
            .put("dataTypeCd", dataTypeCd)
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
        finish(handle, targetCnt, successCnt, failCnt, true);
    }

    /**
     * @param closeBatch false면 step만 종료하고 배치(execId)는 RUNNING 상태로 남겨둔다 - LAW의
     *                    collect→cleanse 배치 연결({@link #finishCollectStepKeepBatchOpen})에서만
     *                    쓴다. 기존 3개 finishXxxBatch는 전부 true로 호출되어 동작이 그대로다.
     */
    private void finish(BatchHandle handle, int targetCnt, int successCnt, int failCnt, boolean closeBatch) {
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

        if (!closeBatch) {
            return;
        }

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
