package egovframework.external.publicdata.collector;

/**
 * 위경도(WGS84) ↔ 기상청 격자좌표(nx/ny) 변환 - Lambert Conformal Conic(LCC) 투영,
 * 기상청 공식 발표 상수(5km 격자: RE=6371.00877, GRID=5.0, SLAT1=30, SLAT2=60, OLON=126,
 * OLAT=38, XO=43, YO=136).
 *
 * <p>실측 검증 완료(2026-08-24) - {@code kma-facility-locations.csv} 59개소 전부(lat/lon으로
 * 계산한 nx/ny가 CSV에 이미 기록된 nx/ny와 완전 일치, 0건 불일치) - Phase B 자동
 * 지오코딩(주소→위경도) 뒤에 이어 붙일 위경도→격자 변환 단계로 사용.</p>
 */
public final class KmaGridConverter {

    private static final double RE = 6371.00877;
    private static final double GRID = 5.0;
    private static final double SLAT1 = 30.0;
    private static final double SLAT2 = 60.0;
    private static final double OLON = 126.0;
    private static final double OLAT = 38.0;
    private static final double XO = 43;
    private static final double YO = 136;
    private static final double DEGRAD = Math.PI / 180.0;

    private KmaGridConverter() {
    }

    public record Grid(int nx, int ny) {
    }

    public static Grid toGrid(double lat, double lon) {
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lon * DEGRAD - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new Grid(nx, ny);
    }
}
