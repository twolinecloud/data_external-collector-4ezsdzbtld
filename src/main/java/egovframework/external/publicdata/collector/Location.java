package egovframework.external.publicdata.collector;

/**
 * 기상청 격자좌표 조회 대상 지점 하나. {@code facilityId}는 URL-safe 안정 식별자
 * (예: {@code f01}), classpath:kma-facility-locations.csv 의 행 순서로 고정 부여됨 -
 * 파일 순서를 바꾸면 id가 밀리므로 행을 추가할 땐 끝에 덧붙일 것.
 */
public record Location(String facilityId, String facilityName, String nx, String ny) {
}
