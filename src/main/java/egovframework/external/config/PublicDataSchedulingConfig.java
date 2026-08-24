package egovframework.external.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 공공데이터 수집 스케줄러(@Scheduled) 활성화. 기존 Main/AsyncMVCConfig에는 없던 설정이라 별도 config로 분리. */
@Configuration
@EnableScheduling
public class PublicDataSchedulingConfig {
}
