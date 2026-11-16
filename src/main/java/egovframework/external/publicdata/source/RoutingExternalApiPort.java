package egovframework.external.publicdata.source;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * operation(인터페이스)별로 직접 연결과 ESB 경유 중 어느 쪽으로 보낼지 고르는 {@link ExternalApiPort}.
 * 클라이언트는 이 빈을 주입받아 쓰고, 어느 쪽으로 가는지 모른다.
 *
 * <p>설정(application.yml):</p>
 * <pre>
 * public-data.source.mode.default: direct        # 기본값. direct | esb
 * public-data.source.mode.getultrasrtncst: esb   # operation별 덮어쓰기(키는 {@link ExternalApiCall#operation()})
 * </pre>
 *
 * <p>기본은 모두 {@code direct}라 아무것도 설정하지 않으면 지금까지와 완전히 같다. {@code esb}가 설정됐는데
 * {@link EsbExternalApiPort} 구현체가 없으면 <b>기동 시</b> 실패한다 - 호출 중에 조용히 직접 경로로 돌려보내면
 * 전환했다고 믿는 채로 원천에 직접 나가게 되므로 막는다. 알 수 없는 모드 값도 기동 시 실패한다.</p>
 */
@Primary
@Component
public class RoutingExternalApiPort implements ExternalApiPort {

    static final String PREFIX = "public-data.source.mode";
    private static final String DEFAULT_KEY = "default";

    enum Mode { DIRECT, ESB }

    private final ExternalApiPort direct;
    private final ExternalApiPort esb; // 없으면 null
    private final Mode defaultMode;
    private final Map<String, Mode> modeByOperation;

    @Autowired
    public RoutingExternalApiPort(DirectExternalApiAdapter direct, ObjectProvider<EsbExternalApiPort> esb,
                                  Environment environment) {
        this(direct, esb.getIfAvailable(),
            Binder.get(environment).bind(PREFIX, Bindable.mapOf(String.class, String.class)).orElse(Map.of()));
    }

    /** @param modes {@code default} 키와 operation 키별 모드 문자열(direct|esb) */
    RoutingExternalApiPort(ExternalApiPort direct, ExternalApiPort esb, Map<String, String> modes) {
        this.direct = direct;
        this.esb = esb;
        this.defaultMode = modes.containsKey(DEFAULT_KEY) ? parse(DEFAULT_KEY, modes.get(DEFAULT_KEY)) : Mode.DIRECT;
        Map<String, Mode> byOperation = new java.util.HashMap<>();
        modes.forEach((operation, value) -> {
            if (!DEFAULT_KEY.equals(operation)) {
                byOperation.put(operation.toLowerCase(Locale.ROOT), parse(operation, value));
            }
        });
        this.modeByOperation = Map.copyOf(byOperation);

        boolean needsEsb = defaultMode == Mode.ESB || modeByOperation.containsValue(Mode.ESB);
        if (needsEsb && esb == null) {
            throw new IllegalStateException(PREFIX + " 에 esb 가 설정됐지만 EsbExternalApiPort 구현체가 없습니다 "
                + "(ESB 어댑터 추가 전에는 direct 만 사용할 수 있습니다)");
        }
    }

    private static Mode parse(String key, String value) {
        try {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException(PREFIX + "." + key + " 값이 올바르지 않습니다: '" + value + "' (direct 또는 esb)");
        }
    }

    @Override
    public String getText(ExternalApiCall call) {
        return route(call).getText(call);
    }

    @Override
    public byte[] getBytes(ExternalApiCall call) {
        return route(call).getBytes(call);
    }

    private ExternalApiPort route(ExternalApiCall call) {
        return modeByOperation.getOrDefault(call.operation(), defaultMode) == Mode.ESB ? esb : direct;
    }
}
