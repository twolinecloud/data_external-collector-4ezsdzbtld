package egovframework.external.publicdata.scheduler;

import egovframework.external.service.FacilitySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 교정기관 목록 자동 동기화 스케줄러 - 매일 새벽 4시(purge 새벽 3시 이후 여유 두고).
 *
 * <p>{@code public-data.facility-sync.enabled=true}일 때만 빈으로 등록됨(2026-08-24 수정) -
 * {@link FacilitySyncService} 자체가 이 조건일 때만 빈으로 존재하므로(클래스 주석 참고)
 * 이 스케줄러도 같은 조건이어야 의존성이 성립.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.facility-sync", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FacilitySyncScheduler {

    private final FacilitySyncService facilitySyncService;

    @Scheduled(cron = "${public-data.facility-sync.cron:0 0 4 * * *}")
    public void sync() {
        facilitySyncService.sync();
    }
}
