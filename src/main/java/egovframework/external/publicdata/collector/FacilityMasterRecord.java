package egovframework.external.publicdata.collector;

/**
 * 교정기관 1개소의 기준정보 - {@link FacilityMasterSource}(csv/db)의 공통 산출 형태.
 * {@link Location}/{@link FacilitySido}/{@link FacilityRegion}은 각자 용도에 맞게 이 레코드의
 * 부분집합을 뽑아 쓰는 특화 뷰(각 Loader가 변환).
 */
public record FacilityMasterRecord(
    String facilityId, String facilityName, String sido, String sigungu, String nx, String ny) {
}
