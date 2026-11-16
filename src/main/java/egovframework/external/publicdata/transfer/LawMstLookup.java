package egovframework.external.publicdata.transfer;

import java.io.IOException;

/**
 * 법령의 MST(법령일련번호)를 알아내는 포트. 법령 본문(target=eflaw)에는 MST가 없어서(법령키는 법령ID+공포일자+공포번호)
 * 목록 검색으로 따로 조회해야 한다. 행정규칙은 본문에 행정규칙일련번호가 있어 조회하지 않는다. 테스트에서 가짜로
 * 갈아끼우려고 분리.
 */
public interface LawMstLookup {

    /**
     * @return MST. 법제처 검색에 해당 법령이 없으면 null(조회는 정상 - 다시 해도 같다)
     * @throws LookupException 통신·응답 형식 오류처럼 다시 하면 달라질 수 있는 실패
     */
    String find(LawDocument doc) throws LookupException;

    class LookupException extends IOException {
        public LookupException(String message) {
            super(message);
        }

        public LookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
