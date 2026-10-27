package egovframework.external.publicdata.collector;

/**
 * 교정기관 1개소의 기준정보 - {@link FacilityMasterSource}(csv/db)의 공통 산출 형태.
 * {@link Location}/{@link FacilitySido}/{@link FacilityRegion}은 각자 용도에 맞게 이 레코드의
 * 부분집합을 뽑아 쓰는 특화 뷰(각 Loader가 변환).
 *
 * <p>{@code lat}/{@code lon}은 기상특보 세분 구역(홍성동부/홍성서부 등)을 시설 위치로 가르는 데 쓴다
 * ({@link KmaWarningZoneDirectory}, 2026-10-27). 없으면 null - 그 시설은 시군 단위로만 매칭된다.</p>
 */
public record FacilityMasterRecord(
    String facilityId, String facilityName, String sido, String sigungu, String nx, String ny, Double lat, Double lon) {

    public FacilityMasterRecord(String facilityId, String facilityName, String sido, String sigungu, String nx, String ny) {
        this(facilityId, facilityName, sido, sigungu, nx, ny, null, null);
    }
}
