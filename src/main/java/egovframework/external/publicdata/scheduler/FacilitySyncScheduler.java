package egovframework.external.publicdata.scheduler;

import egovframework.external.service.FacilitySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 교정기관 목록 자동 동기화 스케줄러 - 매일 새벽 4시(purge 새벽 3시 이후 여유 두고).
 * {@code public-data.facility-sync.enabled=false}(기본값)면
 * {@link FacilitySyncService#sync()}가 즉시 no-op을 반환해서 조용히 아무 일도 안 한다.
 */
@Component
@RequiredArgsConstructor
public class FacilitySyncScheduler {

    private final FacilitySyncService facilitySyncService;

    @Scheduled(cron = "${public-data.facility-sync.cron:0 0 4 * * *}")
    public void sync() {
        facilitySyncService.sync();
    }
}
