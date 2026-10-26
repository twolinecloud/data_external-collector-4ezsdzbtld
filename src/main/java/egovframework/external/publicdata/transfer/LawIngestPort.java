package egovframework.external.publicdata.transfer;

import java.io.IOException;

/** 법령 ZIP을 받는 쪽(Xenon/GenOS)에 대한 포트 - 테스트에서 가짜로 갈아끼우려고 분리. */
public interface LawIngestPort {

    /** 인증과 연결을 확인한다. 실패하면 수백 MB를 내려받기 전에 전송을 포기하려고 쓴다. */
    void ping() throws IngestException;

    /** ZIP 하나를 전송하고 응답의 체크섬·구조 값과 우리가 계산한 값을 대조한다. 어긋나면 {@link IngestException}. */
    Receipt upload(ZipPart part) throws IngestException;

    /**
     * 수신 응답 중 대조에 쓰는 필드 ({@code zip.valid}, {@code zip.json}, {@code zip.top_level_count} 등).
     */
    record Receipt(boolean received, String filename, long sizeBytes, String sha256, boolean zipValid,
                   int zipEntries, int zipJson, int zipAttachments, int topLevelCount) {
    }

    class IngestException extends IOException {
        private final boolean retryable;

        public IngestException(String message) {
            this(message, true);
        }

        /** @param retryable 같은 요청을 다시 보내면 달라질 수 있는가 - 400번대 응답(요청이 잘못됨)은 다시 보내도 같다 */
        public IngestException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }

        public IngestException(String message, Throwable cause) {
            super(message, cause);
            this.retryable = true;
        }
    }
}
