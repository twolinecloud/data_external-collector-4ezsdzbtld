package egovframework.external.publicdata.loader;

import egovframework.external.dto.RawStagingDto;
import egovframework.external.exception.LoadException;

/**
 * 적재 단계 인터페이스. admin-db(kcais) 최종 테이블 스키마/upsert 키 확정 완료
 * (private-doc/cleanse-db-schema-spec.md, 2026-08-21) - 구현체는
 * {@code egovframework.external.publicdata.loader} 하위 소스별 클래스 참고.
 *
 * <p>{@code supports}는 {@link egovframework.external.publicdata.cleanser.PublicDataCleanser}와
 * 동일하게 {@code operationKey} 기반으로 판별한다 - sourceName/apiName 방식은
 * 문자열 표시명이라 정제/적재 두 단계에서 굳이 다르게 유지할 이유가 없어서 미리 통일해둠.</p>
 *
 * <p><b>{@code cleansedPayload}(String)이 아니라 {@link RawStagingDto} 전체를 받는 이유</b>:
 * 정제 산출물 JSON엔 facilityId가 안 들어있는 경우가 있다(예: 초단기실황/예보/단기예보는
 * nx/ny만 있고 facilityId가 없음 - {@code KmaUltraSrtNcstCleanser} 등 참고). admin-db
 * 테이블은 {@code facility_id}로 FK를 걸어야 해서, cleansedPayload 밖에 있는
 * {@code dto.getFacilityId()}/{@code operationKey}/{@code collectedAt}/{@code cleansedAt}까지
 * 같이 필요하다.</p>
 */
public interface PublicDataLoader {

    /** {@link egovframework.external.publicdata.collector.PublicDataCollector#operationKey()}로 판별 (전략 패턴 선택용). */
    boolean supports(String operationKey);

    /** dto.getCleansedPayload()(JSON 원문) + dto의 나머지 메타데이터를 admin-db 최종 테이블에 upsert. */
    void load(RawStagingDto dto) throws LoadException;
}
