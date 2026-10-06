package egovframework.external.controller;

import egovframework.external.dto.ScheduleRefreshRequest;
import egovframework.external.exception.ConflictException;
import egovframework.external.exception.ExceptionResolver;
import egovframework.external.exception.InvalidParameterException;
import egovframework.external.service.CollectorManualRunService;
import egovframework.external.service.ScheduleRefreshService;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 서비스가 부르는 HTTP 계약(경로·상태 코드·본문)을 고정한다. */
@ExtendWith(MockitoExtension.class)
class AdminLinkControllerTest {

    @Mock
    private CollectorManualRunService manualRunService;
    @Mock
    private ScheduleRefreshService scheduleRefreshService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminLinkController(manualRunService, scheduleRefreshService))
            .setControllerAdvice(new ExceptionResolver())
            .build();
    }

    private MvcResult dispatched(RequestBuilder builder) throws Exception {
        MvcResult started = mvc.perform(builder).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started)).andReturn();
    }

    @Test
    void 바로_실행은_202와_execId를_돌려준다() throws Exception {
        when(manualRunService.start("kma-asos-hourly", "admin01"))
            .thenReturn(new CollectorManualRunService.Started("kma-asos-hourly", "EXTERNAL_PUBLIC", "EXEC-1"));

        MvcResult started = mvc.perform(post("/internal/collect/external-incremental/kma-asos-hourly")
                .param("triggerBy", "admin01").param("to", "2026-10-06T09:00:00"))
            .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.http_status_code").value(202))
            .andExpect(jsonPath("$.result.execId").value("EXEC-1"))
            .andExpect(jsonPath("$.result.operationKey").value("kma-asos-hourly"))
            .andExpect(jsonPath("$.result.dataTypeCd").value("EXTERNAL_PUBLIC"));
    }

    @Test
    void 로그_컬렉터가_꺼져_execId가_없어도_202다() throws Exception {
        when(manualRunService.start(any(), any()))
            .thenReturn(new CollectorManualRunService.Started("kma-asos-hourly", "EXTERNAL_PUBLIC", null));

        MvcResult started = mvc.perform(post("/internal/collect/external-incremental/kma-asos-hourly")
                .param("triggerBy", "admin01"))
            .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.result.execId").doesNotExist());
    }

    @Test
    void 이미_진행_중이면_409다() throws Exception {
        when(manualRunService.start(any(), any()))
            .thenThrow(new ConflictException(LogManager.getLogger(getClass()), "running"));

        MvcResult result = dispatched(post("/internal/collect/external-incremental/kma-asos-hourly").param("triggerBy", "admin01"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void triggerBy가_없으면_400이다() throws Exception {
        when(manualRunService.start(any(), any()))
            .thenThrow(new InvalidParameterException(LogManager.getLogger(getClass()), "triggerBy"));

        MvcResult result = dispatched(post("/internal/collect/external-incremental/kma-asos-hourly"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void 즉시_반영은_본문을_받아_서비스에_넘기고_200을_돌려준다() throws Exception {
        MvcResult started = mvc.perform(post("/internal/schedule/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("{\"dataTypeCd\":\"EXTERNAL_PUBLIC\",\"schdCd\":\"KMA_ASOS_HOURLY\",\"activeYn\":\"N\","
                + "\"execSchedTypeCd\":\"INTERVAL_BASED\",\"schedVal\":\"01:00\"}"))
            .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.result.activeYn").value("N"))
            .andExpect(jsonPath("$.result.applied").value("activeYn"));

        verify(scheduleRefreshService).apply(new ScheduleRefreshRequest(
            "EXTERNAL_PUBLIC", "KMA_ASOS_HOURLY", "N", "INTERVAL_BASED", "01:00"));
    }

    @Test
    void 즉시_반영_본문에_모르는_필드가_있어도_받는다() throws Exception {
        MvcResult started = mvc.perform(post("/internal/schedule/refresh").contentType(MediaType.APPLICATION_JSON)
            .content("{\"dataTypeCd\":\"EXTERNAL_LAW\",\"activeYn\":\"Y\",\"somethingNew\":1}"))
            .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());
    }
}
