package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import egovframework.external.publicdata.collector.FacilitySido;
import egovframework.external.publicdata.collector.FacilitySidoLoader;
import egovframework.external.publicdata.collector.LivingWthrIdxArea;
import egovframework.external.publicdata.collector.LivingWthrIdxAreaLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 생활기상지수(자외선지수/대기정체지수) 공통 정제기 - {@code kma-living-uv-idx}/
 * {@code kma-living-air-diffusion-idx} 둘 다 처리한다(요청/응답 구조가 offset 범위만
 * 다르고 완전히 동일해서 클래스 하나로 공유, {@code cleanse()}엔 operationKey가 안 넘어와서
 * 응답 안의 {@code h0} 필드 유무로 자동 구분 - UV는 h0 있음/h78 없음, 대기정체는 반대).
 *
 * <p>두 가지를 한 번에 한다: (1) {@code h0}~{@code h75}(또는 {@code h3}~{@code h78}) 넓은
 * 형태를 offset(3시간 단위)별 1행으로 펴서 다른 예보 테이블들의 (base_dtm, fcst_dtm) 패턴과
 * 맞춤. (2) {@code areaNo}(시도 단위)를 이 시도에 속한 교정기관 전부로 팬아웃 -
 * {@code KmaWeatherWarningListCleanser}와 같은 비정규화 원칙이나, 이쪽은 시도가 곧 지점이라
 * (기상특보처럼 관할구역 목록이 아니라 1:1) 매칭이 더 단순하다.</p>
 */
@Component
public class KmaLivingWthrIdxCleanser implements PublicDataCleanser {

    private static final Logger logger = LogManager.getLogger(KmaLivingWthrIdxCleanser.class);

    /** UV(h0~h75)와 대기정체(h3~h78) 모두 3시간 간격 26개 offset - 값 목록만 다름. */
    private static final int[] UV_OFFSETS = {0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39, 42, 45, 48, 51, 54, 57, 60, 63, 66, 69, 72, 75};
    private static final int[] AIR_OFFSETS = {3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39, 42, 45, 48, 51, 54, 57, 60, 63, 66, 69, 72, 75, 78};

    private final List<LivingWthrIdxArea> areas;
    private final List<FacilitySido> facilities;

    public KmaLivingWthrIdxCleanser(LivingWthrIdxAreaLoader areaLoader, FacilitySidoLoader facilitySidoLoader) {
        this.areas = areaLoader.all();
        this.facilities = facilitySidoLoader.all();
    }

    @Override
    public boolean supports(String operationKey) {
        return "kma-living-uv-idx".equals(operationKey) || "kma-living-air-diffusion-idx".equals(operationKey);
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            JSONArray result = new JSONArray();
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                appendMatches(item, result);
            }
            return result.toString();
        } catch (Exception e) {
            throw new CleanseException("공공데이터포털 (기상청 생활기상지수)", "생활기상지수조회", "정제 실패: " + e.getMessage(), e);
        }
    }

    private void appendMatches(JSONObject item, JSONArray result) {
        String areaNo = item.getString("areaNo");
        String date = item.getString("date");
        String code = item.optString("code", null);
        boolean isUv = item.has("h0");
        int[] offsets = isUv ? UV_OFFSETS : AIR_OFFSETS;

        Optional<LivingWthrIdxArea> area = areas.stream().filter(a -> a.areaNo().equals(areaNo)).findFirst();
        if (area.isEmpty()) {
            // 우리가 아는 16개 시도 코드표 밖의 값 - 기상청이 표를 바꿨거나 우리가 놓친 코드일 수 있음
            logger.warn("[CLEANSE] 알 수 없는 생활기상지수 지점코드(areaNo={}) - 시설 매칭 없이 건너뜀: {}", areaNo, item);
            return;
        }

        for (FacilitySido facility : facilities) {
            if (!facility.sido().equals(area.get().sido())) {
                continue;
            }
            for (int offsetHours : offsets) {
                String field = "h" + offsetHours;
                String value = item.optString(field, "");
                if (value.isBlank()) {
                    continue; // 예측기간(+78h)을 아직 못 채운 먼 미래 offset - 값 없이 건너뜀
                }
                JSONObject row = new JSONObject();
                row.put("areaNo", areaNo);
                row.put("date", date);
                row.put("code", code == null ? JSONObject.NULL : code);
                row.put("offsetHours", offsetHours);
                row.put("value", value);
                row.put("facilityId", facility.facilityId());
                result.put(row);
            }
        }
    }
}
