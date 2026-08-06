package egovframework.external.publicdata.scheduler;

import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.KmaUltraSrtFcstCollector;
import egovframework.external.publicdata.collector.KmaUltraSrtNcstCollector;
import egovframework.external.publicdata.collector.KmaVilageFcstCollector;
import egovframework.external.publicdata.collector.KmaWeatherWarningListCollector;
import egovframework.external.service.PublicDataCollectionAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 소스(오퍼레이션)별 독립 스케줄러. cron은 각 오퍼레이션의 실제 발표주기에 맞춤
 * (weather-api.docx "예보 발표시각" 기준, 발표+제공지연 이후 여유를 두고 호출).
 * {@code application.yml}의 {@code public-data.collector.*.cron}으로 오버라이드 가능.
 *
 * <p>새 소스를 추가할 때: (1) {@code PublicDataCollector} 구현체 추가 (2) 여기에
 * {@code @Scheduled} 메서드 하나 추가 (3) application.yml에 cron 프로퍼티 추가.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataCollectorScheduler {

    private final PublicDataCollectionAttemptService collectionAttemptService;
    private final KmaUltraSrtNcstCollector kmaUltraSrtNcstCollector;
    private final KmaUltraSrtFcstCollector kmaUltraSrtFcstCollector;
    private final KmaVilageFcstCollector kmaVilageFcstCollector;
    private final KmaWeatherWarningListCollector kmaWeatherWarningListCollector;

    /** 초단기실황: 매시 정각 발표, 10분 이후 제공 -> 매시 12분에 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-ultra-srt-ncst.cron:0 12 * * * *}")
    public void collectKmaUltraSrtNcst() {
        collectionAttemptService.run(kmaUltraSrtNcstCollector, ExecutionType.SCHEDULE);
    }

    /** 초단기예보: 매시 30분 발표, 45분 이후 제공 -> 매시 47분에 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-ultra-srt-fcst.cron:0 47 * * * *}")
    public void collectKmaUltraSrtFcst() {
        collectionAttemptService.run(kmaUltraSrtFcstCollector, ExecutionType.SCHEDULE);
    }

    /** 단기예보: 1일 8회(02/05/08/11/14/17/20/23시) 발표, 10분 이후 제공 -> 15분에 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-vilage-fcst.cron:0 15 2,5,8,11,14,17,20,23 * * *}")
    public void collectKmaVilageFcst() {
        collectionAttemptService.run(kmaVilageFcstCollector, ExecutionType.SCHEDULE);
    }

    /** 기상특보목록: 발표주기가 정해져있지 않아(이벤트성) 10분 간격 폴링. */
    @Scheduled(cron = "${public-data.collector.kma-weather-warning-list.cron:0 */10 * * * *}")
    public void collectKmaWeatherWarningList() {
        collectionAttemptService.run(kmaWeatherWarningListCollector, ExecutionType.SCHEDULE);
    }
}
