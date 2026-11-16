package egovframework.external.publicdata.source;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * <p><b>호출 이력</b>: 모든 외부 호출이 여기를 지나므로 호출 1회마다 메트릭
 * {@code external_api_call}(Timer, 태그 operation·mode·outcome)을 남기고, 실패는 WARN 로그로 남긴다. 성공 호출은
 * 하루 약 3,900건이라 로그로는 남기지 않는다. ESB 거래ID·IF_ID는 ESB 어댑터가 생기면 그 구현체가 채운다.</p>
 *
 * <p>기본은 모두 {@code direct}라 아무것도 설정하지 않으면 지금까지와 완전히 같다. {@code esb}가 설정됐는데
 * {@link EsbExternalApiPort} 구현체가 없으면 <b>기동 시</b> 실패한다 - 호출 중에 조용히 직접 경로로 돌려보내면
 * 전환했다고 믿는 채로 원천에 직접 나가게 되므로 막는다. 알 수 없는 모드 값도 기동 시 실패한다.</p>
 */
@Primary
@Component
public class RoutingExternalApiPort implements ExternalApiPort {

    private static final Logger logger = LogManager.getLogger(RoutingExternalApiPort.class);

    static final String PREFIX = "public-data.source.mode";
    private static final String DEFAULT_KEY = "default";

    enum Mode { DIRECT, ESB }

    private final ExternalApiPort direct;
    private final ExternalApiPort esb; // 없으면 null
    private final Mode defaultMode;
    private final Map<String, Mode> modeByOperation;
    private final MeterRegistry meterRegistry; // 없으면 null(메트릭 생략)

    @Autowired
    public RoutingExternalApiPort(DirectExternalApiAdapter direct, ObjectProvider<EsbExternalApiPort> esb,
                                  Environment environment, ObjectProvider<MeterRegistry> meterRegistry) {
        this(direct, esb.getIfAvailable(),
            Binder.get(environment).bind(PREFIX, Bindable.mapOf(String.class, String.class)).orElse(Map.of()),
            meterRegistry.getIfAvailable());
    }

    RoutingExternalApiPort(ExternalApiPort direct, ExternalApiPort esb, Map<String, String> modes) {
        this(direct, esb, modes, null);
    }

    /** @param modes {@code default} 키와 operation 키별 모드 문자열(direct|esb) */
    RoutingExternalApiPort(ExternalApiPort direct, ExternalApiPort esb, Map<String, String> modes,
                           MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
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
        return invoke(call, port -> port.getText(call));
    }

    @Override
    public byte[] getBytes(ExternalApiCall call) {
        return invoke(call, port -> port.getBytes(call));
    }

    private <T> T invoke(ExternalApiCall call, java.util.function.Function<ExternalApiPort, T> action) {
        Mode mode = modeOf(call);
        long start = System.nanoTime();
        String outcome = "success";
        try {
            T result = action.apply(mode == Mode.ESB ? esb : direct);
            if (result == null) {
                outcome = "empty";
            }
            return result;
        } catch (RuntimeException e) {
            outcome = "failure";
            logger.warn("[외부호출] 실패 operation={} mode={} source={} api={}: {}", call.operation(),
                mode.name().toLowerCase(Locale.ROOT), call.sourceName(), call.apiName(), e.toString());
            throw e;
        } finally {
            record(call, mode, outcome, System.nanoTime() - start);
        }
    }

    private void record(ExternalApiCall call, Mode mode, String outcome, long nanos) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder("external_api_call")
            .description("외부 API 호출 1회(직접/ESB 공통)")
            .tag("operation", call.operation())
            .tag("mode", mode.name().toLowerCase(Locale.ROOT))
            .tag("outcome", outcome)
            .register(meterRegistry)
            .record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private Mode modeOf(ExternalApiCall call) {
        return modeByOperation.getOrDefault(call.operation(), defaultMode);
    }
}
