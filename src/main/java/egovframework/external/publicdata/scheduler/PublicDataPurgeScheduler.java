package egovframework.external.publicdata.scheduler;

import egovframework.external.service.PublicDataPurgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * admin-db 보존기간(기본 30일) 초과 데이터를 매일 새벽에 지우는 스케줄러.
 *
 * <p>{@code public-data.purge.enabled=true}일 때만 빈으로 등록됨(2026-08-24 수정) -
 * {@link PublicDataPurgeService} 자체가 이 조건일 때만 빈으로 존재하므로(admin-db 매퍼
 * 미존재 시 부팅 실패 방지, 클래스 주석 참고) 이 스케줄러도 같은 조건이어야 의존성이 성립.</p>
 */
@Component
@ConditionalOnProperty(prefix = "public-data.purge", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PublicDataPurgeScheduler {

    private final PublicDataPurgeService purgeService;

    @Scheduled(cron = "${public-data.purge.cron:0 0 3 * * *}")
    public void purge() {
        purgeService.purgeExpired();
    }
}
