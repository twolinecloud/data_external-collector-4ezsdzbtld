package egovframework.external.publicdata.collector;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KmaLocationCollectorFactory}가 59개소 × 오퍼레이션 3종 조합으로 컬렉터를
 * 올바르게 생성하는지 검증. 실제 API 호출은 하지 않음 - 생성/키 유일성만 확인.
 */
class KmaLocationCollectorFactoryTest {

    private final KmaApiClient apiClient = new KmaApiClient(new RestTemplate());
    private final FacilityLocationLoader locationLoader = new FacilityLocationLoader(new CsvFacilityMasterSource(new FacilityMasterCsvLoader()));
    private final KmaLocationCollectorFactory factory =
        new KmaLocationCollectorFactory(apiClient, "https://example.invalid", "test-key", locationLoader);

    @Test
    void 오퍼레이션별로_59개소만큼_컬렉터가_생성된다() {
        assertThat(factory.ultraSrtNcstCollectors()).hasSize(59);
        assertThat(factory.ultraSrtFcstCollectors()).hasSize(59);
        assertThat(factory.vilageFcstCollectors()).hasSize(59);
    }

    @Test
    void 전체_목록은_3개_오퍼레이션을_합친_177개다() {
        assertThat(factory.allLocationBasedCollectors()).hasSize(59 * 3);
    }

    @Test
    void 기상_컬렉터의_staging_유효기간은_날짜_기준_자정_경계다() {
        LocalDate 수집일 = LocalDate.of(2026, 9, 1);

        for (PublicDataCollector collector : factory.allLocationBasedCollectors()) {
            // 오늘이 9/2일 때 9/1 00:00 이후 수집분은 적재 실패해도 재시도 대기로 남아야 하고,
            // 8/31 이하만 폐기 대상이다 - 그러려면 9/1 수집분의 만료가 9/3 00:00이어야 한다.
            assertThat(collector.stagingExpiresAt(수집일))
                .isEqualTo(LocalDateTime.of(2026, 9, 3, 0, 0));
        }
    }

    @Test
    void 모든_컬렉터의_key는_서로_고유하다() {
        List<PublicDataCollector> all = factory.allLocationBasedCollectors();

        Set<String> keys = all.stream().map(PublicDataCollector::key).collect(Collectors.toSet());

        assertThat(keys).hasSameSizeAs(all);
    }

    @Test
    void apiName에_기관명이_포함되어_구분된다() {
        PublicDataCollector first = factory.ultraSrtNcstCollectors().get(0);

        assertThat(first.apiName()).contains("초단기실황조회").contains("(");
        assertThat(first.sourceName()).isEqualTo("공공데이터포털 (기상청 동네예보)");
    }

    @Test
    void 목록을_캐시하지_않고_호출할_때마다_새로_조회한다() {
        // db 소스일 때 승인된 신규 시설이 재시작 없이 다음 스케줄 틱부터 반영되려면
        // 생성자 시점이 아니라 매 메서드 호출마다 다시 조회해야 함(Phase C, 2026-08-24).
        StubFacilityMasterSource stubSource = new StubFacilityMasterSource();
        stubSource.records = List.of(new FacilityMasterRecord("1", "A", "시도", "시군구", "60", "124"));
        KmaLocationCollectorFactory dynamicFactory = new KmaLocationCollectorFactory(
            apiClient, "https://example.invalid", "test-key", new FacilityLocationLoader(stubSource));

        assertThat(dynamicFactory.ultraSrtNcstCollectors()).hasSize(1);

        stubSource.records = List.of(
            new FacilityMasterRecord("1", "A", "시도", "시군구", "60", "124"),
            new FacilityMasterRecord("2", "B", "시도", "시군구", "61", "125"));

        assertThat(dynamicFactory.ultraSrtNcstCollectors()).hasSize(2);
    }

    private static class StubFacilityMasterSource implements FacilityMasterSource {
        List<FacilityMasterRecord> records = List.of();

        @Override
        public List<FacilityMasterRecord> current() {
            return records;
        }
    }
}
