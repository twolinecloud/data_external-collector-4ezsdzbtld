package egovframework.external.model;

/**
 * 법령 Xenon 전송 1회 결과({@code LawTransferService}). {@code LoadResult}와 같은 모양 - 로그 컬렉터 SEND 스텝의
 * 대상/성공/실패 건수로 그대로 보고한다. 건수는 <b>이번에 보내야 했던(변경된) 문서</b> 기준이고, 변경 없어 건너뛴 문서는 세지 않는다.
 */
public record SendResult(int totalProcessed, int successCount, int failCount) {
}
