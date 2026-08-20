package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;

import java.util.List;

/**
 * 정제 단계 인터페이스. raw_staging의 원본(수집 1회 = JSON 배열 1개, 카테고리x시간별로
 * 흩어진 "긴 형태" 데이터)을 시간 단위로 묶어 카테고리→값을 한 행에 펼친 "넓은 형태"로
 * 변환한다.
 *
 * <p>admin-db 최종 테이블 스키마는 아직 미확정이지만, 이 변환 자체는 그와 독립적으로
 * 진행 가능하다고 판단해 구현함 - 나중에 적재 스키마가 정해지면 그에 맞춰 필드명/타입만
 * 조정하면 됨.</p>
 */
public interface PublicDataCleanser {

    /** {@link egovframework.external.publicdata.collector.PublicDataCollector#operationKey()}로 판별 (전략 패턴 선택용). */
    boolean supports(String operationKey);

    /** rawPayload(JSON 배열 원문)를 넓은 형태로 정제한 JSON 배열 원문을 반환. */
    String cleanse(String rawPayload) throws CleanseException;

    /**
     * 구조 드리프트 감지용 프로브 목록 (2026-08-14 추가). 이 정제기가 만들어질 당시 실측으로
     * 확인해둔 원본 API 필드 구조를 선언해두면, {@link JsonStructureDriftDetector}가 매 정제
     * 시도마다 실제 rawPayload와 대조해서 "우리가 모르는 새 필드가 생겼다"/"있어야 할 필드가
     * 없어졌다"를 로그로 남긴다 - API가 스펙 확인 없이 슬쩍 바뀌어도 조용히 놓치지 않기 위함.
     * 기본은 빈 리스트(검사 안 함) - 프로브를 선언 안 한 정제기는 그냥 대상에서 빠진다.
     */
    default List<StructureProbe> structureProbes() {
        return List.of();
    }
}
