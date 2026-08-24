package egovframework.external.staging;

import egovframework.external.dto.RawStagingDto;

import java.util.List;

/**
 * 수집→정제→적재 파이프라인의 작업 데이터(원본/정제결과) 저장소 포트.
 *
 * <p>현재 활성 구현은 {@link InMemoryRawStagingStore}(메모리) 하나뿐이다. 클러스터
 * 중복실행 문제 해결 전까지는 단일 인스턴스 운용을 전제로 하고, admin-db 적재 방식이
 * 확정되면(직접 write 또는 admin-api 경유) DB/HTTP 기반 어댑터로 교체 가능하도록
 * 메서드 계약을 DB 저장을 가정한 형태(생성 ID, 상태 전이별 개별 메서드)로 유지한다.
 * (과거 raw_staging 테이블 DDL + MyBatis 매퍼 구현은 git 커밋 이력에 남아있음
 * - 되돌리려면 그걸 참고해서 이 인터페이스를 구현하면 됨)</p>
 */
public interface RawStagingStore {

    /** COLLECTED 상태로 신규 레코드 저장. dto.id 에 생성된 식별자가 채워진다. */
    void insert(RawStagingDto dto);

    /** 특정 상태의 레코드를 오래된 순으로 최대 limit 건 조회 (정제/적재 단계가 작업 대상을 가져올 때 사용). */
    List<RawStagingDto> findByStatus(String status, int limit);

    /** 정제 성공: cleansedPayload 채우고 status=CLEANSED, processedBatchId 기록. */
    void markCleansed(Long id, String cleansedPayload, String processedBatchId);

    /** 정제 실패: status=CLEANSE_FAILED, 실패사유 기록. */
    void markCleanseFailed(Long id, String failureLog, String processedBatchId);

    /** 적재 성공: status=LOADED. */
    void markLoaded(Long id);

    /** 적재 실패: status=LOAD_FAILED, 실패사유 기록. */
    void markLoadFailed(Long id, String failureLog);
}
