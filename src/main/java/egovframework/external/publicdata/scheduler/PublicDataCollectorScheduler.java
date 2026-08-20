package egovframework.external.publicdata.scheduler;

import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.KmaLocationCollectorFactory;
import egovframework.external.publicdata.collector.KmaWeatherWarningListCollector;
import egovframework.external.publicdata.collector.MolegLawCollectorFactory;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.service.PublicDataCollectionAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 소스(오퍼레이션)별 독립 스케줄러. cron은 각 오퍼레이션의 실제 발표주기에 맞춤
 * (weather-api.docx "예보 발표시각" 기준, 발표+제공지연 이후 여유를 두고 호출).
 * {@code application.yml}의 {@code public-data.collector.*.cron}으로 오버라이드 가능.
 *
 * <p>위치의존 오퍼레이션 3종(초단기실황/초단기예보/단기예보)은 cron 하나당 오퍼레이션
 * 하나가 아니라 <b>오퍼레이션 하나가 지역(59개소) 전체를 순회</b>하는 구조 - 소스 하나당
 * {@code @Scheduled} 메서드 하나였던 걸 그대로 유지하면서, 각 메서드 내부에서
 * {@link KmaLocationCollectorFactory}가 만든 지역별 인스턴스를 차례로 실행한다. 개별
 * 지역의 성공/실패는 여전히 {@link PublicDataCollectionAttemptService}가 각각 독립적으로
 * 기록한다.</p>
 *
 * <p>새 위치의존 소스를 추가할 때: {@code kma-facility-locations.csv}에 행만 추가하면
 * 됨 - 코드 변경 불필요. 새 오퍼레이션(위치독립)을 추가할 때: (1) {@code PublicDataCollector}
 * 구현체 추가 (2) 여기에 {@code @Scheduled} 메서드 하나 추가 (3) application.yml에
 * cron 프로퍼티 추가.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataCollectorScheduler {

    private final PublicDataCollectionAttemptService collectionAttemptService;
    private final KmaLocationCollectorFactory locationCollectorFactory;
    private final KmaWeatherWarningListCollector kmaWeatherWarningListCollector;
    private final MolegLawCollectorFactory lawCollectorFactory;

    /** 초단기실황: 매시 정각 발표, 10분 이후 제공 -> 매시 12분에 전 지역(59개소) 순회 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-ultra-srt-ncst.cron:0 12 * * * *}")
    public void collectKmaUltraSrtNcst() {
        runAll(locationCollectorFactory.ultraSrtNcstCollectors());
    }

    /** 초단기예보: 매시 30분 발표, 45분 이후 제공 -> 매시 47분에 전 지역(59개소) 순회 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-ultra-srt-fcst.cron:0 47 * * * *}")
    public void collectKmaUltraSrtFcst() {
        runAll(locationCollectorFactory.ultraSrtFcstCollectors());
    }

    /** 단기예보: 1일 8회(02/05/08/11/14/17/20/23시) 발표, 10분 이후 제공 -> 15분에 전 지역(59개소) 순회 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-vilage-fcst.cron:0 15 2,5,8,11,14,17,20,23 * * *}")
    public void collectKmaVilageFcst() {
        runAll(locationCollectorFactory.vilageFcstCollectors());
    }

    /** 기상특보목록: 발표주기가 정해져있지 않아(이벤트성) 10분 간격 폴링. 전국 조회 1회라 지역 순회 불필요. */
    @Scheduled(cron = "${public-data.collector.kma-weather-warning-list.cron:0 */10 * * * *}")
    public void collectKmaWeatherWarningList() {
        collectionAttemptService.run(kmaWeatherWarningListCollector, ExecutionType.SCHEDULE);
    }

    /**
     * 형사법령 본문조회: 하루 1회, 새벽 5시(부하 적은 시간대) - 60건 전체 순회 수집.
     * 변경감지/이력누적은 아직 여기서 안 함(admin-db 쓰기 경로 확정 대기, private-doc 31번
     * 항목) - 지금은 매번 전체를 raw_staging에 새로 적재하기만 함.
     */
    @Scheduled(cron = "${public-data.collector.moleg-criminal-law.cron:0 0 5 * * *}")
    public void collectMolegCriminalLaws() {
        runAll(lawCollectorFactory.allLawCollectors());
    }

    private void runAll(List<PublicDataCollector> collectors) {
        for (PublicDataCollector collector : collectors) {
            collectionAttemptService.run(collector, ExecutionType.SCHEDULE);
        }
    }
}
