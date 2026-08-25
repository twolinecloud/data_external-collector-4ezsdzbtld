package egovframework.external.publicdata.collector;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code classpath:kma-living-wthr-idx-area.csv}(16개 시도, {@link LivingWthrIdxArea} 참고)를 읽어 제공. */
@Component
public class LivingWthrIdxAreaLoader {

    private static final String RESOURCE_PATH = "kma-living-wthr-idx-area.csv";

    private final List<LivingWthrIdxArea> areas;

    public LivingWthrIdxAreaLoader() {
        this.areas = Collections.unmodifiableList(load());
    }

    public List<LivingWthrIdxArea> all() {
        return areas;
    }

    private List<LivingWthrIdxArea> load() {
        List<LivingWthrIdxArea> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                // sido,areaNo
                result.add(new LivingWthrIdxArea(cols[0], cols[1]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("생활기상지수 지역코드 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("생활기상지수 지역코드 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
