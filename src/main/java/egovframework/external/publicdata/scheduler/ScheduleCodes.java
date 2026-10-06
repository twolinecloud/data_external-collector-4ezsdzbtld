package egovframework.external.publicdata.scheduler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 오퍼레이션 키(operationKey) ↔ 관리자 스케줄 코드(SCHD_CD) 매핑.
 *
 * <p>관리자 서비스는 스케줄 한 행을 {@code (DATA_TYPE_CD, SCHD_CD)}로 식별한다. SCHD_CD는
 * {@code ^[A-Z][A-Z0-9_]*$} 형식에 최대 30자라서, 소문자 하이픈 형식에 35자까지 되는 operationKey를
 * 기계적으로 바꿔 쓸 수 없다 - 그래서 오퍼레이션마다 짧은 코드를 명시적으로 정한다.
 *
 * <p><b>SCHD_CD는 관리자 DB 행의 키이므로 한 번 정하면 바꾸지 않는다.</b> 새 오퍼레이션을 스케줄에 추가할 때는
 * {@link ScheduledOperations}에 등록하고 여기에도 코드를 추가한다(코드가 없는 오퍼레이션은 데이터 구분 대표 행으로만
 * 켜고 끌 수 있다).
 */
public final class ScheduleCodes {

    private static final Map<String, String> SCHD_CD_BY_OPERATION;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("kma-village-forecast-ultra-srt-ncst", "KMA_SRT_NCST");
        m.put("kma-village-forecast-ultra-srt-fcst", "KMA_SRT_FCST");
        m.put("kma-village-forecast-vilage-fcst", "KMA_VILAGE_FCST");
        m.put("kma-weather-warning-list", "KMA_WRN_LIST");
        m.put("kma-asos-hourly", "KMA_ASOS_HOURLY");
        m.put("airkorea-realtime-measure", "AIRKOREA_REALTIME");
        m.put("airkorea-dust-forecast", "AIRKOREA_DUST_FCST");
        m.put("moleg-criminal-law", "MOLEG_LAW");
        m.put("safetydata-disaster-msg-list", "SAFETY_DISASTER_MSG");
        m.put("kma-living-uv-idx", "KMA_LIVING_UV");
        m.put("kma-living-air-diffusion-idx", "KMA_LIVING_AIR_DIFF");
        SCHD_CD_BY_OPERATION = Collections.unmodifiableMap(m);
    }

    private ScheduleCodes() {
    }

    /** @return operationKey에 정해진 SCHD_CD. 정해지지 않았으면 null */
    public static String schdCdOf(String operationKey) {
        return SCHD_CD_BY_OPERATION.get(operationKey);
    }

    /** @return SCHD_CD에 해당하는 operationKey. 모르는 코드면 비어 있다 */
    public static Optional<String> operationOf(String schdCd) {
        if (schdCd == null) {
            return Optional.empty();
        }
        return SCHD_CD_BY_OPERATION.entrySet().stream()
            .filter(e -> e.getValue().equals(schdCd))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    /**
     * 경로 변수로 받은 값을 operationKey로 해석한다. operationKey 그대로("kma-asos-hourly")나
     * SCHD_CD("KMA_ASOS_HOURLY") 어느 쪽으로 불러도 같은 오퍼레이션을 가리킨다.
     */
    public static Optional<String> resolveOperation(String keyOrSchdCd) {
        if (keyOrSchdCd == null || keyOrSchdCd.isBlank()) {
            return Optional.empty();
        }
        String trimmed = keyOrSchdCd.trim();
        if (SCHD_CD_BY_OPERATION.containsKey(trimmed)) {
            return Optional.of(trimmed);
        }
        return operationOf(trimmed.toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public static Map<String, String> all() {
        return SCHD_CD_BY_OPERATION;
    }
}
