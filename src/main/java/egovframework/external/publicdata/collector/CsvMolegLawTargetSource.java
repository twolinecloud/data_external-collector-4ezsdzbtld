package egovframework.external.publicdata.collector;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code public-data.moleg.law-target-source=csv}(기본값)일 때 활성화 - 기존과 동일하게
 * {@code classpath:moleg-criminal-laws.csv}를 그대로 쓴다. admin-db 연동 없이도 항상
 * 동작해야 하는 기본값이라 {@code matchIfMissing=true}.
 */
@Component
@ConditionalOnProperty(prefix = "public-data.moleg", name = "law-target-source", havingValue = "csv", matchIfMissing = true)
@RequiredArgsConstructor
public class CsvMolegLawTargetSource implements MolegLawTargetSource {

    private final MolegLawListLoader lawListLoader;

    @Override
    public List<MolegLaw> current() {
        return lawListLoader.all();
    }
}
