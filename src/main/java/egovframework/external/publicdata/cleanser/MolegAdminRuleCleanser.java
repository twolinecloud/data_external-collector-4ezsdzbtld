package egovframework.external.publicdata.cleanser;

import egovframework.external.exception.CleanseException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 법제처 행정규칙 본문조회(moleg-admin-rule) 정제기(2026-08-28 추가).
 *
 * <p>{@code MolegCriminalLawCleanser}와 같은 이유로 최소 통과 구현이다 - raw_staging의
 * rawPayload는 {@code MolegAdminRuleCollector.collect()}가 반환한 원문 1건짜리 배열이라
 * 카테고리 피벗이 필요 없다. 다만 봉투가 법령과 달라({@code "법령"} 대신 {@code "AdmRulService"})
 * 별도 클래스로 분리했다 - {@code DirectLawSourceAdapter}가 이미 성공 응답만 통과시키므로
 * 여기서는 {@code "AdmRulService"} 키 존재만 재검증하고 원문을 그대로 통과시킨다.</p>
 *
 * <p>조문/별표 단위 변경감지·이력누적은 법령과 마찬가지로 admin-db 스키마 확정 후로 미룬다.
 * 조문내용/별표/첨부파일 등 내부 필드까지의 구조 프로브는 아직 없음 - 최상위 봉투 키만
 * 실측 확인됐고(2026-08-28), 내부 구조는 법령만큼 전수 검증되지 않아 우선 최상위 1단만
 * 선언한다.</p>
 */
@Component
public class MolegAdminRuleCleanser implements PublicDataCleanser {

    /** {@code AdmRulService} 직속 키 전체 (2026-08-28 실측 - 법령과 다른 봉투). */
    private static final Set<String> ADMIN_RULE_FIELDS =
        Set.of("개정문", "별표", "행정규칙기본정보", "조문내용", "첨부파일", "부칙", "제개정이유");

    @Override
    public boolean supports(String operationKey) {
        return "moleg-admin-rule".equals(operationKey);
    }

    @Override
    public List<StructureProbe> structureProbes() {
        return List.of(
            new StructureProbe("AdmRulService", ADMIN_RULE_FIELDS, ADMIN_RULE_FIELDS,
                MolegAdminRuleCleanser::observeAdminRuleFields)
        );
    }

    private static Set<String> observeAdminRuleFields(JSONArray rawItems) {
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < rawItems.length(); i++) {
            keys.addAll(rawItems.getJSONObject(i).getJSONObject("AdmRulService").keySet());
        }
        return keys;
    }

    @Override
    public String cleanse(String rawPayload) throws CleanseException {
        try {
            JSONArray rawItems = new JSONArray(rawPayload);
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject item = rawItems.getJSONObject(i);
                if (!item.has("AdmRulService")) {
                    throw new IllegalStateException("행정규칙 본문에 'AdmRulService' 키 없음: " + item);
                }
            }
            return rawItems.toString();
        } catch (Exception e) {
            throw new CleanseException("국가법령정보센터 (법제처)", "행정규칙 본문조회", "정제 실패: " + e.getMessage(), e);
        }
    }
}
