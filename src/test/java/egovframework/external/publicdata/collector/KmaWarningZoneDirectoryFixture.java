package egovframework.external.publicdata.collector;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** 다른 패키지 테스트가 실제 API허브 응답(테스트 리소스)으로 시설 구역을 얻는 창구. */
public final class KmaWarningZoneDirectoryFixture {

    private static final KmaWarningZoneDirectory DIRECTORY;

    static {
        try (InputStream in = KmaWarningZoneDirectoryFixture.class.getResourceAsStream("/kma-wrn-reg-aws2.txt")) {
            DIRECTORY = KmaWarningZoneDirectory.fromText(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private KmaWarningZoneDirectoryFixture() {
    }

    public static KmaWarningZone zoneOf(FacilityMasterRecord facility) {
        return DIRECTORY.resolve(facility).orElse(null);
    }
}
