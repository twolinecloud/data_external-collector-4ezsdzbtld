package egovframework.external.publicdata.transfer;

import java.util.List;

/**
 * 다운로드까지 끝나 ZIP에 넣을 수 있게 된 문서 1건.
 *
 * @param files   ZIP에 넣을 첨부(허용 확장자만)
 * @param skipped 넣지 않은 첨부와 그 이유 - 허용되지 않는 확장자, 영구 다운로드 실패 등. manifest에 남는다
 */
public record PreparedDocument(LawDocument doc, List<PackedFile> files, List<SkippedFile> skipped) {

    /**
     * @param folder       문서 폴더 아래의 하위 폴더 - "별표" 또는 "첨부"
     * @param baseName     확장자를 뺀 파일명(별표1, 서식3의2, 첨부1)
     * @param extension    점 없는 소문자 확장자(hwp, pdf ...)
     * @param title        사람이 읽는 제목(별표 제목 또는 원본 파일명) - manifest용
     * @param originalName 서버가 알려준 원본 파일명(없으면 null) - manifest용
     * @param kind         서식파일 / PDF / 이미지 / 첨부 - manifest용
     */
    public record PackedFile(String folder, String baseName, String extension, byte[] content,
                             String title, String originalName, String kind) {
    }

    public record SkippedFile(String title, String kind, String reason) {
    }
}
