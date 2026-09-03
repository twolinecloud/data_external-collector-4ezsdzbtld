package egovframework.external.publicdata.collector;

import egovframework.external.exception.CollectException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 기상청 API 허브(apihub.kma.go.kr) 호출 로직. 공공데이터포털(data.go.kr)을 쓰는
 * {@link KmaApiClient}와 별개로 둔다 - 같은 기상청이지만 포털이 다르고, 인증 파라미터
 * ({@code authKey})와 응답 형식이 전혀 다르기 때문.
 *
 * <p><b>왜 포털을 나눠 쓰나(2026-09-03)</b>: 지상관측(ASOS) 시간자료는 두 포털 모두 제공하지만,
 * data.go.kr 쪽은 {@code stnIds}가 필수라 지점을 하나씩 불러야 한다({@code stnIds=0}은
 * NO_DATA, 생략하면 필수파라미터 오류 - 실측 확인). 전국 97개 지점이면 시간당 97회가 되어
 * 현재 하루 호출량(약 3,448회)을 두 배 가까이 늘린다. API 허브는 {@code stn=0} 한 번으로
 * 전 지점을 주므로 시간당 1회면 된다. 서비스키 승인 쿼터가 아직 확인되지 않은 상태라
 * (task-spec.md 23/24번 항목) 호출량 차이를 우선했다.</p>
 *
 * <p><b>응답 형식</b>: JSON이 아니라 <b>CP949(EUC-KR) 고정폭 텍스트</b>다. {@code #}으로 시작하는
 * 주석/헤더 줄과 {@code #7777END} 종료 표시를 걷어내고, 공백으로 구분된 데이터 줄만 남긴다.
 * 값 자체에는 공백이 없어 단순 split으로 안전하게 갈린다. 파이프라인 나머지 단계는 raw_payload가
 * JSON 배열이라고 가정하므로({@code RawStagingDto#rawPayload}) 여기서 줄 하나를 JSON 객체
 * 하나로 바꿔서 돌려준다 - 필드명은 API가 {@code help=1}로 알려주는 이름을 그대로 쓴다.</p>
 *
 * <p><b>결측</b>: 숫자 결측을 {@code -9 / -9.0 / -9.00 / -99.0}으로, 문자 결측을 {@code -}로
 * 표기한다. 여기서는 원본 보존이 목적이라 그대로 담아두고, 결측 판정은 정제 단계가 한다.</p>
 */
@Component
public class KmaApiHubClient {

    /** 응답 본문 인코딩. API 허브 typ01 계열은 UTF-8이 아니라 CP949로 내려준다. */
    private static final Charset RESPONSE_CHARSET = Charset.forName("CP949");

    private final RestTemplate restTemplate;

    public KmaApiHubClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 고정폭 텍스트 응답을 받아 데이터 줄마다 JSON 객체 하나로 변환한다.
     *
     * @param fieldNames 컬럼 순서대로의 필드명. 데이터 줄의 컬럼 수와 정확히 같아야 한다 -
     *                   다르면 API 스펙이 바뀐 것이므로 {@link CollectException}으로 올린다
     *                   (조용히 어긋난 필드에 값을 넣는 것보다 수집 실패가 낫다).
     */
    public List<String> call(String sourceName, String apiName, String endpoint, String authKey,
            Map<String, String> params, List<String> fieldNames) throws CollectException {
        if (endpoint == null || endpoint.isBlank() || authKey == null || authKey.isBlank()) {
            throw new CollectException(sourceName, apiName, "엔드포인트/인증키 설정이 비어있음 (미확정)");
        }

        String body = fetch(sourceName, apiName, endpoint, authKey, params);
        return parse(sourceName, apiName, body, fieldNames);
    }

    private String fetch(String sourceName, String apiName, String endpoint, String authKey,
            Map<String, String> params) {
        StringBuilder url = new StringBuilder(endpoint).append('?').append("authKey=").append(authKey);
        params.forEach((key, value) -> url.append('&').append(key).append('=').append(value));

        try {
            URI uri = URI.create(url.toString());
            byte[] raw = restTemplate.getForObject(uri, byte[].class);
            if (raw == null) {
                throw new CollectException(sourceName, apiName, "API 호출 실패: 빈 응답");
            }
            return new String(raw, RESPONSE_CHARSET);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new CollectException(sourceName, apiName, "API 호출 실패: " + e.getMessage(), e);
        }
    }

    private List<String> parse(String sourceName, String apiName, String body, List<String> fieldNames) {
        // 활용신청이 안 된 API는 200 OK에 JSON 오류 본문을 실어 보낸다(고정폭 텍스트가 아님).
        // 그냥 두면 데이터 줄이 0건이라 "수집 성공 0건"으로 조용히 넘어가므로 여기서 잡아낸다.
        String head = body.stripLeading();
        if (head.startsWith("{")) {
            throw new CollectException(sourceName, apiName, "API 허브 오류 응답: " + summarize(head));
        }

        List<String> items = new ArrayList<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue; // 주석/헤더/#START7777/#7777END
            }

            String[] values = trimmed.split("\\s+");
            if (values.length != fieldNames.size()) {
                throw new CollectException(sourceName, apiName,
                    "응답 컬럼 수가 예상과 다름 - 기대 " + fieldNames.size() + "개, 실제 " + values.length
                        + "개 (API 스펙 변경 의심)");
            }

            JSONObject item = new JSONObject();
            for (int i = 0; i < values.length; i++) {
                item.put(fieldNames.get(i), values[i]);
            }
            items.add(item.toString());
        }
        return items;
    }

    private String summarize(String json) {
        String oneLine = json.replaceAll("\\s+", " ").strip();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }
}
