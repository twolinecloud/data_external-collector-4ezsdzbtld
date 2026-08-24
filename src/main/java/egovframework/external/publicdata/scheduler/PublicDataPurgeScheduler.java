package egovframework.external.publicdata.scheduler;

import egovframework.external.service.PublicDataPurgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * admin-db 보존기간(기본 30일) 초과 데이터를 매일 새벽에 지우는 스케줄러.
 * {@code public-data.purge.enabled=false}(기본값)면 {@link PublicDataPurgeService#purgeExpired()}가
 * 즉시 no-op을 반환해서 조용히 아무 일도 안 한다(Load/Cleanse와 동일 원칙).
 */
@Component
@RequiredArgsConstructor
public class PublicDataPurgeScheduler {

    private final PublicDataPurgeService purgeService;

    @Scheduled(cron = "${public-data.purge.cron:0 0 3 * * *}")
    public void purge() {
        purgeService.purgeExpired();
    }
}
