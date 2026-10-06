package egovframework.external.publicdata.scheduler;

import egovframework.external.publicdata.scheduler.mapper.ScheduleSettingMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * admin-db의 배치 스케줄 설정에서 오퍼레이션 사용 여부를 읽어 {@link ScheduleActivation}에 반영한다.
 *
 * <p>관리자 서비스가 즉시 반영 호출({@code /internal/schedule/refresh})을 실패해도, 이 동기화가 최대 5분 안에 따라잡는다.
 * 재시작 직후에도 처음 한 번 읽는다.
 *
 * <p>admin-db 연결이 구성되지 않은 환경(매퍼 빈 없음)에서는 아무것도 하지 않고, 조회에 실패하면 <b>기존 값을 그대로
 * 둔다</b> - 일시적인 DB 장애로 중지해 둔 스케줄이 갑자기 켜지거나 그 반대가 되면 안 된다.
 */
@Component
public class ScheduleSettingSync {

    private static final Logger logger = LogManager.getLogger(ScheduleSettingSync.class);

    private final ObjectProvider<ScheduleSettingMapper> mapperProvider;
    private final ScheduleActivation activation;

    public ScheduleSettingSync(ObjectProvider<ScheduleSettingMapper> mapperProvider, ScheduleActivation activation) {
        this.mapperProvider = mapperProvider;
        this.activation = activation;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        sync();
    }

    @Scheduled(fixedDelayString = "${public-data.schedule-sync.interval-ms:300000}",
        initialDelayString = "${public-data.schedule-sync.interval-ms:300000}")
    public void syncPeriodically() {
        sync();
    }

    /** @return 반영한 행 수. DB를 쓸 수 없거나 조회에 실패하면 -1 */
    int sync() {
        ScheduleSettingMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return -1;
        }
        try {
            List<ScheduleActivation.Setting> settings = new ArrayList<>();
            for (Map<String, Object> row : mapper.selectExternalSettings()) {
                String schdCd = row.get("schdCd") == null ? null : row.get("schdCd").toString();
                settings.add(new ScheduleActivation.Setting(
                    String.valueOf(row.get("dataTypeCd")), schdCd, "Y".equals(String.valueOf(row.get("activeYn")).trim())));
            }
            activation.replaceAll(settings);
            return settings.size();
        } catch (Exception e) {
            logger.warn("[SCHEDULE] 스케줄 설정 동기화 실패 - 기존 값을 유지합니다 (admin-db 연결 확인 필요): {}", e.getMessage());
            return -1;
        }
    }
}
