package egovframework.external.service;

import egovframework.external.model.PurgeResult;
import egovframework.external.publicdata.loader.mapper.AirQualityMapper;
import egovframework.external.publicdata.loader.mapper.AsosHourlyMapper;
import egovframework.external.publicdata.loader.mapper.DisasterMsgMapper;
import egovframework.external.publicdata.loader.mapper.DustForecastMapper;
import egovframework.external.publicdata.loader.mapper.LivingAirDiffusionIdxMapper;
import egovframework.external.publicdata.loader.mapper.LivingUvIdxMapper;
import egovframework.external.publicdata.loader.mapper.WeatherNcstMapper;
import egovframework.external.publicdata.loader.mapper.WeatherUltraFcstMapper;
import egovframework.external.publicdata.loader.mapper.WeatherVilageFcstMapper;
import egovframework.external.publicdata.loader.mapper.WeatherWarningMapper;
import egovframework.external.utility.PipelineLogUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.function.IntUnaryOperator;

/**
 * admin-db(kcais)에 적재된 지 오래된 데이터를 주기적으로 지우는 보존기간(retention) 정책
 * 구현체 - task-spec.md 11번 항목("비식별화(저장)/전송 데이터는 실행일시로부터 1개월만
 * 노출, 이후 폐기") 요구사항. {@code PublicDataLoadService}와 대칭 구조이되, 대상이
 * "raw_staging의 CLEANSED 행"이 아니라 "admin-db 최종 테이블 5개"라는 점이 다르다.
 *
 * <p><b>대상 테이블 9개</b> - {@code tb_ext_weather_ncst}/{@code _ultra_fcst}/
 * {@code _vilage_fcst}/{@code _warning}/{@code tb_ext_disaster_msg}/
 * {@code tb_ext_living_uv_idx}/{@code tb_ext_living_air_diffusion_idx}(2026-08-24 추가)/
 * {@code tb_ext_asos_hourly}/{@code tb_ext_air_quality}/{@code tb_ext_dust_forecast}
 * (2026-09-04 추가, 날씨 기호 안개·황사용). {@code tb_ext_law_target}(수집 대상 "목록",
 * 시계열 데이터 아님)과
 * {@code tb_ext_weather_facility}(마스터 테이블)는 폐기 대상이 아니다.</p>
 *
 * <p><b>기준 컬럼은 {@code reg_dtm}(적재 시각)</b> - "실행일시로부터"라는 요구사항 문구가
 * 관측/예보 대상 시각(base_dtm 등 업무 시각)이 아니라 우리가 이 행을 적재한 시각 기준임을
 * 뜻한다고 해석함(2026-08-24). 실제 DELETE는 각 매퍼가 SQL의 {@code now()}를 직접 써서
 * 계산한다(Java에서 {@code LocalDateTime.now()}로 cutoff를 미리 계산해 넘기지 않음) - reg_dtm
 * 을 채울 때 쓴 것과 동일한 {@code now()}를 재사용해 타임존 불일치 여지를 원천 차단하기
 * 위함(Main.java 전역 UTC 기본값 때문에 겪었던 사고, 2026-08-21, 재발 방지).</p>
 *
 * <p><b>Log Collector 연동 안 함</b> - Collect/Cleanse/Load와 달리 이 배치는 로그 컬렉터
 * 배치를 만들지 않는다. 로그 컬렉터의 C05 공통코드 stepTypeCd(COLLECT/CLEANSE/ANALYZE/
 * DEIDENT/STORE/SEND)에 "폐기(PURGE)"에 해당하는 값이 없고, STORE를 억지로 재사용하면
 * "적재 건수"로 집계되는 지표에 삭제 건수가 섞여 의미가 왜곡되므로(2026-08-24 판단) 일단
 * 로컬 로그({@link PipelineLogUtils})로만 남긴다 - 필요해지면 PL에게 stepTypeCd 추가를
 * 요청하고 그때 연동.</p>
 *
 * <p><b>{@code public-data.purge.enabled=true}일 때만 빈으로 등록됨(2026-08-24 수정)</b> -
 * 이 서비스가 생성자에서 admin-db 매퍼 7개를 직접 요구하는데, 그 매퍼들은
 * {@code AdminDbConfig}가 활성화될 때만(=admin-db 관련 플래그 중 하나라도 켜져있을 때만)
 * 빈으로 존재한다. 이전엔 이 서비스가 무조건부 빈이라 admin-db 관련 플래그가 <b>전부</b>
 * 기본값(꺼짐)이면 매퍼 빈을 못 찾아 앱이 부팅 자체를 못 하는 잠재 버그가 있었음(실측,
 * 2026-08-24 - facility-sync 개발 중 admin-db 플래그를 전부 끈 상태로 부팅해보다가 발견) -
 * {@code KmaUltraSrtNcstLoader} 등 다른 admin-db 소비자들처럼 조건부 빈으로 바꿔 해결.
 * {@code PublicDataPurgeScheduler}/{@code PublicDataPurgeController}도 이 서비스에
 * 의존하므로 동일 조건을 달아야 함(같이 수정).</p>
 */
@Service
@ConditionalOnProperty(prefix = "public-data.purge", name = "enabled", havingValue = "true")
public class PublicDataPurgeService {

    private static final Logger logger = LogManager.getLogger(PublicDataPurgeService.class);
    private static final String STAGE = "PURGE";
    private static final String SOURCE = "admin-db(kcais)";

    private final WeatherNcstMapper weatherNcstMapper;
    private final WeatherUltraFcstMapper weatherUltraFcstMapper;
    private final WeatherVilageFcstMapper weatherVilageFcstMapper;
    private final WeatherWarningMapper weatherWarningMapper;
    private final DisasterMsgMapper disasterMsgMapper;
    private final LivingUvIdxMapper livingUvIdxMapper;
    private final LivingAirDiffusionIdxMapper livingAirDiffusionIdxMapper;
    private final AsosHourlyMapper asosHourlyMapper;
    private final AirQualityMapper airQualityMapper;
    private final DustForecastMapper dustForecastMapper;
    private final boolean enabled;
    private final int retentionDays;

    public PublicDataPurgeService(
        WeatherNcstMapper weatherNcstMapper,
        WeatherUltraFcstMapper weatherUltraFcstMapper,
        WeatherVilageFcstMapper weatherVilageFcstMapper,
        WeatherWarningMapper weatherWarningMapper,
        DisasterMsgMapper disasterMsgMapper,
        LivingUvIdxMapper livingUvIdxMapper,
        LivingAirDiffusionIdxMapper livingAirDiffusionIdxMapper,
        AsosHourlyMapper asosHourlyMapper,
        AirQualityMapper airQualityMapper,
        DustForecastMapper dustForecastMapper,
        @Value("${public-data.purge.enabled:false}") boolean enabled,
        @Value("${public-data.purge.retention-days:30}") int retentionDays
    ) {
        this.weatherNcstMapper = weatherNcstMapper;
        this.weatherUltraFcstMapper = weatherUltraFcstMapper;
        this.weatherVilageFcstMapper = weatherVilageFcstMapper;
        this.weatherWarningMapper = weatherWarningMapper;
        this.disasterMsgMapper = disasterMsgMapper;
        this.livingUvIdxMapper = livingUvIdxMapper;
        this.livingAirDiffusionIdxMapper = livingAirDiffusionIdxMapper;
        this.asosHourlyMapper = asosHourlyMapper;
        this.airQualityMapper = airQualityMapper;
        this.dustForecastMapper = dustForecastMapper;
        this.enabled = enabled;
        this.retentionDays = retentionDays;
    }

    /**
     * 대상 테이블 10개에서 {@code retentionDays}일 초과 행을 각각 삭제한다. 테이블 하나가
     * 실패해도(DB 오류 등) 나머지 테이블은 계속 시도한다.
     *
     * @return 삭제 총 건수 / 성공한 테이블 수 / 실패한 테이블 수 (enabled=false면 전부 0)
     */
    public PurgeResult purgeExpired() {
        if (!enabled) {
            return new PurgeResult(0, 0, 0);
        }

        int[] deleted = {
            purgeOne("tb_ext_weather_ncst", weatherNcstMapper::deleteOlderThan),
            purgeOne("tb_ext_weather_ultra_fcst", weatherUltraFcstMapper::deleteOlderThan),
            purgeOne("tb_ext_weather_vilage_fcst", weatherVilageFcstMapper::deleteOlderThan),
            purgeOne("tb_ext_weather_warning", weatherWarningMapper::deleteOlderThan),
            purgeOne("tb_ext_disaster_msg", disasterMsgMapper::deleteOlderThan),
            purgeOne("tb_ext_living_uv_idx", livingUvIdxMapper::deleteOlderThan),
            purgeOne("tb_ext_living_air_diffusion_idx", livingAirDiffusionIdxMapper::deleteOlderThan),
            purgeOne("tb_ext_asos_hourly", asosHourlyMapper::deleteOlderThan),
            purgeOne("tb_ext_air_quality", airQualityMapper::deleteOlderThan),
            purgeOne("tb_ext_dust_forecast", dustForecastMapper::deleteOlderThan),
        };

        int totalDeleted = 0;
        int successTableCount = 0;
        int failTableCount = 0;
        for (int count : deleted) {
            if (count < 0) {
                failTableCount++;
            } else {
                successTableCount++;
                totalDeleted += count;
            }
        }
        return new PurgeResult(totalDeleted, successTableCount, failTableCount);
    }

    /** @return 삭제된 건수, 실패 시 -1(집계에서 실패로 분류) */
    private int purgeOne(String tableName, IntUnaryOperator deleteFn) {
        try {
            int deletedCount = deleteFn.applyAsInt(retentionDays);
            PipelineLogUtils.info(logger, STAGE, SOURCE, tableName,
                retentionDays + "일 초과 데이터 삭제 완료: " + deletedCount + "건");
            return deletedCount;
        } catch (Exception e) {
            PipelineLogUtils.error(logger, STAGE, SOURCE, tableName, "삭제 실패", e);
            return -1;
        }
    }
}
