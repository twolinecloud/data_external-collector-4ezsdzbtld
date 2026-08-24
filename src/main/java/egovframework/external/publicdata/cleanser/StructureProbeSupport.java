package egovframework.external.publicdata.cleanser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/** {@link StructureProbe}의 observer 구현을 짧게 만들기 위한 package-private 공유 헬퍼. */
final class StructureProbeSupport {

    private StructureProbeSupport() {
    }

    /** 최상위 배열의 각 원소(JSONObject)에서 키 전체의 합집합을 구함. */
    static Set<String> unionKeys(JSONArray items) {
        return unionKeys(items, o -> true);
    }

    /** filter를 통과하는 원소만 대상으로 키 합집합을 구함 (예: 조문여부="조문"인 것만). */
    static Set<String> unionKeys(JSONArray items, Predicate<JSONObject> filter) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (filter.test(item)) {
                keys.addAll(item.keySet());
            }
        }
        return keys;
    }
}
