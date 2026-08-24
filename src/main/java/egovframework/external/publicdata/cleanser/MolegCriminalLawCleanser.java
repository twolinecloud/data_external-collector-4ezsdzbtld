package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 법제처 형사법령 본문조회(moleg-criminal-law) 정제기.
 *
 * <p>{@code MolegCriminalLawCollector.collect()}는 law.go.kr 응답 원문(법령 1건) 하나를
 * 리스트에 담아 반환하므로, raw_staging의 rawPayload는 그 원문 객체 하나짜리 배열이다 -
 * 기상특보목록과 마찬가지로 이미 "정제 대상 1건 = 배열 원소 1개"인 넓은 형태라 카테고리
 * 피벗이 필요 없다. {@code DirectLawSourceAdapter}가 이미 성공 응답만 통과시키므로
 * (private-doc 31번 항목 - 실패는 "Law" 키로 구분해 CollectException으로 걸러짐) 여기서는
 * {@code "법령"} 키 존재만 재검증하고 원문을 그대로 통과시킨다.</p>
 *
 * <p><b>조문 단위 변경감지/해시비교/이력누적(private-doc 31번 항목,
 * {@code cleanse-db-schema-spec.md} 3장)은 아직 구현 전</b> - admin-db 스키마가 확정돼야
 * 어느 단계(Cleanse/Load)에 붙일지 정해지므로 그때까지는 이 최소 통과 구현으로 둔다. 이게
 * 없으면 raw_staging 행이 "정제기 없음"으로 영원히 CLEANSE_FAILED에 머물러, 법령 데이터가
 * 원문조차 확인 불가능한 상태가 되는 걸 막기 위한 최소 구현.</p>
 */
@Component
public class MolegCriminalLawCleanser implements PublicDataCleanser {

    /** {@code 법령} 직속 키 전체 (2026-08-14 형법/형사소송법 실측, 6개 - 선택 필드 없음). */
    private static final Set<String> LAW_FIELDS =
        Set.of("개정문", "조문", "제개정이유", "법령키", "기본정보", "부칙");

    /** {@code 조문단위} 원소가 조문/전문 어느 쪽이든 항상 갖는 필드 (2026-08-14 전수확인, 100%). */
    private static final Set<String> ARTICLE_UNIT_COMMON_FIELDS = Set.of(
        "조문내용", "조문번호", "조문변경여부", "조문시행일자", "조문여부", "조문이동이전", "조문이동이후", "조문키");

    /** 조문여부="조문"인 원소가 추가로 가질 수 있는 선택 필드 (실측: 조문가지번호 7~19%, 조문제목 98~99%, 조문참고자료 36~38%, 항 36~52%). */
    private static final Set<String> JOMUN_KNOWN_FIELDS =
        union(ARTICLE_UNIT_COMMON_FIELDS, Set.of("조문가지번호", "조문제목", "조문참고자료", "항"));

    /** 조문여부="전문"인 원소가 추가로 가질 수 있는 선택 필드 (실측: 조문가지번호가 드물게 붙는 경우 있음). */
    private static final Set<String> JEONMUN_KNOWN_FIELDS =
        union(ARTICLE_UNIT_COMMON_FIELDS, Set.of("조문가지번호"));

    @Override
    public boolean supports(String operationKey) {
        return "moleg-criminal-law".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(
            new StructureProbe("법령", LAW_FIELDS, LAW_FIELDS, MolegCriminalLawCleanser::observeLawFields),
            new StructureProbe("조문단위:조문", JOMUN_KNOWN_FIELDS, ARTICLE_UNIT_COMMON_FIELDS,
                items -> observeArticleUnitFields(items, "조문")),
            new StructureProbe("조문단위:전문", JEONMUN_KNOWN_FIELDS, ARTICLE_UNIT_COMMON_FIELDS,
                items -> observeArticleUnitFields(items, "전문"))
        );
    }

    private static Set<String> observeLawFields(JSONArray rawItems) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < rawItems.length(); i++) {
            keys.addAll(rawItems.getJSONObject(i).getJSONObject("법령").keySet());
        }
        return keys;
    }

    private static Set<String> observeArticleUnitFields(JSONArray rawItems, String articleTypeFilter) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < rawItems.length(); i++) {
            JSONObject law = rawItems.getJSONObject(i).getJSONObject("법령");
            if (!law.has("조문")) {
                continue;
            }
            JSONArray units = law.getJSONObject("조문").getJSONArray("조문단위");
            keys.addAll(StructureProbeSupport.unionKeys(units, u -> articleTypeFilter.equals(u.optString("조문여부", ""))));
        }
        return keys;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new LinkedHashSet<>(a);
        result.addAll(b);
        return result;
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                if (!item.has("법령")) {
                    throw new IllegalStateException("법령 본문에 '법령' 키 없음: " + item);
                }
            }
            return rawItems.toString();
        } catch (Exception e) {
            throw new CleanseException("국가법령정보센터 (법제처)", "법령 본문조회", "정제 실패: " + e.getMessage(), e);
        }
    }
}
