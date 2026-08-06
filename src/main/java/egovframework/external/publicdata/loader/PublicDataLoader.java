package egovframework.external.publicdata.loader;

import egovframework.external.exception.LoadException;

/**
 * 적재 단계 인터페이스.
 *
 * <p><b>TODO:</b> admin-db 최종 테이블 스키마/upsert 키가 확정된 후 구현 (PL 확인 필요,
 * private-doc/task-spec.md 6번/11번 항목 참고). 소스 고유키+수집시각 기준 upsert로
 * 멱등성을 보장해야 함 (스케줄러 중복 실행/재시도 대비).</p>
 */
public interface PublicDataLoader {

    /** source/api에 맞는 적재기인지 판별 (전략 패턴 선택용). */
    boolean supports(String sourceName, String apiName);

    /** cleansedPayload(JSON 원문)를 admin-db 최종 테이블에 upsert. */
    void load(String cleansedPayload) throws LoadException;
}
