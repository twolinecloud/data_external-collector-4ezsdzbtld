package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 재난안전데이터공유플랫폼(safetydata.go.kr) - 행정안전부_긴급재난문자 목록조회
 * (엔드포인트 {@code DSSP-IF-00247}).
 *
 * <p>기상특보목록({@link KmaWeatherWarningListCollector})과 같은 성격 - 지역별 파라미터 없이
 * 전국 목록을 받아오고, 특정 지역(교정기관) 매칭은 정제/적재 단계에서 {@code RCPTN_RGN_NM}을
 * 보고 처리한다(위치기반 컬렉터가 아니므로 {@code facilityId()}는 기본값 null).</p>
 *
 * <p><b>2026-08-18 서비스키 발급 후 실호출로 확인한 사항</b> (그 전엔 전부 추정값이었음):</p>
 * <ul>
 *   <li>응답 봉투가 공공데이터포털과 다르다 - {@code response} 래퍼가 없고 {@code body}가
 *       곧바로 배열이다. {@link SafetyDataResponseParser} 참고.</li>
 *   <li><b>{@code crtDt}(생성일자, yyyyMMdd) 필터가 필수다.</b> 이 파라미터가 없으면 정렬이
 *       SN 오름차순이라 {@code pageNo=1}이 <b>가장 오래된 2023-09-19 데이터</b>를 반환한다.
 *       10분 주기로 최신 재난문자를 받으려는 목적과 정반대라 반드시 당일로 걸러야 한다.</li>
 *   <li>{@code rgnNm}(지역명) 필터도 동작하지만 시도/시군구까지만 매칭된다 - 읍면동을 넣으면
 *       0건이다(예: {@code 경상북도 청송군}=430건, {@code 경상북도 청송군 진보면}=0건).
 *       교정기관 매칭을 서버측 필터로 처리할 수 없어 전국 수집 후 정제 단계에서 처리한다.</li>
 *   <li>{@code DST_SE_NM}에 rule-base가 쓸 값들이 실제로 들어온다 - 하루치 표본에서
 *       호우/산사태/홍수/폭염/풍랑/산불/교통사고 등이 확인됨.</li>
 * </ul>
 *
 * <p><b>알려진 한계:</b> 당일({@code crtDt}=오늘)만 조회하므로, 자정 직전에 생성됐지만
 * 자정 이후에야 API에 노출되는 문자는 놓칠 수 있다. 기상특보 컬렉터가 당일치만 받는 것과
 * 같은 방식이고, 재수집으로 메우려면 전일자까지 같이 조회해야 한다.</p>
 */
@Component
public class DisasterMsgCollector implements PublicDataCollector {

    private static final Logger logger = LogManager.getLogger(DisasterMsgCollector.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 한 번에 받아올 건수. 하루 발생량이 실측 100~150건 수준이라 2~3페이지면 충분. */
    private static final int ROWS_PER_PAGE = 100;
    /** 무한루프 방지 상한 ({@link KmaApiClient}와 동일한 방어). */
    private static final int MAX_PAGES = 10;

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String serviceKey;

    public DisasterMsgCollector(
        RestTemplate restTemplate,
        @Value("${public-data.safetydata.endpoint:https://www.safetydata.go.kr/V2/api/DSSP-IF-00247}") String endpoint,
        @Value("${public-data.safetydata.service-key:}") String serviceKey
    ) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
        this.serviceKey = serviceKey;
    }

    @Override
    public String key() {
        return "safetydata-disaster-msg-list";
    }

    @Override
    public String sourceName() {
        return "재난안전데이터공유플랫폼 (행정안전부)";
    }

    @Override
    public String apiName() {
        return "긴급재난문자 목록조회";
    }

    @Override
    public List<String> collect() throws CollectException {
        if (endpoint == null || endpoint.isBlank() || serviceKey == null || serviceKey.isBlank()) {
            throw new CollectException(sourceName(), apiName(), "엔드포인트/서비스키 설정이 비어있음 (미확정)");
        }

        // Main.java가 JVM 기본 타임존을 UTC로 고정해둬서(회사 스켈레톤 컨벤션) LocalDate.now()가
        // 실제 한국 날짜가 아니게 됨 - 자정~오전 9시 사이엔 하루 전 날짜로 조회될 수 있어 명시 필요.
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE_FMT);
        List<String> allItems = new ArrayList<>();
        int totalCount = Integer.MAX_VALUE; // 첫 페이지 파싱 전엔 모름
        int pageNo = 1;
        int pagesFetched = 0;

        while (allItems.size() < totalCount && pagesFetched < MAX_PAGES) {
            String responseBody = fetch(today, pageNo);
            SafetyDataResponseParser.ParsedPage page = parse(responseBody);

            allItems.addAll(page.items());
            totalCount = page.totalCount();
            pagesFetched++;
            pageNo++;

            if (page.items().isEmpty()) {
                // totalCount가 부정확해도 빈 페이지를 받으면 더 없다고 보고 중단
                break;
            }
        }

        if (allItems.size() < totalCount) {
            logger.warn("[{}] {} - {}페이지({}건)까지 받았지만 totalCount={}에 못 미쳐 나머지를 포기함 (MAX_PAGES 상한)",
                sourceName(), apiName(), pagesFetched, allItems.size(), totalCount);
        } else if (pagesFetched > 1) {
            logger.info("[{}] {} - 페이지네이션으로 {}페이지에 걸쳐 {}건 수집 (crtDt={}, totalCount={})",
                sourceName(), apiName(), pagesFetched, allItems.size(), today, totalCount);
        }

        return allItems;
    }

    private String fetch(String crtDt, int pageNo) {
        String url = endpoint
            + "?serviceKey=" + serviceKey
            + "&returnType=json"
            + "&crtDt=" + crtDt
            + "&pageNo=" + pageNo
            + "&numOfRows=" + ROWS_PER_PAGE;
        try {
            // serviceKey가 이미 URL-encode된 값일 수 있어 URI.create로 직접 만든다
            // (String 오버로드는 URI 템플릿으로 재인코딩해 키를 깨뜨림 - KmaApiClient와 동일한 이유)
            return restTemplate.getForObject(URI.create(url), String.class);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new CollectException(sourceName(), apiName(), "API 호출 실패: " + e.getMessage(), e);
        }
    }

    private SafetyDataResponseParser.ParsedPage parse(String responseBody) {
        try {
            return SafetyDataResponseParser.parse(responseBody);
        } catch (SafetyDataApiException e) {
            // HTTP 200이면서 header.resultCode로 실패를 알린 경우 - 파싱 실패와 구분
            throw new CollectException(sourceName(), apiName(), "safetydata API 오류 - " + e.getMessage(), e);
        } catch (Exception e) {
            throw new CollectException(sourceName(), apiName(), "응답 파싱 실패(형식 오류): " + e.getMessage(), e);
        }
    }
}
