package egovframework.external.publicdata.collector;

/**
 * 형사법령 목록의 법령 1건.
 *
 * <p>{@code lawId}(법령ID)는 그 법령이라는 개체를 관통하는 고정 식별자, {@code mst}
 * (법령일련번호)는 현재 시행 중인 버전의 식별자다 - 법령이 개정되면 lawId는 그대로고 mst만
 * 새로 발급된다 (실 API로 확인한 내용, private-doc 31번 항목 참고). 나중에 변경감지를
 * 구현할 때 이 mst 값을 마지막으로 저장해둔 값과 비교하는 방식을 1차로 검토 중.</p>
 */
public record MolegLaw(String lawId, String lawName, String mst, String lawType,
                        String promulgationDate, String effectiveDate, String ministry) {
}
