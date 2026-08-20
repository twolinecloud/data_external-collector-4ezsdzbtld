package egovframework.external.publicdata.cleanser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link CleansedJsonDropWriter}의 파일 드롭 동작 검증. 실제 로컬 파일시스템에 씀(@TempDir로 격리).
 */
class CleansedJsonDropWriterTest {

    @Test
    void disabled_이면_아무_파일도_만들지_않는다(@TempDir Path tempDir) {
        Path dir = tempDir.resolve("drop");
        CleansedJsonDropWriter writer = new CleansedJsonDropWriter(false, dir.toString());

        writer.write("kma-weather-warning-list", "[{\"title\":\"x\"}]");

        assertThat(Files.exists(dir)).isFalse();
    }

    @Test
    void collectorKey_json_파일명으로_쓴다(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("drop");
        CleansedJsonDropWriter writer = new CleansedJsonDropWriter(true, dir.toString());

        writer.write("kma-weather-warning-list", "[{\"title\":\"특보\"}]");

        Path expected = dir.resolve("kma-weather-warning-list.json");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(Files.readString(expected, StandardCharsets.UTF_8)).isEqualTo("[{\"title\":\"특보\"}]");
    }

    @Test
    void operationKey가_같아도_collectorKey가_다르면_서로_다른_파일에_쓴다(@TempDir Path tempDir) throws IOException {
        // 법령처럼 여러 인스턴스가 operationKey("moleg-criminal-law")를 공유하고 facilityId도
        // 없는 소스 - collectorKey(법령ID 포함)로 나눠써야 서로 안 덮어씀
        Path dir = tempDir.resolve("drop");
        CleansedJsonDropWriter writer = new CleansedJsonDropWriter(true, dir.toString());

        writer.write("moleg-criminal-law--001692", "[{\"법령\":{\"법령명\":\"형법\"}}]");
        writer.write("moleg-criminal-law--001671", "[{\"법령\":{\"법령명\":\"형사소송법\"}}]");

        assertThat(Files.exists(dir.resolve("moleg-criminal-law--001692.json"))).isTrue();
        assertThat(Files.exists(dir.resolve("moleg-criminal-law--001671.json"))).isTrue();
        assertThat(Files.readString(dir.resolve("moleg-criminal-law--001692.json"), StandardCharsets.UTF_8))
            .contains("형법");
    }

    @Test
    void 같은_키로_다시_쓰면_최신값으로_덮어쓴다(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("drop");
        CleansedJsonDropWriter writer = new CleansedJsonDropWriter(true, dir.toString());

        writer.write("moleg-criminal-law--001692", "[{\"mst\":\"1\"}]");
        writer.write("moleg-criminal-law--001692", "[{\"mst\":\"2\"}]");

        Path expected = dir.resolve("moleg-criminal-law--001692.json");
        assertThat(Files.readString(expected, StandardCharsets.UTF_8)).isEqualTo("[{\"mst\":\"2\"}]");
    }

    @Test
    void 디렉토리_생성이_실패해도_예외를_던지지_않는다(@TempDir Path tempDir) throws IOException {
        // dir 자리에 이미 "파일"이 있으면 createDirectories가 실패함 - 그래도 write()는 조용히 삼켜야 함
        Path blockingFile = tempDir.resolve("not-a-dir");
        Files.writeString(blockingFile, "이미 파일임");
        CleansedJsonDropWriter writer = new CleansedJsonDropWriter(true, blockingFile.toString());

        assertThatCode(() -> writer.write("kma-weather-warning-list", "[]")).doesNotThrowAnyException();
    }
}
