package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 기상특보목록(getWthrWrnList) 정제기. 응답 자체가 이미 "특보 발표문 1건 = 1행"인
 * 넓은 형태라서 카테고리 피벗이 필요 없음 - 필수 필드(title) 존재 검증 정도만 수행.
 */
@Component
public class KmaWeatherWarningListCleanser implements PublicDataCleanser {

    @Override
    public boolean supports(String operationKey) {
        return "kma-weather-warning-list".equals(operationKey);
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                if (!item.has("title")) {
                    throw new IllegalStateException("특보 항목에 title 필드 없음: " + item);
                }
            }
            return rawItems.toString();
        } catch (Exception e) {
            throw new CleanseException("공공데이터포털 (기상청 기상특보)", "기상특보목록조회", "정제 실패: " + e.getMessage(), e);
        }
    }
}
