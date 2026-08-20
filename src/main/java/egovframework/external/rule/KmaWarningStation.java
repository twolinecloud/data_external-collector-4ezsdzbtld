package egovframework.external.rule;

import java.util.List;

/**
 * 기상특보(getWthrWrnList) {@code stnId}(지점코드) 1개의 관할구역.
 *
 * <p>출처: 기상청21_기상특보 조회서비스_오픈API활용가이드.docx "첨부. 지점코드"
 * (2026-06-01판, data.go.kr 15000415 참고문서 zip에서 직접 확인). 실측(2026-08-18,
 * 최근 6일 326건)에서도 이 표의 9개 코드 중 8개(108/109/133/143/146/156/159/184)가
 * 그대로 관측됨 - 나머지 1개(131=청주/충청북도)는 그 기간에 발생이 없었을 뿐.</p>
 *
 * @param nationwide {@code stnId=108}(서울) 전용 - 관할구역이 "전국"으로 명시돼 있어
 *                   {@code jurisdictionSido}가 비어있는 대신 이 플래그로 표시한다.
 */
public record KmaWarningStation(String stnId, String stnName, List<String> jurisdictionSido, boolean nationwide) {

    public boolean covers(String facilitySido) {
        return nationwide || jurisdictionSido.contains(facilitySido);
    }
}
