package egovframework.external.publicdata.cleanser;

import org.json.JSONObject;

/**
 * 기상청 공식 체감온도 계산식 - 정제 단계에서 이미 피벗된 행(JSONObject)에 {@code senstemp}
 * 필드를 계산해 채워 넣는 헬퍼.
 *
 * <p><b>별도 API 의존 없음</b> - 2026-08-24 조사 결과, 공공데이터포털 "기상청 생활기상지수
 * 조회서비스"가 4.0으로 개편되면서 체감온도(대상/환경별) 오퍼레이션 자체가 빠졌고 자외선지수/
 * 대기정체지수만 남음(data.go.kr Swagger 직접 열람으로 실측 확인 - API 목록에
 * getUVIdxV5/getAirDiffusionIdxV5 둘뿐). 대신 기상청이 공개한 공식 계산식을 우리가 이미
 * 수집 중인 기온/습도/풍속으로 직접 적용한다 - 별도 API 승인 대기 없이 바로 반영 가능.</p>
 *
 * <p>여름철(5~9월)과 겨울철(10~익년4월) 공식이 다르고 서로 배타적 - 대상 시각(관측이면
 * base_dtm, 예보면 fcst_dtm)의 "월"로 가른다. 여기서는 날짜 문자열(yyyyMMdd)의 5~6번째
 * 자리만 보는 최소 파싱으로 처리한다 - {@code LocalDateTime} 전체 파싱(그리고
 * {@code egovframework.external.publicdata.loader.KmaDateTimeSupport}와의 패키지 간 결합)이
 * 필요 없다. 시스템 현재 시각({@code now()})은 전혀 쓰지 않는다 - 미래 예보(fcst_dtm)를 다루는
 * 코드에서 "지금이 몇 월인지"는 무의미하고 대상 시각 자체의 월을 봐야 한다.</p>
 *
 * <p>출처: 기상자료개방포털 응용기상분석:체감온도(data.kma.go.kr) 공식 발표 계산식.</p>
 */
final class ApparentTemperatureCalculator {

    private ApparentTemperatureCalculator() {
    }

    /**
     * {@code row}에서 {@code tempField}/{@code humidityField}/{@code windField} 값을 읽어
     * 체감온도를 계산하고 {@code "senstemp"} 키로 채운다. 필요한 입력이 없거나(null) 겨울철
     * 산출조건(기온 10도 이하 &amp; 풍속 1.3m/s 이상)을 만족 못 하면 {@code senstemp}는
     * {@code JSONObject.NULL}로 남는다(기상청 공식 자체가 그 범위 밖에서는 산출 안 함 - 그
     * 경우 원 기온을 그대로 체감온도로 봐도 무방하다는 게 관례라, 소비 측에서 null이면
     * 기온 필드로 대체해서 쓰는 걸 권장).
     *
     * @param targetDateYyyyMMdd 대상 시각의 날짜(yyyyMMdd) - baseDate 또는 fcstDate
     */
    static void enrich(JSONObject row, String targetDateYyyyMMdd,
                        String tempField, String humidityField, String windField) {
        Double temperatureC = readDouble(row, tempField);
        Double humidityPct = readDouble(row, humidityField);
        Double windSpeedMs = readDouble(row, windField);

        Double sensTemp = calculate(targetDateYyyyMMdd, temperatureC, humidityPct, windSpeedMs);
        row.put("senstemp", sensTemp == null ? JSONObject.NULL : round1(sensTemp));
    }

    private static Double calculate(String targetDateYyyyMMdd, Double temperatureC, Double humidityPct, Double windSpeedMs) {
        if (temperatureC == null) {
            return null;
        }
        int month = Integer.parseInt(targetDateYyyyMMdd.substring(4, 6));
        boolean summer = month >= 5 && month <= 9;
        return summer ? summer(temperatureC, humidityPct) : winter(temperatureC, windSpeedMs);
    }

    /** 여름철(5~9월) - 습구온도(Stull 근사식)를 경유해서 계산. */
    private static Double summer(double ta, Double rhPct) {
        if (rhPct == null) {
            return null;
        }
        double rh = rhPct;
        double tw = ta * Math.atan(0.151977 * Math.sqrt(rh + 8.313659))
            + Math.atan(ta + rh) - Math.atan(rh - 1.676331)
            + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh)
            - 4.686035;
        return -0.2442 + 0.55399 * tw + 0.45535 * ta - 0.0022 * tw * tw + 0.00278 * tw * ta + 3.0;
    }

    /** 겨울철(10~익년4월) - 기온 10도 이하 &amp; 풍속 1.3m/s 이상일 때만 산출(기상청 공식 조건). */
    private static Double winter(double ta, Double windSpeedMs) {
        if (windSpeedMs == null || ta > 10.0 || windSpeedMs < 1.3) {
            return null;
        }
        double vKmh = windSpeedMs * 3.6;
        double v016 = Math.pow(vKmh, 0.16);
        return 13.12 + 0.6215 * ta - 11.37 * v016 + 0.3965 * v016 * ta;
    }

    private static Double readDouble(JSONObject row, String field) {
        if (!row.has(field) || row.isNull(field)) {
            return null;
        }
        try {
            return Double.parseDouble(row.get(field).toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
