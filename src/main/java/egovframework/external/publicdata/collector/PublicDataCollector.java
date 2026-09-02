package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * 실제 수집 수행. 한 번 호출로 여러 건(예: 시간대별/카테고리별 값)을 받아올 수 있으므로
     * 리스트를 반환하며, 이 리스트 전체가 raw_staging 의 한 행(raw_payload, JSON 배열 원문)이 된다.
     */
    List<String> collect() throws CollectException;

    /**
     * 안정적인 오퍼레이션 식별자 - {@link #key()}와 달리 지역(facilityId) 접미사가 안 붙는다.
     * 정제 단계({@code PublicDataCleanser})가 이 값으로 알맞은 정제기를 찾는다. {@code apiName()}은
     * 사람이 보라고 있는 표시용 문자열(기관명이 섞여 들어갈 수 있음)이라 매칭 키로 쓰기 불안정 -
     * 그래서 별도로 둠. 위치기반이 아닌 컬렉터는 기본값(= key())을 그대로 쓰면 됨.
     */
    default String operationKey() {
        return key();
    }

    /** 이 컬렉터가 특정 지역(교정기관) 전용이면 그 {@code facilityId}, 아니면 {@code null}. */
    default String facilityId() {
        return null;
    }

    /**
     * {@code collectedOn}에 수집한 분이 raw_staging에서 유효한 마지막 시각.
     * {@code null}(기본값)이면 기한 없음.
     *
     * <p>수집 단위가 반복되는 소스는 {@code InMemoryRawStagingStore#insert}의 종결 행 회수만으로
     * 충분하지만, 그건 <b>다음 수집이 실제로 들어와야</b> 동작한다. 적재가 오래 막혀 있으면 미적재
     * 행(COLLECTED/CLEANSED/LOAD_FAILED)은 회수 대상이 아니라서 계속 남는데, 데이터 자체에
     * 유효기간이 있는 소스라면 그렇게 붙들고 있어도 쓸모가 없다. 그런 소스가 여기에 기한을 밝힌다.</p>
     *
     * <p>기상 실황/예보가 그 경우다. 판단 기준은 수집 시각으로부터 몇 시간이 아니라 <b>날짜</b>이므로
     * (2026-09-02, 사용자 확인) 구현체는 자정 경계를 돌려준다. 반대로 법령은 내용이 오래 유효하고,
     * 재난문자는 적재 재시도가 생명이라 둘 다 기한을 두지 않는다.</p>
     */
    default LocalDateTime stagingExpiresAt(LocalDate collectedOn) {
        return null;
    }
}
