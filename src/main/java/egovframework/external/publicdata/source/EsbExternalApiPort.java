package egovframework.external.publicdata.source;

/**
 * 메타빌드 ESB 경유 {@link ExternalApiPort} 구현체의 표지 인터페이스. ESB 스펙이 확정되면 이 인터페이스를 구현한
 * {@code @Component} 하나를 추가한다 - {@link RoutingExternalApiPort}가 operation별 모드가 {@code esb}일 때
 * 이 빈으로 보낸다. 지금은 구현체가 없으므로 {@code esb} 모드를 설정하면 기동 시 실패한다.
 */
public interface EsbExternalApiPort extends ExternalApiPort {
}
