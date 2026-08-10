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

/**
 * {@code classpath:moleg-criminal-laws.csv}(형사법령 44건 - 모법 22개 + 시행령/시행규칙
 * 22개)를 읽어 {@link MolegLaw} 목록으로 제공.
 *
 * <p>CSV 컬럼: lawId,lawName,mst,lawType,promulgationDate,effectiveDate,ministry.
 * "형사법령"이 국가법령정보센터에 공식 분류로 존재하지 않아(확인함) 사람이 직접 선정한
 * 목록이다 - 각 법령명을 실 API로 검색해서 lawId/mst/시행일자까지 확인 후 확정 (private-doc
 * 31번 항목 참고). ministry 컬럼은 공동소관인 경우 "·"로 구분(원본엔 ","라 CSV 파싱과
 * 충돌해서 치환).</p>
 */
@Component
public class MolegLawListLoader {

    private static final String RESOURCE_PATH = "moleg-criminal-laws.csv";

    private final List<MolegLaw> laws;

    public MolegLawListLoader() {
        this.laws = Collections.unmodifiableList(load());
    }

    public List<MolegLaw> all() {
        return laws;
    }

    private List<MolegLaw> load() {
        List<MolegLaw> result = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                // lawId,lawName,mst,lawType,promulgationDate,effectiveDate,ministry
                result.add(new MolegLaw(cols[0], cols[1], cols[2], cols[3], cols[4], cols[5], cols[6]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("형사법령 목록 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("형사법령 목록 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }
}
