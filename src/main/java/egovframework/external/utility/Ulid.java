package egovframework.external.utility;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * ULID(Universally Unique Lexicographically sortable Identifier) 생성기.
 *
 * <p>admin-db(kcais)의 앱 생성 PK 컨벤션(VARCHAR(30))에 맞춰 도입 - 표준 UUID(하이픈 있든
 * 없든 32~36자)는 그대로 안 들어가지만, ULID는 26자라 여유 있게 들어간다(2026-08-21,
 * private-doc/cleanse-db-schema-spec.md §5 "ID 채번 스킴" 결정 - 사용자 확정). 48비트
 * 밀리초 타임스탬프 + 80비트 난수를 Crockford Base32(대소문자 구분 없음, I/L/O/U 제외 -
 * 사람이 옮겨적을 때 헷갈리는 문자를 뺀 알파벳)로 인코딩한다. 타임스탬프가 앞에 오기 때문에
 * 생성 순서대로 문자열 정렬이 가능하고, 여러 인스턴스/재시작 간 조율(시퀀스 채번) 없이도
 * 충돌 걱정 없이 유일성이 보장된다 - 로그 컬렉터의 execId(yyyyMMdd+prefix+seq) 방식과 달리
 * 중앙 채번 로직이 필요 없다.</p>
 *
 * <p>스펙: <a href="https://github.com/ulid/spec">github.com/ulid/spec</a></p>
 */
public final class Ulid {

    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    /** 새 ULID(26자)를 생성한다. */
    public static String generate() {
        return generate(Instant.now().toEpochMilli());
    }

    static String generate(long epochMillis) {
        byte[] randomness = new byte[10]; // 80비트
        RANDOM.nextBytes(randomness);

        char[] result = new char[26];
        // 타임스탬프 48비트 -> 10자 (앞부분, 정렬 기준)
        long time = epochMillis;
        for (int i = 9; i >= 0; i--) {
            result[i] = CROCKFORD_BASE32[(int) (time & 0x1F)];
            time >>>= 5;
        }
        // 난수 80비트 -> 16자 (뒷부분)
        encodeRandomness(randomness, result);
        return new String(result);
    }

    private static void encodeRandomness(byte[] randomness, char[] result) {
        // 80비트(10바이트)를 5비트씩 16덩어리로 - 바이트 경계와 5비트 경계가 안 맞아 비트를
        // 이어붙인 정수(long 2개로 나눠 계산)로 다뤄야 함. 표준 ULID 구현들의 통상적인 방식.
        int idx = 10;
        long buffer = 0;
        int bits = 0;
        for (byte b : randomness) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result[idx++] = CROCKFORD_BASE32[(int) ((buffer >>> bits) & 0x1F)];
            }
        }
        if (bits > 0) {
            result[idx] = CROCKFORD_BASE32[(int) ((buffer << (5 - bits)) & 0x1F)];
        }
    }
}
