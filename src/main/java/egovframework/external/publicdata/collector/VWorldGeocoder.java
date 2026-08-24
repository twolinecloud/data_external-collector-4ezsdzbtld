package egovframework.external.publicdata.collector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 국토교통부 공간정보오픈플랫폼(VWorld) 주소 지오코딩(Geocoder API 2.0, {@code getcoord}) -
 * 시설 신규 검토 큐 항목(도로명 주소)을 위경도로 변환하는 데 씀({@code FacilitySyncService}).
 *
 * <p><b>실측 확인(2026-08-24)</b>: {@code type=road}(도로명)로 서울지방교정청 주소를 호출하니
 * 정상 응답 - 다만 구글맵 대비 약 111m 오차(기존 terrain-rule-base-spec.md §6-1 기록과
 * 정확히 일치). {@code type=parcel}(지번)은 같은 주소로 NOT_FOUND - 지번 매칭은 안 되는
 * 케이스가 있어 도로명만 시도한다.</p>
 *
 * <p><b>중요한 한계</b>: {@code tb_dim_instt.dtladr}(교정기관 상세주소)의 상당수(64건 중
 * 40건 이상, 실측)가 번지수를 {@code 000}/{@code 0000}으로 마스킹해뒀다(보안 목적으로 추정) -
 * 마스킹된 주소는 VWorld에서 100% NOT_FOUND로 확인됨(2026-08-24 실측). 그래서 자동 지오코딩이
 * 절반 이상 실패할 걸 전제로 설계함 - 실패 건은 검토 큐에 {@code NOT_FOUND} 상태로 남아
 * 사람이 직접 좌표를 조사해서 채워야 한다(기존 59개소 중 29개소를 구글맵으로 직접 검증했던
 * 것과 같은 패턴, terrain-rule-base-spec.md §6-1).</p>
 *
 * <p>실패해도 절대 예외를 던지지 않는다 - 시설 하나 지오코딩 실패가 나머지 동기화를 막으면
 * 안 되므로(fail-isolation 원칙, {@code DbMolegLawTargetSource}와 동일).</p>
 */
@Component
public class VWorldGeocoder {

    private static final Logger logger = LogManager.getLogger(VWorldGeocoder.class);

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String apiKey;

    public VWorldGeocoder(
        RestTemplate restTemplate,
        @Value("${public-data.vworld.endpoint:http://api.vworld.kr/req/address}") String endpoint,
        @Value("${public-data.vworld.api-key:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    public GeocodeResult geocode(String address) {
        if (endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()
            || address == null || address.isBlank()) {
            return GeocodeResult.failed();
        }

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(endpoint)
                .queryParam("service", "address")
                .queryParam("request", "getcoord")
                .queryParam("version", "2.0")
                .queryParam("crs", "epsg:4326")
                .queryParam("address", address)
                .queryParam("refine", "true")
                .queryParam("simple", "false")
                .queryParam("format", "json")
                .queryParam("type", "road")
                .queryParam("key", apiKey)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
            String responseBody = restTemplate.getForObject(uri, String.class);
            return parse(responseBody);
        } catch (Exception e) {
            logger.warn("[GEOCODE] VWorld 호출 실패: address={} error={}", address, e.getMessage());
            return GeocodeResult.failed();
        }
    }

    private GeocodeResult parse(String responseBody) {
        try {
            JSONObject response = new JSONObject(responseBody).getJSONObject("response");
            String status = response.optString("status", GeocodeResult.FAILED);
            if (GeocodeResult.NOT_FOUND.equals(status)) {
                return GeocodeResult.notFound();
            }
            if (!"OK".equals(status)) {
                logger.warn("[GEOCODE] VWorld 응답 status 비정상: {}", status);
                return GeocodeResult.failed();
            }
            JSONObject point = response.getJSONObject("result").getJSONObject("point");
            double lon = point.getDouble("x");
            double lat = point.getDouble("y");
            JSONObject structure = response.getJSONObject("refined").getJSONObject("structure");
            String sido = structure.optString("level1", null);
            String sigungu = structure.optString("level2", null);
            return new GeocodeResult(GeocodeResult.SUCCESS, lat, lon, sido, sigungu);
        } catch (Exception e) {
            logger.warn("[GEOCODE] VWorld 응답 파싱 실패: {}", e.getMessage());
            return GeocodeResult.failed();
        }
    }
}
