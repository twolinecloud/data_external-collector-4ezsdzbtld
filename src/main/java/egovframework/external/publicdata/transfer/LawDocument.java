package egovframework.external.publicdata.transfer;

import java.util.List;

/**
 * 정제된 법령/행정규칙 1건에서 전송에 필요한 부분만 뽑은 것. {@code body}는 법제처 응답 원문(JSON)이고
 * ZIP에는 이걸 그대로 {@code <이름>.json}으로 넣는다 - 우리가 필드를 가공하지 않는다.
 *
 * @param docType {@code LAW} / {@code ADMIN_RULE} ({@code MolegLaw#DOC_TYPE_*})
 * @param lawId   법령ID / 행정규칙ID. 법령과 행정규칙은 ID 체계가 달라 같은 값이 나올 수 있으니 {@code docType}과 함께 써야 유일하다
 * @param name    응답에 적힌 현행 이름 - 대상 목록(CSV)의 옛 이름이 아니라 이 값을 폴더명으로 쓴다
 * @param kind    법종구분(법령: 법률/대통령령/부령...) 또는 행정규칙종류(훈령/예규...)
 */
public record LawDocument(String docType, String lawId, String name, String kind,
                          String promulgationDate, String effectiveDate, String ministry,
                          String body, List<Annex> annexes, List<Attachment> attachments) {

    public static final String LAW = "LAW";
    public static final String ADMIN_RULE = "ADMIN_RULE";

    /** 같은 문서를 오늘/어제 비교하는 키. */
    public String key() {
        return docType + ":" + lawId;
    }

    /**
     * 별표 단위 1개. 법제처는 한 별표를 서식파일(HWP/HWPX)·PDF·이미지(GIF) 세 가지로 제공한다.
     *
     * @param category 별표구분 - 별표/서식/별지/별도/부록
     * @param number   별표번호(0 채움 문자열, "0000"은 번호 없음)
     * @param branch   별표가지번호("00"은 없음)
     */
    public record Annex(String category, String number, String branch, String title,
                        String formUrl, String pdfUrl, List<String> imageUrls) {
    }

    /** 행정규칙 {@code 첨부파일}(별표와 별개인 첨부) 1개. {@code name}은 원본 파일명. */
    public record Attachment(String name, String url) {
    }
}
