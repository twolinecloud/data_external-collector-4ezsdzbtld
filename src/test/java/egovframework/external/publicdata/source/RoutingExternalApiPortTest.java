package egovframework.external.publicdata.source;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingExternalApiPortTest {

    private static final class Tagged implements ExternalApiPort {
        private final String tag;

        Tagged(String tag) {
            this.tag = tag;
        }

        @Override
        public String getText(ExternalApiCall call) {
            return tag;
        }

        @Override
        public byte[] getBytes(ExternalApiCall call) {
            return tag.getBytes();
        }
    }

    private static ExternalApiCall call(String endpoint) {
        return new ExternalApiCall(ExternalApiCall.Channel.DEFAULT, "s", "a", endpoint, "serviceKey", "k", Map.of(), false);
    }

    @Test
    void operation은_마지막_경로조각의_영숫자_소문자다() {
        assertEquals("getultrasrtncst", call("https://x/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst").operation());
        assertEquals("dsspif00247", call("https://www.safetydata.go.kr/V2/api/DSSP-IF-00247").operation());
        assertEquals("kmasfctm2php", call("https://apihub.kma.go.kr/api/typ01/url/kma_sfctm2.php").operation());
        assertEquals("getultrasrtncst", call("https://x/getUltraSrtNcst/?a=b").operation());
    }

    @Test
    void 설정이_없으면_전부_direct다() {
        RoutingExternalApiPort port = new RoutingExternalApiPort(new Tagged("direct"), null, Map.of());

        assertEquals("direct", port.getText(call("https://x/getUltraSrtNcst")));
    }

    @Test
    void operation별_설정이_기본값보다_우선한다() {
        RoutingExternalApiPort port = new RoutingExternalApiPort(new Tagged("direct"), new Tagged("esb"),
            Map.of("default", "direct", "getultrasrtncst", "esb"));

        assertEquals("esb", port.getText(call("https://x/getUltraSrtNcst")));
        assertEquals("direct", port.getText(call("https://x/getVilageFcst")));
    }

    @Test
    void 기본값을_esb로_두고_일부만_direct로_돌릴_수_있다() {
        RoutingExternalApiPort port = new RoutingExternalApiPort(new Tagged("direct"), new Tagged("esb"),
            Map.of("default", "ESB", "getwthrwrnlist", "Direct"));

        assertEquals("esb", port.getText(call("https://x/getVilageFcst")));
        assertEquals("direct", port.getText(call("https://x/getWthrWrnList")));
        assertEquals("esb", new String(port.getBytes(call("https://x/getVilageFcst"))));
    }

    @Test
    void esb를_설정했는데_구현체가_없으면_기동_시_실패한다() {
        assertThrows(IllegalStateException.class,
            () -> new RoutingExternalApiPort(new Tagged("direct"), null, Map.of("default", "esb")));
        assertThrows(IllegalStateException.class,
            () -> new RoutingExternalApiPort(new Tagged("direct"), null, Map.of("getvilagefcst", "esb")));
    }

    @Test
    void 알_수_없는_모드_값은_기동_시_실패한다() {
        assertThrows(IllegalStateException.class,
            () -> new RoutingExternalApiPort(new Tagged("direct"), new Tagged("esb"), Map.of("default", "proxy")));
    }

    @Test
    void 호출마다_operation_mode_outcome_태그로_메트릭을_남긴다() {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        ExternalApiPort failing = new ExternalApiPort() {
            @Override public String getText(ExternalApiCall call) { throw new IllegalStateException("boom"); }
            @Override public byte[] getBytes(ExternalApiCall call) { return null; }
        };
        RoutingExternalApiPort ok = new RoutingExternalApiPort(new Tagged("direct"), null, Map.of(), registry);
        RoutingExternalApiPort bad = new RoutingExternalApiPort(failing, null, Map.of(), registry);

        ok.getText(call("https://x/getVilageFcst"));
        assertThrows(IllegalStateException.class, () -> bad.getText(call("https://x/getVilageFcst")));
        bad.getBytes(call("https://x/getVilageFcst"));

        assertEquals(1L, registry.get("external_api_call").tag("operation", "getvilagefcst")
            .tag("mode", "direct").tag("outcome", "success").timer().count());
        assertEquals(1L, registry.get("external_api_call").tag("outcome", "failure").timer().count());
        assertEquals(1L, registry.get("external_api_call").tag("outcome", "empty").timer().count());
    }
}
