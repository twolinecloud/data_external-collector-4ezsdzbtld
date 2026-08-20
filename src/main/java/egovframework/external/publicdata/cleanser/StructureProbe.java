package egovframework.external.publicdata.cleanser;

import org.json.JSONArray;

import java.util.Set;
import java.util.function.Function;

/**
 * 구조 드리프트 감지 1개 지점("프로브"). 정제기가 "여기 이 레벨의 필드는 이런 것들이어야 한다"고
 * 선언해두면, {@link JsonStructureDriftDetector}가 실제 수집된 rawPayload에서 관찰된 필드
 * 집합과 대조해서 새 필드/누락 필드를 로그로 남긴다.
 *
 * @param label          로그/메트릭에 찍히는 식별자 (예: "raw-item", "조문단위:조문")
 * @param knownFields    이 정제기가 "알고 있는" 필드 전체(필수+선택). 관찰된 필드 중 여기 없는
 *                       게 있으면 "새 필드 등장"으로 경고
 * @param requiredFields knownFields의 부분집합 - 모든 원소에 항상 있어야 하는 필드만. 관찰된
 *                       필드에서 여기 있는 게 빠졌으면 "예상 필드 누락"으로 경고. 선택 필드(예:
 *                       조문가지번호처럼 일부 원소에만 있는 게 정상인 필드)는 knownFields에만
 *                       넣고 여기엔 넣지 않아야 오탐이 안 남
 * @param observer       rawPayload를 파싱한 최상위 {@link JSONArray}를 받아, 이 프로브가 보는
 *                       레벨에서 실제 관찰된 필드명 전체(여러 원소가 있으면 합집합)를 반환
 */
public record StructureProbe(
    String label,
    Set<String> knownFields,
    Set<String> requiredFields,
    Function<JSONArray, Set<String>> observer
) {
}
