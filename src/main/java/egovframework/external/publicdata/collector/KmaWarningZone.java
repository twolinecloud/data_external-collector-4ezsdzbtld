package egovframework.external.publicdata.collector;

import java.util.List;

/**
 * 시설 위치로 계산한 기상특보 구역 ({@link KmaWarningZoneDirectory#resolve}).
 *
 * @param code         가장 가까운 구역의 특보구역코드 (예: L1032010)
 * @param name         그 구역의 통보문 이름 (예: "홍성동부", "인천남부", "군산(옥도면 제외)")
 * @param areaPrefix   이 구역이 속한 시군(또는 광역시) 이름 - 통보문 항목이 이 이름으로 시작하면 같은 시군의 세분 구역이다
 * @param nearestKm    시설에서 이 구역 관측소까지 거리
 * @param runnerUpName 같은 시군의 다른 구역 중 가장 가까운 것 (없으면 null)
 * @param runnerUpKm   그 거리 (없으면 null)
 * @param names        시설이 받는 구역 이름들 - 첫째가 {@code name}. 경계 근처면 이웃 구역도 들어간다(놓치지 않는 쪽)
 * @param elevationM   시설 해발(m), 모르면 null
 */
public record KmaWarningZone(String code, String name, String areaPrefix, double nearestKm, String runnerUpName, Double runnerUpKm,
                             List<String> names, Double elevationM) {

    /** 이웃 구역 관측소가 가장 가까운 구역보다 이만큼 이내로 가까우면 경계 근처로 보고 그 구역 특보도 받는다. */
    public static final double BORDER_MARGIN_KM = 3.0;

    public KmaWarningZone(String code, String name, String areaPrefix, double nearestKm, String runnerUpName, Double runnerUpKm) {
        this(code, name, areaPrefix, nearestKm, runnerUpName, runnerUpKm, List.of(name), null);
    }

    public boolean nearBorder() {
        return names.size() > 1;
    }
}
