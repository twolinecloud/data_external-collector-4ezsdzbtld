package egovframework.external.publicdata.collector;

/**
 * {@link VWorldGeocoder} 호출 결과.
 *
 * @param status  SUCCESS | NOT_FOUND(VWorld 주소DB에 없음) | FAILED(호출/응답 오류)
 * @param sidoNm  VWorld 응답의 refined.structure.level1 - 시도명(성공 시에만 채워짐)
 * @param sigunguNm VWorld 응답의 refined.structure.level2 - 시군구명(성공 시에만 채워짐)
 */
public record GeocodeResult(String status, Double lat, Double lon, String sidoNm, String sigunguNm) {

    public static final String SUCCESS = "SUCCESS";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String FAILED = "FAILED";

    public static GeocodeResult notFound() {
        return new GeocodeResult(NOT_FOUND, null, null, null, null);
    }

    public static GeocodeResult failed() {
        return new GeocodeResult(FAILED, null, null, null, null);
    }

    public boolean isSuccess() {
        return SUCCESS.equals(status);
    }
}
