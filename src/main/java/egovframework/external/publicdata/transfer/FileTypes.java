package egovframework.external.publicdata.transfer;

import java.util.Locale;

/**
 * 내려받은 파일의 확장자 판정. 별표 서식파일은 링크가 같은 종류인데도 HWP와 HWPX가 섞여 있어(표본에서 HWPX 약 6%)
 * 서버가 알려준 원본 파일명({@code Content-Disposition})이 가장 정확하다. 그게 없으면 힌트 이름, 마지막으로 파일 머리 바이트.
 */
final class FileTypes {

    private FileTypes() {
    }

    static String extensionOf(LawFileFetcher.FetchedFile file, String hintName, String kind) {
        String fromServer = extensionOfName(file.fileName());
        if (!fromServer.isEmpty()) {
            return fromServer;
        }
        String fromHint = extensionOfName(hintName);
        if (!fromHint.isEmpty()) {
            return fromHint;
        }
        return sniff(file.content(), kind);
    }

    static String extensionOfName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.strip();
        int dot = trimmed.lastIndexOf('.');
        if (dot < 0 || dot == trimmed.length() - 1) {
            return "";
        }
        return trimmed.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String sniff(byte[] c, String kind) {
        if (startsWith(c, '%', 'P', 'D', 'F')) {
            return "pdf";
        }
        if (startsWith(c, 'G', 'I', 'F', '8')) {
            return "gif";
        }
        if (c.length >= 4 && (c[0] & 0xFF) == 0xD0 && (c[1] & 0xFF) == 0xCF && (c[2] & 0xFF) == 0x11 && (c[3] & 0xFF) == 0xE0) {
            return "hwp";
        }
        if (startsWith(c, 'P', 'K')) {
            // OOXML/HWPX/일반 ZIP이 모두 PK로 시작한다 - 서식파일 링크면 HWPX, 그 외는 ZIP으로 보고 허용 목록에서 걸러낸다.
            return "서식파일".equals(kind) ? "hwpx" : "zip";
        }
        return "bin";
    }

    private static boolean startsWith(byte[] c, char... prefix) {
        if (c.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (c[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
