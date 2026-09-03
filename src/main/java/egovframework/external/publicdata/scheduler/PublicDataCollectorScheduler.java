package egovframework.external.publicdata.scheduler;

import egovframework.external.logcollector.BatchHandle;
import egovframework.external.logcollector.DataTypeClassifier;
import egovframework.external.logcollector.LogCollectorBatchService;
import egovframework.external.model.CollectResult;
import egovframework.external.model.ExecutionType;
import egovframework.external.publicdata.collector.DisasterMsgCollector;
import egovframework.external.publicdata.collector.KmaAsosHourlyCollector;
import egovframework.external.publicdata.collector.KmaLocationCollectorFactory;
import egovframework.external.publicdata.collector.KmaWeatherWarningListCollector;
import egovframework.external.publicdata.collector.LivingWthrIdxCollectorFactory;
import egovframework.external.publicdata.collector.MolegLawCollectorFactory;
import egovframework.external.publicdata.collector.PublicDataCollector;
import egovframework.external.service.PublicDataCollectionAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
 *
 * <p><b>로그 컬렉터 연동(2026-08-20)</b>: {@code @Scheduled} 메서드 1틱 = 로그 컬렉터
 * 배치(execId) 1개 (private-doc/log-collector-api-spec.md §8). {@link LogCollectorBatchService}가
 * 꺼져있으면({@code log-collector.enabled=false}, 기본값) 아래 로직은 전부 조용히 no-op이라
 * 기존 수집 동작에는 영향이 없다.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicDataCollectorScheduler {

    private final PublicDataCollectionAttemptService collectionAttemptService;
    private final KmaLocationCollectorFactory locationCollectorFactory;
    private final KmaWeatherWarningListCollector kmaWeatherWarningListCollector;
    private final KmaAsosHourlyCollector kmaAsosHourlyCollector;
    private final MolegLawCollectorFactory lawCollectorFactory;
    private final DisasterMsgCollector disasterMsgCollector;
    private final LivingWthrIdxCollectorFactory livingWthrIdxCollectorFactory;
    private final LogCollectorBatchService logCollectorBatchService;

    /** 초단기실황: 매시 정각 발표, 10분 이후 제공 -> 매시 12분에 전 지역(59개소) 순회 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-ultra-srt-ncst.cron:0 12 * * * *}")
    public void collectKmaUltraSrtNcst() {
        runAll("kma-village-forecast-ultra-srt-ncst", locationCollectorFactory.ultraSrtNcstCollectors());
    }

    /** 초단기예보: 매시 30분 발표, 45분 이후 제공 -> 매시 47분에 전 지역(59개소) 순회 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-ultra-srt-fcst.cron:0 47 * * * *}")
    public void collectKmaUltraSrtFcst() {
        runAll("kma-village-forecast-ultra-srt-fcst", locationCollectorFactory.ultraSrtFcstCollectors());
    }

    /** 단기예보: 1일 8회(02/05/08/11/14/17/20/23시) 발표, 10분 이후 제공 -> 15분에 전 지역(59개소) 순회 수집. */
    @Scheduled(cron = "${public-data.collector.kma-village-forecast-vilage-fcst.cron:0 15 2,5,8,11,14,17,20,23 * * *}")
    public void collectKmaVilageFcst() {
        runAll("kma-village-forecast-vilage-fcst", locationCollectorFactory.vilageFcstCollectors());
    }

    /** 기상특보목록: 발표주기가 정해져있지 않아(이벤트성) 10분 간격 폴링. 전국 조회 1회라 지역 순회 불필요. */
    @Scheduled(cron = "${public-data.collector.kma-weather-warning-list.cron:0 */10 * * * *}")
    public void collectKmaWeatherWarningList() {
        runAll("kma-weather-warning-list", List.of(kmaWeatherWarningListCollector));
    }

    /**
     * 지상관측(ASOS) 시간자료: 매시 정시 관측 -> 25분에 직전 정시분 수집. API 허브가 {@code stn=0}
     * 한 번으로 전 지점(97개)을 주므로 지역 순회가 없다({@link KmaAsosHourlyCollector} 참고).
     */
    @Scheduled(cron = "${public-data.collector.kma-asos-hourly.cron:0 25 * * * *}")
    public void collectKmaAsosHourly() {
        runAll("kma-asos-hourly", List.of(kmaAsosHourlyCollector));
    }

    /**
     * 법령/행정규칙 본문조회: 하루 1회, 새벽 5시(부하 적은 시간대) - 대상 목록 전체 순회 수집.
     * 2026-08-28부터 법령(433건) + 행정규칙(58건, moleg-admin-rule) 총 491건을 이 틱 하나가
     * 함께 처리한다({@link MolegLawCollectorFactory} 참고) - 둘 다 dataTypeCd가 EXTERNAL_LAW로
     * 같아서 로그 컬렉터 배치를 나눌 필요가 없다(EXTERNAL_PUBLIC/EXTERNAL_LAW 분리와 달리
     * 여기선 배치 1개로 충분 - {@link DataTypeClassifier} 참고). 변경감지/이력누적은 아직
     * 여기서 안 함(admin-db 쓰기 경로 확정 대기, private-doc 31번 항목) - 지금은 매번 전체를
     * raw_staging에 새로 적재하기만 함.
     */
    @Scheduled(cron = "${public-data.collector.moleg-criminal-law.cron:0 0 5 * * *}")
    public void collectMolegCriminalLaws() {
        runAll("moleg-criminal-law", lawCollectorFactory.allLawCollectors());
    }

    /**
     * 긴급재난문자 목록: 발표주기가 정해져있지 않아(이벤트성) 기상특보와 동일하게 10분 간격
     * 폴링. 전국 조회 1회라 지역 순회 불필요.
     */
    @Scheduled(cron = "${public-data.collector.safetydata-disaster-msg-list.cron:0 */10 * * * *}")
    public void collectDisasterMsgList() {
        runAll("safetydata-disaster-msg-list", List.of(disasterMsgCollector));
    }

    /**
     * 자외선지수: 1일 8회(00/03/06/09/12/15/18/21시 KST) 발표, 10분 이후 제공 -> 발표시각
     * 10분 후에 전국 16개 시도 순회 수집(실측 확인, 2026-08-24 - 정각에도 이미 응답됨).
     */
    @Scheduled(cron = "${public-data.collector.kma-living-uv-idx.cron:0 10 0,3,6,9,12,15,18,21 * * *}")
    public void collectKmaLivingUvIdx() {
        runAll("kma-living-uv-idx", livingWthrIdxCollectorFactory.uvIdxCollectors());
    }

    /** 대기정체지수: 자외선지수와 동일 발표주기 - {@link #collectKmaLivingUvIdx()} 참고. */
    @Scheduled(cron = "${public-data.collector.kma-living-air-diffusion-idx.cron:0 10 0,3,6,9,12,15,18,21 * * *}")
    public void collectKmaLivingAirDiffusionIdx() {
        runAll("kma-living-air-diffusion-idx", livingWthrIdxCollectorFactory.airDiffusionIdxCollectors());
    }

    /** operationKey 1틱 = 로그 컬렉터 배치 1개 (컬렉터가 몇 개든 - 59개소 순회도 배치 하나). */
    private void runAll(String operationKey, List<PublicDataCollector> collectors) {
        BatchHandle handle = logCollectorBatchService.startCollectBatch(
            operationKey, ExecutionType.SCHEDULE, "scheduler:" + operationKey);

        List<CollectResult> results = new ArrayList<>(collectors.size());
        for (PublicDataCollector collector : collectors) {
            results.add(collectionAttemptService.run(collector, ExecutionType.SCHEDULE));
        }

        logCollectorBatchService.finishCollectBatch(handle, results);
    }
}
