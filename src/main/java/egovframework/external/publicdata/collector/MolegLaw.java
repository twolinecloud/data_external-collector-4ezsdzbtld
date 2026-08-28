package egovframework.external.publicdata.collector;

/**
 * 형사법령 목록의 법령 1건.
 *
 * <p>{@code lawId}(법령ID)는 그 법령이라는 개체를 관통하는 고정 식별자, {@code mst}
 * (법령일련번호)는 조회 시점에 시행 중이던 버전의 식별자다 - 법령이 개정되면 lawId는 그대로고
 * mst만 새로 발급된다 (실 API로 확인한 내용, private-doc 31번 항목 참고).</p>
 *
 * <p><b>실제 수집(2026-08-28)은 이 mst를 쓰지 않는다</b> - {@code target=eflaw}(법령명 기준
 * "현행법령" 조회)로 전환해서, 개정으로 mst가 바뀌어도 목록의 법령명만 정확하면 코드/설정
 * 변경 없이 최신 버전이 자동 반영된다({@code LawSourcePort} 참고). {@code mst}는 그 법령을
 * 대상 목록에 추가한 시점의 참고 메타데이터로만 남겨둔다.</p>
 */
public record MolegLaw(String lawId, String lawName, String mst, String lawType,
                        String promulgationDate, String effectiveDate, String ministry) {
}
