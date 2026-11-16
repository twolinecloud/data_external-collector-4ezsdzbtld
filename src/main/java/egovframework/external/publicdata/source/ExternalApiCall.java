package egovframework.external.publicdata.source;

import java.util.Map;

/**
 * 외부 API 호출 1회의 내용.
 *
 * @param channel      어느 연결 설정(타임아웃 등)을 쓰는지 - 직접 연결 어댑터가 RestTemplate을 고르는 데 쓴다
 * @param sourceName   표시용 출처명(로그·이력용)
 * @param apiName      표시용 API명(로그·이력용)
 * @param endpoint     원천 URL(쿼리 제외)
 * @param keyParam     인증 파라미터 이름(예: {@code serviceKey}, {@code authKey})
 * @param key          인증 키 - 이미 URL-encode된 값일 수 있어 직접 연결 시 그대로 붙인다(재인코딩 금지)
 * @param params       나머지 파라미터(순서 유지, 값은 인코딩 전 원문)
 * @param encodeParams true면 파라미터 값을 UTF-8 URL 인코딩해서 붙인다(한글 값이 있는 API), false면 원문 그대로
 */
public record ExternalApiCall(
    Channel channel,
    String sourceName,
    String apiName,
    String endpoint,
    String keyParam,
    String key,
    Map<String, String> params,
    boolean encodeParams
) {

    /**
     * 이 호출이 어느 인터페이스(operation)인지 나타내는 모드 설정용 이름 - endpoint의 마지막 경로 조각에서
     * 영숫자만 소문자로 남긴 값(예: {@code .../getUltraSrtNcst} → {@code getultrasrtncst},
     * {@code .../DSSP-IF-00247} → {@code dsspif00247}). 설정 키에 점·하이픈이 끼지 않게 하려는 정규화다.
     */
    public String operation() {
        String path = endpoint;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** 연결 설정 구분. */
    public enum Channel {
        /** 기본 RestTemplate(data.go.kr, 에어코리아, 안전데이터). */
        DEFAULT,
        /** 기상청 API허브 전용 RestTemplate(시도당 15초 read timeout). */
        API_HUB
    }
}
