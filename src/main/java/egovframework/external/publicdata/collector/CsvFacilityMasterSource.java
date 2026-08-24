package egovframework.external.publicdata.collector;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** 기본값(csv) - {@code classpath:kma-facility-locations.csv} 그대로 반환(변경 없음). */
@Component
@ConditionalOnProperty(prefix = "public-data.facility", name = "master-source", havingValue = "csv", matchIfMissing = true)
public class CsvFacilityMasterSource implements FacilityMasterSource {

    private final FacilityMasterCsvLoader loader;

    public CsvFacilityMasterSource(FacilityMasterCsvLoader loader) {
        this.loader = loader;
    }

    @Override
    public List<FacilityMasterRecord> current() {
        return loader.all();
    }
}
