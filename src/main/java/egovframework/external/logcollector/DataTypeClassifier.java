package egovframework.external.logcollector;

import java.util.Set;

/**
 * operationKey -> C01 공통코드(DATA_TYPE_CD) 매핑.
 *
 * <p>플랫폼 쪽 {@code tb_comm_code}에서 EXTERNAL 1종이 EXTERNAL_PUBLIC(공공 연계 데이터)/
 * EXTERNAL_LAW(법제처 법령정보) 2종으로 분리됨(2026-08-27 실측 확인, 기존 EXTERNAL은
 * use_yn='N'으로 비활성화). {@code moleg-criminal-law}(법령)/{@code moleg-admin-rule}
 * (행정규칙, 2026-08-28 추가)만 법령이고 나머지(KMA 동네예보/기상특보/생활기상지수,
 * safetydata 긴급재난문자)는 전부 공공데이터 - 새 operationKey가 추가되면 기본값이
 * EXTERNAL_PUBLIC이 되도록 "법령 목록"만 관리한다(반대로 "공공 목록"을 관리하면 새 오퍼레이션
 * 추가 시 깜빡하고 안 넣었을 때 조용히 누락되는 위험이 있음).</p>
 */
public final class DataTypeClassifier {

    public static final String EXTERNAL_PUBLIC = "EXTERNAL_PUBLIC";
    public static final String EXTERNAL_LAW = "EXTERNAL_LAW";

    private static final Set<String> LAW_OPERATION_KEYS = Set.of("moleg-criminal-law", "moleg-admin-rule");

    private DataTypeClassifier() {
    }

    /** Cleanse/Load처럼 오퍼레이션 구분 없이 raw_staging을 훑는 단계에서 필터링용으로 쓴다. */
    public static Set<String> lawOperationKeys() {
        return LAW_OPERATION_KEYS;
    }

    public static boolean isLaw(String operationKey) {
        return LAW_OPERATION_KEYS.contains(operationKey);
    }

    /** Collect는 operationKey가 배치 1개에 1:1 대응이라 이걸로 바로 결정 가능. */
    public static String dataTypeCd(String operationKey) {
        return isLaw(operationKey) ? EXTERNAL_LAW : EXTERNAL_PUBLIC;
    }
}
