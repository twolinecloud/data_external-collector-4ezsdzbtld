package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;

/**
 * 정제 단계 인터페이스.
 *
 * <p><b>TODO:</b> 정제 규칙(필드 매핑/검증/결측치 처리)은 admin-db 최종 테이블 스키마가
 * 확정된 후 구현. 지금은 raw_staging의 status 전이 흐름(COLLECTED -&gt; CLEANSED,
 * 실패 시 CLEANSE_FAILED)만 계약으로 정의해둠. private-doc/task-spec.md 참고.</p>
 */
public interface PublicDataCleanser {

    /** source/api에 맞는 정제기인지 판별 (전략 패턴 선택용). */
    boolean supports(String sourceName, String apiName);

    /** rawPayload(JSON 원문)를 admin-db 적재 스키마에 맞게 정제한 JSON 원문을 반환. */
    String cleanse(String rawPayload) throws CleanseException;
}
