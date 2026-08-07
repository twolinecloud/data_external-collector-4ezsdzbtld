package egovframework.external.publicdata.loader;

import egovframework.external.exception.LoadException;

/**
 * 적재 단계 인터페이스.
 *
 * <p><b>TODO:</b> admin-db 최종 테이블 스키마/upsert 키가 확정된 후 구현 (PL 확인 필요,
 * private-doc/task-spec.md 6번/11번 항목 참고). 소스 고유키+수집시각 기준 upsert로
 * 멱등성을 보장해야 함 (스케줄러 중복 실행/재시도 대비).</p>
 *
 * <p>{@code supports}는 {@link egovframework.external.publicdata.cleanser.PublicDataCleanser}와
 * 동일하게 {@code operationKey} 기반으로 판별한다 (28번 항목 - sourceName/apiName 방식은
 * 문자열 표시명이라 정제/적재 두 단계에서 굳이 다르게 유지할 이유가 없어서 미리 통일해둠).</p>
 */
public interface PublicDataLoader {

    /** {@link egovframework.external.publicdata.collector.PublicDataCollector#operationKey()}로 판별 (전략 패턴 선택용). */
    boolean supports(String operationKey);

    /** cleansedPayload(JSON 원문)를 admin-db 최종 테이블에 upsert. */
    void load(String cleansedPayload) throws LoadException;
}
