package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 지상 종관기상관측(ASOS) 시간자료 - 2026-09-03 신규. 날씨 기호 요구사항의 안개/박무/연무용
 * 시정·습도를 가져온다.
 *
 * <p>{@code stn=0}으로 <b>전국 지점을 한 번에</b> 받으므로 지역별 인스턴스를 만들지 않는다
 * ({@link KmaLocationCollectorFactory}가 59개소만큼 컬렉터를 찍어내는 것과 대조적) - 빈 하나면
 * 충분하고 시간당 호출도 1회다. 지점↔교정기관 매칭은 정제 단계가 한다({@code KmaWeatherWarningListCleanser}가
 * stnId를 시설로 펼치는 것과 같은 패턴).</p>
 *
 * <p><b>안개를 현상코드로 읽을 수 없다(실측, 2026-09-03)</b>: 응답에 국내식 일기코드(WW)와
 * GTS 현재/과거일기(WC/WP)가 있고 과거일기 코드에는 황사·안개가 정의돼 있지만, 이 값들은
 * 일기 포함여부(IX)가 1인 지점에만 채워진다. 9/1 06시·07시 실측에서 전국 97개 지점 중
 * <b>9개 지점</b>만 해당했다(나머지는 생략 또는 결측). 교정기관 59개소를 9개 지점으로 덮을 수
 * 없으므로, 거의 전 지점에 들어오는 <b>시정(VS, 96/97)과 습도(HM, 97/97)로 유도</b>한다.
 * 판정 기준값은 기획 확정 대기 - 통상 안개는 시정 1km 미만, 박무는 1~10km에 습도가 높을 때,
 * 연무는 같은 시정에 습도가 낮을 때로 본다. 유도값은 관측된 사실이 아니라는 점을 화면에서도
 * 구분해야 한다.</p>
 *
 * <p>수집 시점에 필드를 고르지 않고 46개 전부를 raw_payload에 담는다 - raw_staging은 원본
 * 보존이 목적이라, 나중에 화면 요구가 늘어도 재수집 없이 대응하기 위함(task-spec.md 29번
 * 항목의 단기예보 결정과 같은 원칙).</p>
 */
@Component
public class KmaAsosHourlyCollector implements PublicDataCollector {

    private static final DateTimeFormatter TM_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH00");

    /**
     * 응답 컬럼 순서대로의 필드명 - API가 {@code help=1}로 알려주는 이름을 그대로 쓴다.
     * 순서와 개수가 응답과 정확히 일치해야 하며, 어긋나면 {@link KmaApiHubClient}가 수집을
     * 실패시킨다(조용히 밀린 값이 들어가는 것보다 낫다).
     *
     * <p>이 중 화면 요구와 직접 닿는 것은 {@code VS}(시정, 10m 단위)와 {@code HM}(상대습도, %),
     * 그리고 유도 결과를 검증할 때 참고할 {@code WW}/{@code WC}/{@code WP}(일기)와
     * {@code IX}(일기 포함여부)다.</p>
     *
     * <p>{@code public}인 이유 - {@code KmaAsosHourlyCleanser}의 구조 드리프트 감지가 이 목록을
     * 그대로 참조한다({@code KmaWeatherWarningListCleanser}처럼 필드 목록을 정제기 쪽에 따로
     * 두면 수집기가 필드를 늘려도 정제기가 몰라 드리프트 감지가 새 필드를 못 잡는다).</p>
     */
    public static final List<String> FIELD_NAMES = List.of(
        "TM", "STN", "WD", "WS", "GST_WD", "GST_WS", "GST_TM", "PA", "PS", "PT", "PR",
        "TA", "TD", "HM", "PV", "RN", "RN_DAY", "RN_JUN", "RN_INT", "SD_HR3", "SD_DAY", "SD_TOT",
        "WC", "WP", "WW", "CA_TOT", "CA_MID", "CH_MIN", "CT", "CT_TOP", "CT_MID", "CT_LOW",
        "VS", "SS", "SI", "ST_GD", "TS", "TE_005", "TE_01", "TE_02", "TE_03", "ST_SEA",
        "WH", "BF", "IR", "IX");

    private final KmaApiHubClient apiClient;
    private final String endpoint;
    private final String authKey;

    public KmaAsosHourlyCollector(
        KmaApiHubClient apiClient,
        @Value("${public-data.kma.asos-hourly.endpoint}") String endpoint,
        @Value("${public-data.kma.asos-hourly.auth-key:}") String authKey
    ) {
        this.apiClient = apiClient;
        this.endpoint = endpoint;
        this.authKey = authKey;
    }

    @Override
    public String key() {
        return "kma-asos-hourly";
    }

    @Override
    public String sourceName() {
        return "기상청 API허브 (지상 종관기상관측)";
    }

    @Override
    public String apiName() {
        return "지상관측 시간자료";
    }

    @Override
    public List<String> collect() throws CollectException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tm", targetHour().format(TM_FMT));
        params.put("stn", "0"); // 0 = 전 지점

        return apiClient.call(sourceName(), apiName(), endpoint, authKey, params, FIELD_NAMES);
    }

    /**
     * 직전 정시를 조회한다. 실측(2026-09-03 13:50)에서 당시 정시(13시) 자료가 이미 있었지만,
     * 스케줄이 정시 직후에 돌면 아직 안 올라와 빈 응답을 받을 수 있어 한 시간 뒤로 잡는다.
     * 놓친 시각은 다음 틱이 아니라 영영 비므로, 여유를 두는 쪽이 안전하다.
     *
     * <p>Main.java의 JVM 기본 타임존이 Asia/Seoul이라 {@code now()}가 곧 KST다 - API의
     * {@code tm}도 KST 기준.</p>
     */
    private LocalDateTime targetHour() {
        return LocalDateTime.now().minusHours(1);
    }

    /**
     * 기상값은 날짜 기준으로 하루 전까지 유효하다 - 오늘이 9/2면 9/1 00:00 이후 수집분까지는
     * 적재에 실패해도 재시도 대기로 들고 있어야 하고, 8/31 이하는 이미 지난 값이라 폐기해도 된다
     * (2026-09-02 사용자 확인). 그래서 수집일 D의 행은 D가 "그저께"가 되는 순간인
     * D+2일 0시에 만료된다 - {@link PublicDataCollector#stagingExpiresAt(LocalDate)} 참고.
     */
    @Override
    public LocalDateTime stagingExpiresAt(LocalDate collectedOn) {
        return collectedOn.plusDays(2).atStartOfDay();
    }
}
