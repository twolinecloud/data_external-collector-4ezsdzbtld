package egovframework.external.rule;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code classpath:kma-warning-station.csv}(기상특보 지점코드 10건, {@link KmaWarningStation}
 * 클래스 주석의 출처 참고)를 읽어 제공.
 */
@Component
public class KmaWarningStationLoader {

    private static final String RESOURCE_PATH = "kma-warning-station.csv";

    private final List<KmaWarningStation> stations;

    public KmaWarningStationLoader() {
        this.stations = Collections.unmodifiableList(load());
    }

    public List<KmaWarningStation> all() {
        return stations;
    }

    private List<KmaWarningStation> load() {
        List<KmaWarningStation> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                // stnId,stnName,jurisdictionSido(세미콜론 구분),nationwide(Y/N)
                List<String> sidoList = cols[2].isBlank()
                    ? List.of()
                    : Arrays.asList(cols[2].split(";"));
                result.add(new KmaWarningStation(cols[0], cols[1], sidoList, "Y".equals(cols[3])));
            }
        } catch (IOException e) {
            throw new IllegalStateException("기상특보 지점코드 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("기상특보 지점코드 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
