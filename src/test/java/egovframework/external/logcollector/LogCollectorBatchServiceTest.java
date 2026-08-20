package egovframework.external.logcollector;

import egovframework.external.model.AttemptStatus;
import egovframework.external.model.CleanseResult;
import egovframework.external.model.CollectResult;
import egovframework.external.model.ExecutionType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogCollectorBatchServiceTest {

    @Mock
    private LogCollectorClient client;

    private LogCollectorBatchService service() {
        return new LogCollectorBatchService(client);
    }

    @Test
    void 비활성화_상태면_배치_생성을_시도하지_않고_비활성_핸들을_반환한다() {
        when(client.isEnabled()).thenReturn(false);

        BatchHandle handle = service().startCollectBatch("kma-weather-warning-list", ExecutionType.SCHEDULE, "scheduler:kma-weather-warning-list");

        assertThat(handle.active()).isFalse();
        verify(client, never()).createBatch(any());
    }

    @Test
    void 정상_시작시_배치와_스텝을_생성하고_활성_핸들을_반환한다() {
        when(client.isEnabled()).thenReturn(true);
        when(client.createBatch(any())).thenReturn(Optional.of("20260820EXT001"));
        when(client.createStep(eq("20260820EXT001"), any())).thenReturn(Optional.of("20260820EXT00101"));

        BatchHandle handle = service().startCollectBatch("kma-weather-warning-list", ExecutionType.SCHEDULE, "scheduler:kma-weather-warning-list");

        assertThat(handle.active()).isTrue();
        assertThat(handle.execId()).isEqualTo("20260820EXT001");
        assertThat(handle.stepLogId()).isEqualTo("20260820EXT00101");
    }

    @Test
    void 배치_생성_요청바디에_확정된_필드값이_들어간다() {
        when(client.isEnabled()).thenReturn(true);
        when(client.createBatch(any())).thenReturn(Optional.of("exec1"));
        when(client.createStep(any(), any())).thenReturn(Optional.of("step1"));

        service().startCollectBatch("kma-weather-warning-list", ExecutionType.SCHEDULE, "scheduler:kma-weather-warning-list");

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).createBatch(captor.capture());
        JSONObject body = captor.getValue();
        assertThat(body.getString("jobId")).isEqualTo("EXTERNAL_API");
        assertThat(body.getString("dataTypeCd")).isEqualTo("EXTERNAL");
        assertThat(body.getString("execTypeCd")).isEqualTo("SCHEDULED"); // 우리 SCHEDULE -> 플랫폼 SCHEDULED
        assertThat(body.getString("jobNm")).isEqualTo("외부연계 수집 - 기상특보목록조회");
        assertThat(body.getString("triggerBy")).isEqualTo("scheduler:kma-weather-warning-list");
    }

    @Test
    void 수동실행이면_triggerBy와_execTypeCd가_다르게_채워진다() {
        when(client.isEnabled()).thenReturn(true);
        when(client.createBatch(any())).thenReturn(Optional.of("exec1"));
        when(client.createStep(any(), any())).thenReturn(Optional.of("step1"));

        service().startCollectBatch("moleg-criminal-law", ExecutionType.MANUAL, "manual-api:moleg-criminal-law--001692");

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).createBatch(captor.capture());
        assertThat(captor.getValue().getString("execTypeCd")).isEqualTo("MANUAL");
        // triggerBy는 이제 호출자가 조립해서 그대로 넘긴 값 - 컨트롤러는 개별 컬렉터 key까지 넣을 수 있음
        assertThat(captor.getValue().getString("triggerBy")).isEqualTo("manual-api:moleg-criminal-law--001692");
    }

    @Test
    void 등록안된_operationKey는_한글라벨_대신_키_그대로_쓴다() {
        when(client.isEnabled()).thenReturn(true);
        when(client.createBatch(any())).thenReturn(Optional.of("exec1"));
        when(client.createStep(any(), any())).thenReturn(Optional.of("step1"));

        service().startCollectBatch("some-new-operation", ExecutionType.SCHEDULE, "scheduler:some-new-operation");

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).createBatch(captor.capture());
        assertThat(captor.getValue().getString("jobNm")).isEqualTo("외부연계 수집 - some-new-operation");
    }

    @Test
    void 배치생성은_성공했는데_스텝생성이_실패하면_즉시_FAIL로_배치를_종료하고_비활성_핸들을_반환한다() {
        when(client.isEnabled()).thenReturn(true);
        when(client.createBatch(any())).thenReturn(Optional.of("exec1"));
        when(client.createStep(any(), any())).thenReturn(Optional.empty());

        BatchHandle handle = service().startCollectBatch("kma-weather-warning-list", ExecutionType.SCHEDULE, "scheduler:kma-weather-warning-list");

        assertThat(handle.active()).isFalse();
        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).finishBatch(eq("exec1"), captor.capture());
        assertThat(captor.getValue().getString("execStsCd")).isEqualTo("FAIL");
    }

    @Test
    void 비활성_핸들로_종료를_호출하면_아무_호출도_안한다() {
        BatchHandle inactive = BatchHandle.inactive();

        service().finishCollectBatch(inactive, List.of());
        service().finishCleanseBatch(inactive, new CleanseResult(0, 0, 0));

        verify(client, never()).postExternalCollects(any(), any());
        verify(client, never()).finishStep(any(), any());
        verify(client, never()).finishBatch(any(), any());
    }

    @Test
    void collect_종료시_레코드건수로_T6를_채우고_시도건수로_배치를_집계한다() {
        BatchHandle handle = new BatchHandle("exec1", "step1", java.time.LocalDateTime.now().minusSeconds(3), true);
        List<CollectResult> results = List.of(
            new CollectResult("k1", "src1", "api1", AttemptStatus.SUCCESS, 27, null),
            new CollectResult("k2", "src2", "api2", AttemptStatus.SUCCESS, 1, null),
            new CollectResult("k3", "src3", "api3", AttemptStatus.FAILED, 0, "타임아웃")
        );

        service().finishCollectBatch(handle, results);

        ArgumentCaptor<JSONArray> itemsCaptor = ArgumentCaptor.forClass(JSONArray.class);
        verify(client).postExternalCollects(eq("exec1"), itemsCaptor.capture());
        JSONArray items = itemsCaptor.getValue();
        assertThat(items.length()).isEqualTo(3);
        assertThat(items.getJSONObject(0).getInt("targetCnt")).isEqualTo(27); // 레코드 건수
        assertThat(items.getJSONObject(0).getString("collectStsCd")).isEqualTo("SUCCESS");
        assertThat(items.getJSONObject(2).getInt("targetCnt")).isEqualTo(1); // 실패는 시도 1건
        assertThat(items.getJSONObject(2).getString("collectStsCd")).isEqualTo("FAIL");
        assertThat(items.getJSONObject(2).getString("errStack")).isEqualTo("타임아웃");
        assertThat(items.getJSONObject(1).has("errStack")).isFalse(); // 성공 건엔 errStack 없음

        // 배치/단계 레벨은 "시도 건수"(3건 중 2성공 1실패) - 레코드 건수 합(28)이 아님
        ArgumentCaptor<JSONObject> stepCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).finishStep(eq("step1"), stepCaptor.capture());
        assertThat(stepCaptor.getValue().getInt("inCnt")).isEqualTo(3);
        assertThat(stepCaptor.getValue().getInt("outCnt")).isEqualTo(2);
        assertThat(stepCaptor.getValue().getInt("errCnt")).isEqualTo(1);
        assertThat(stepCaptor.getValue().getString("stepStsCd")).isEqualTo("PARTIAL");

        ArgumentCaptor<JSONObject> batchCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).finishBatch(eq("exec1"), batchCaptor.capture());
        assertThat(batchCaptor.getValue().getInt("targetCnt")).isEqualTo(3);
        assertThat(batchCaptor.getValue().getInt("successCnt")).isEqualTo(2);
        assertThat(batchCaptor.getValue().getInt("failCnt")).isEqualTo(1);
        assertThat(batchCaptor.getValue().getString("execStsCd")).isEqualTo("PARTIAL");
    }

    @Test
    void 전부_성공하면_SUCCESS_전부_실패하면_FAIL로_집계된다() {
        BatchHandle handle = new BatchHandle("exec1", "step1", java.time.LocalDateTime.now(), true);

        service().finishCollectBatch(handle, List.of(
            new CollectResult("k1", "s", "a", AttemptStatus.SUCCESS, 1, null)));
        ArgumentCaptor<JSONObject> c1 = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).finishBatch(eq("exec1"), c1.capture());
        assertThat(c1.getValue().getString("execStsCd")).isEqualTo("SUCCESS");
    }

    @Test
    void 빈_결과로_종료해도_외부수집_bulk_호출은_생략된다() {
        BatchHandle handle = new BatchHandle("exec1", "step1", java.time.LocalDateTime.now(), true);

        service().finishCollectBatch(handle, List.of());

        verify(client, never()).postExternalCollects(any(), any());
        verify(client).finishStep(eq("step1"), any());
        verify(client).finishBatch(eq("exec1"), any());
    }

    @Test
    void cleanse_종료시_CleanseResult_건수를_그대로_배치에_반영한다() {
        BatchHandle handle = new BatchHandle("exec2", "step2", java.time.LocalDateTime.now().minusSeconds(1), true);

        service().finishCleanseBatch(handle, new CleanseResult(7, 6, 1));

        ArgumentCaptor<JSONObject> batchCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).finishBatch(eq("exec2"), batchCaptor.capture());
        assertThat(batchCaptor.getValue().getInt("targetCnt")).isEqualTo(7);
        assertThat(batchCaptor.getValue().getInt("successCnt")).isEqualTo(6);
        assertThat(batchCaptor.getValue().getInt("failCnt")).isEqualTo(1);
        assertThat(batchCaptor.getValue().getString("execStsCd")).isEqualTo("PARTIAL");
    }

    @Test
    void cleanse_배치_시작시_jobNm과_stepTypeCd가_CLEANSE로_고정된다() {
        when(client.isEnabled()).thenReturn(true);
        when(client.createBatch(any())).thenReturn(Optional.of("exec1"));
        when(client.createStep(anyString(), any())).thenReturn(Optional.of("step1"));

        service().startCleanseBatch(ExecutionType.SCHEDULE, "scheduler:cleanse");

        ArgumentCaptor<JSONObject> batchCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).createBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getString("jobNm")).isEqualTo("외부연계 정제");
        assertThat(batchCaptor.getValue().getString("triggerBy")).isEqualTo("scheduler:cleanse");

        ArgumentCaptor<JSONObject> stepCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(client).createStep(eq("exec1"), stepCaptor.capture());
        assertThat(stepCaptor.getValue().getString("stepTypeCd")).isEqualTo("CLEANSE");
    }
}
