package egovframework.external.publicdata.transfer;

import java.nio.file.Path;
import java.util.List;

/**
 * 만들어진 ZIP 하나와, 전송 후 응답과 대조할 값들.
 *
 * @param jsonCount        본문 JSON 수(manifest.json 제외)
 * @param topLevelFolders  최상위 폴더 수(law, admrul)
 * @param documents        이 ZIP에 든 문서 - 전송이 확인되면 이것들을 "보냄"으로 기록한다
 * @param manifestJson     ZIP 최상위 manifest.json 과 같은 내용. Xenon 이 업로드 요청의 'manifest' 필드로도 받기를 요구한다
 */
public record ZipPart(int index, Path file, String sha256, long sizeBytes, int jsonCount,
                      int attachmentCount, int topLevelFolders, List<LawDocument> documents, String manifestJson) {
}
