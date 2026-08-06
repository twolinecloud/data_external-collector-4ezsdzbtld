package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.util.List;

/**
 * 공공데이터 소스 하나(예: 기상청 동네예보 - 기온)에 대한 수집기.
 *
 * <p>소스마다 인증/응답 스키마가 다르므로 소스별 구현체(전략 패턴)를 둔다.
 * 각 구현체는 {@code PublicDataCollectorScheduler}에 자기 자신의 cron 스케줄로 등록되고,
 * {@code PublicDataCollectController}를 통해 수동으로도 트리거될 수 있다 (실행 경로는 동일한
 * {@code PublicDataCollectionAttemptService} 를 공유).</p>
 */
public interface PublicDataCollector {

    /** URL-safe 식별자. 수동 트리거 API의 path variable로 사용됨. 예: "kma-village-forecast-temperature" */
    String key();

    /** 표시용 수집 출처명. dashboard-fe 배치 상세 화면의 "수집 출처" 컬럼과 대응. 예: "공공데이터포털 (기상청 동네예보)" */
    String sourceName();

    /** 표시용 API명. dashboard-fe 배치 상세 화면의 "API" 컬럼과 대응. 예: "기온" */
    String apiName();

    /**
     * 실제 수집 수행. 한 번 호출로 여러 건(예: 지역별 기온)을 받아올 수 있으므로 리스트를 반환하며,
     * 각 원소가 raw_staging 의 한 행(raw_payload, JSON 원문)이 된다.
     */
    List<String> collect() throws CollectException;
}
