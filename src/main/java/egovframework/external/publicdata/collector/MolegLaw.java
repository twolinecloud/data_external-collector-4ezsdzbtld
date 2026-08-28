package egovframework.external.publicdata.collector;

/**
 * 법제처 수집 대상 목록의 1건 - 법령(LAW) 또는 행정규칙(ADMIN_RULE) 둘 다 이 레코드로 표현한다.
 *
 * <p>{@code lawId}(법령ID/행정규칙ID)는 그 문서라는 개체를 관통하는 고정 식별자, {@code mst}
 * (법령/행정규칙일련번호)는 조회 시점에 시행 중이던 버전의 식별자다 - 개정되면 lawId는 그대로고
 * mst만 새로 발급된다 (실 API로 확인한 내용, private-doc 31번 항목 참고).</p>
 *
 * <p><b>실제 수집(2026-08-28)은 이 mst를 쓰지 않는다</b> - {@code target=eflaw/admrul}(명칭
 * 기준 "현행" 조회)로 전환해서, 개정으로 mst가 바뀌어도 목록의 명칭만 정확하면 코드/설정
 * 변경 없이 최신 버전이 자동 반영된다({@code LawSourcePort} 참고). {@code mst}는 그 문서를
 * 대상 목록에 추가한 시점의 참고 메타데이터로만 남겨둔다.</p>
 *
 * <p><b>{@code docType}(2026-08-28 추가)</b>: {@link #DOC_TYPE_LAW}(법령) / {@link
 * #DOC_TYPE_ADMIN_RULE}(행정규칙) - {@code LawSourcePort}가 어느 API({@code target=eflaw} vs
 * {@code admrul})로 조회할지, {@code MolegLawCollectorFactory}가 어느 컬렉터를 만들지 이
 * 값으로 결정한다. {@code lawType}은 그 안의 세부 종류(법령: 법률/대통령령/부령, 행정규칙:
 * 훈령/예규/고시/지침/규칙 등)로 docType과 별개 축이다 - 기존 60건(csv 소스 대상)은 docType
 * 컬럼이 없던 시절 데이터라 {@link #docTypeOrDefault()}가 null/공백을 LAW로 취급한다(admin-db
 * 소스는 아직 doc_type_cd 컬럼이 없어 항상 null - private-doc 참고, DB 스키마 반영 전까지는
 * db 소스로 전환 시 전부 LAW로만 동작함).</p>
 */
public record MolegLaw(String lawId, String lawName, String mst, String lawType,
                        String promulgationDate, String effectiveDate, String ministry,
                        String docType) {

    public static final String DOC_TYPE_LAW = "LAW";
    public static final String DOC_TYPE_ADMIN_RULE = "ADMIN_RULE";

    /** docType 컬럼이 없던 기존 데이터(csv 60건/db 소스) 하위호환용 - null/공백이면 LAW로 취급. */
    public String docTypeOrDefault() {
        return (docType == null || docType.isBlank()) ? DOC_TYPE_LAW : docType;
    }
}
