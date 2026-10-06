package egovframework.external.publicdata.scheduler;

import egovframework.external.logcollector.DataTypeClassifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 관리자 서비스(admin-api)의 배치 스케줄 설정에서 오퍼레이션 사용 여부를 읽어 {@link ScheduleActivation}에 반영한다.
 * 정형·비정형 수집기와 같이 <b>기동 직후와 5분마다</b> 가져온다.
 *
 * <p>관리자 서비스가 즉시 반영 호출({@code /internal/schedule/refresh})을 실패해도 이 폴링이 최대 5분 안에 따라잡는다.
 * 관리자 서비스 주소가 설정되지 않았으면 아무것도 하지 않고, 조회에 실패하면 <b>기존 값을 그대로 둔다</b> -
 * 일시적인 장애로 중지해 둔 스케줄이 갑자기 켜지거나 그 반대가 되면 안 된다.
 */
@Component
public class ScheduleSettingSync {

    private static final Logger logger = LogManager.getLogger(ScheduleSettingSync.class);

    private static final Set<String> OWNED_DATA_TYPES =
        Set.of(DataTypeClassifier.EXTERNAL_PUBLIC, DataTypeClassifier.EXTERNAL_LAW);

    private final AdminBatchScheduleClient client;
    private final ScheduleActivation activation;

    public ScheduleSettingSync(AdminBatchScheduleClient client, ScheduleActivation activation) {
        this.client = client;
        this.activation = activation;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        int rows = sync();
        if (rows < 0) {
            logger.info("[SCHEDULE] 기동 시 스케줄 동기화 안 함(관리자 서비스 주소 없음 또는 조회 실패) - 모든 스케줄을 사용으로 시작");
        } else {
            logger.info("[SCHEDULE] 기동 시 스케줄 동기화 완료 rows={}", rows);
        }
    }

    @Scheduled(fixedDelayString = "${public-data.schedule-sync.interval-ms:300000}",
        initialDelayString = "${public-data.schedule-sync.interval-ms:300000}")
    public void syncPeriodically() {
        sync();
    }

    /** @return 반영한 행 수. 관리자 서비스 주소가 없거나 조회에 실패하면 -1 */
    int sync() {
        if (!client.isConfigured()) {
            return -1;
        }
        try {
            List<ScheduleActivation.Setting> settings = new ArrayList<>();
            for (AdminBatchScheduleClient.Row row : client.fetchAll()) {
                if (OWNED_DATA_TYPES.contains(row.dataTypeCd())) {
                    String schdCd = (row.schdCd() == null || row.schdCd().isBlank()) ? null : row.schdCd().trim();
                    settings.add(new ScheduleActivation.Setting(row.dataTypeCd(), schdCd, "Y".equals(row.activeYn())));
                }
            }
            activation.replaceAll(settings);
            return settings.size();
        } catch (Exception e) {
            logger.warn("[SCHEDULE] 스케줄 설정 동기화 실패 - 기존 값을 유지합니다 (admin-api 연결 확인 필요): {}", e.getMessage());
            return -1;
        }
    }
}
