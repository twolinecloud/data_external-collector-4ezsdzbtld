package egovframework.external.publicdata.collector;

import java.util.Optional;

/** 위경도의 해발 고도(m). 특보 세분 구역 중 고도로 나뉜 것(평지/산지/중산간)을 가르는 데 쓴다 ({@link KmaWarningZoneDirectory}). */
public interface FacilityElevationLookup {

    Optional<Double> elevationM(double lat, double lon);

    /** 고도를 쓰지 않을 때(테스트, 설정으로 끔). */
    FacilityElevationLookup NONE = (lat, lon) -> Optional.empty();
}
