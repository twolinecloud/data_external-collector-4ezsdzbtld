package egovframework.external.publicdata.scheduler;

import egovframework.external.logcollector.DataTypeClassifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관리자 화면의 "스케줄 사용 여부"를 오퍼레이션별로 들고 있는다.
 *
 * <p>관리자 DB의 스케줄 행은 {@code (DATA_TYPE_CD, SCHD_CD)}로 식별하고, SCHD_CD가 없는 행은 그 데이터 구분의 대표 행이다.
 * 오퍼레이션의 사용 여부는 아래 순서로 정한다.
 * <ol>
 *   <li>그 오퍼레이션의 SCHD_CD 행이 있으면 그 값</li>
 *   <li>없으면 데이터 구분 대표 행(SCHD_CD 없음)의 값</li>
 *   <li>둘 다 없으면 <b>사용</b> - 관리자 설정이 없던 때와 같은 동작을 유지한다</li>
 * </ol>
 *
 * <p>값은 관리자 서비스의 즉시 반영 호출({@code POST /internal/schedule/refresh})과, 기동 시·5분마다 관리자 서비스에서
 * 가져오는 동기화({@link ScheduleSettingSync})로 갱신된다. 메모리에만 있으므로 재시작하면 처음 동기화 때까지
 * 모두 사용 상태다. <b>사용 여부만 반영하고 실행 시각은 건드리지 않는다</b>(cron은 application.yml 그대로).
 */
@Component
public class ScheduleActivation {

    private static final Logger logger = LogManager.getLogger(ScheduleActivation.class);

    /** 관리자 DB의 스케줄 한 행. schdCd가 null이면 데이터 구분 대표 행 */
    public record Setting(String dataTypeCd, String schdCd, boolean active) {
    }

    private final Map<String, Boolean> activeByRow = new ConcurrentHashMap<>();

    public void apply(String dataTypeCd, String schdCd, boolean active) {
        Boolean before = activeByRow.put(rowKey(dataTypeCd, schdCd), active);
        if (before == null || before != active) {
            logger.info("[SCHEDULE] 스케줄 사용 여부 반영 dataTypeCd={} schdCd={} active={}", dataTypeCd, schdCd, active);
        }
    }

    /** DB에서 읽은 전체 행으로 통째로 바꾼다 - DB에서 사라진 행의 오래된 값이 남지 않게 한다. */
    public void replaceAll(Collection<Setting> settings) {
        Map<String, Boolean> next = new ConcurrentHashMap<>();
        for (Setting s : settings) {
            next.put(rowKey(s.dataTypeCd(), s.schdCd()), s.active());
        }
        Map<String, Boolean> before = new HashMap<>(activeByRow);
        activeByRow.keySet().retainAll(next.keySet());
        activeByRow.putAll(next);

        // 설정이 없는 행은 사용으로 보므로, 그 기준으로 달라진 행만 로그에 남긴다(5분마다 도는 동기화가 조용하도록)
        Set<String> rows = new TreeSet<>(before.keySet());
        rows.addAll(next.keySet());
        for (String row : rows) {
            boolean was = before.getOrDefault(row, true);
            boolean now = next.getOrDefault(row, true);
            if (was != now) {
                logger.info("[SCHEDULE] 동기화로 스케줄 사용 여부 변경 row={} active={}", row, now);
            }
        }
    }

    public boolean isActive(String operationKey) {
        String dataTypeCd = DataTypeClassifier.dataTypeCd(operationKey);
        String schdCd = ScheduleCodes.schdCdOf(operationKey);
        if (schdCd != null) {
            Boolean own = activeByRow.get(rowKey(dataTypeCd, schdCd));
            if (own != null) {
                return own;
            }
        }
        return activeByRow.getOrDefault(rowKey(dataTypeCd, null), true);
    }

    private static String rowKey(String dataTypeCd, String schdCd) {
        return dataTypeCd + "|" + (schdCd == null ? "" : schdCd);
    }
}
