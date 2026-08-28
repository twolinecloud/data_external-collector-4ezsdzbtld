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
 * {@code classpath:moleg-criminal-laws.csv}(교정시설 관련 법령/행정규칙 수집 대상 목록)를
 * 읽어 {@link MolegLaw} 목록으로 제공한다.
 *
 * <p>CSV 컬럼: lawId,lawName,mst,lawType,promulgationDate,effectiveDate,ministry,docType.
 * "형사법령" 44건(모법 22 + 시행령/시행규칙 22)에서 출발했지만, 2026-08-28 여러 출처를
 * 교차 참조한 목록({@code law_target_260828.csv}, 법령/행정규칙/매뉴얼 523건)을 반영하며
 * 법령 433건 + 행정규칙 58건(총 491건)으로 확대됐다 - 각 법령/행정규칙명을 실 API로 검색해서
 * lawId/mst/시행일자까지 확인 후 확정(private-doc 31/39번 항목 참고). 매뉴얼 8건은 API
 * 소스가 없어 이 목록에서 제외(수집 불가, 별도 처리 방식 확정 전까지 보류).</p>
 *
 * <p><b>RFC4180 방식 quoted-CSV 파싱(2026-08-28)</b>: 공동소관 부처("국방부,법무부" 등)나
 * 쉼표가 포함된 법령명("...창설, 가족관계등록부 정정...에 관한 특례법" 등 실존)이 목록
 * 확대 과정에서 다수 발견돼, 필드 값 안의 "," 자체를 다른 문자로 치환하던 기존 방식(원본
 * 데이터를 훼손)을 버리고 큰따옴표로 감싼 필드를 제대로 해석하는 {@link #parseCsvLine}으로
 * 교체했다. Python {@code csv.writer}로 생성한 표준 quoted-CSV를 그대로 읽을 수 있다.</p>
 */
@Component
public class MolegLawListLoader {

    private static final String RESOURCE_PATH = "moleg-criminal-laws.csv";
    private static final int COLUMN_COUNT = 8;

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
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cols = parseCsvLine(line);
                if (cols.size() != COLUMN_COUNT) {
                    throw new IllegalStateException(RESOURCE_PATH + " " + lineNo + "행 컬럼 수 불일치("
                        + cols.size() + "개, " + COLUMN_COUNT + "개 기대): " + line);
                }
                // lawId,lawName,mst,lawType,promulgationDate,effectiveDate,ministry,docType
                result.add(new MolegLaw(cols.get(0), cols.get(1), cols.get(2), cols.get(3),
                    cols.get(4), cols.get(5), cols.get(6), cols.get(7)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("법령/행정규칙 목록 리소스(" + RESOURCE_PATH + ") 로딩 실패", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("법령/행정규칙 목록 리소스(" + RESOURCE_PATH + ")에 데이터가 없음");
        }
        return result;
    }

    /**
     * RFC4180 방식 CSV 한 줄 파싱 - 큰따옴표로 감싼 필드 안의 ","는 구분자로 취급하지 않고,
     * {@code ""}는 이스케이프된 큰따옴표 1개로 해석한다. 필드 안 개행은 다루지 않는다(이 목록의
     * 값들은 전부 한 줄짜리라 필요 없음).
     */
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }
}
