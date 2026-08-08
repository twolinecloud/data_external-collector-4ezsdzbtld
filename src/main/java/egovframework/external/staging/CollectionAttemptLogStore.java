package egovframework.external.staging;

import egovframework.external.dto.CollectionAttemptLogDto;

import java.util.List;

/**
 * 소스별 수집 시도 로그 저장소 포트. {@link RawStagingStore}와 동일한 이유로 인터페이스로
 * 분리 - 나중에 DB/admin-db 연동 어댑터로 교체 가능하도록.
 *
 * <p>{@code findUnclaimed}/{@code claimBatch}는 "소스별 독립 스케줄 수집" ↔ "카테고리 단위
 * 배치로 묶어서 정제/적재"를 잇는 접점으로 설계해둔 것 - 배치 오케스트레이터가 아직 없어
 * 현재는 호출되지 않지만, admin-db 적재 방식(tb_ext_collect_log 등) 확정 시 필요해질
 * 것으로 예상해 계약만 미리 정의해둠.</p>
 */
public interface CollectionAttemptLogStore {

    /** 수집 시도 1건 기록 (성공/실패 모두). */
    void insert(CollectionAttemptLogDto dto);

    /** 아직 어떤 배치에도 집계되지 않은(claimedBatchId 없음) 시도 로그 전체 조회. */
    List<CollectionAttemptLogDto> findUnclaimed();

    /** 주어진 로그들을 이번 배치가 집계했다고 표시. */
    void claimBatch(List<Long> ids, String batchId);
}
