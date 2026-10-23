package egovframework.external.publicdata.transfer;

/** 별표/첨부 파일 다운로드 포트 - 테스트에서 가짜로 갈아끼우려고 분리. */
public interface LawFileFetcher {

    FetchedFile fetch(String url) throws FetchException;

    /**
     * @param fileName 서버가 {@code Content-Disposition}으로 알려준 원본 파일명(없으면 null) - 확장자 판정에 쓴다.
     *                 별표 서식파일은 같은 링크 종류인데도 hwp와 hwpx가 섞여 있어 링크만으로는 알 수 없다.
     */
    record FetchedFile(byte[] content, String fileName, String contentType) {
    }

    /** @param permanent true면 재시도해도 소용없는 실패(404/410 등) - 이 파일만 건너뛰고 문서는 보낸다. */
    class FetchException extends Exception {
        private final boolean permanent;

        public FetchException(String message, boolean permanent) {
            super(message);
            this.permanent = permanent;
        }

        public FetchException(String message, boolean permanent, Throwable cause) {
            super(message, cause);
            this.permanent = permanent;
        }

        public boolean isPermanent() {
            return permanent;
        }
    }
}
