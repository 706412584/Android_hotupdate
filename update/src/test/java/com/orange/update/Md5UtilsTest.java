package com.orange.update;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Md5Utils 单元测试
 *
 * 补丁完整性依赖 MD5 比对，因此这里用已知向量锁定其行为。
 * 本类无 Android 依赖，可直接以普通 JUnit 运行。
 */
public class Md5UtilsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File fileWith(String name, byte[] content) throws IOException {
        File f = tempFolder.newFile(name);
        Files.write(f.toPath(), content);
        return f;
    }

    // ---------- calculateMd5(byte[]) ----------

    @Test
    public void calculateMd5_matchesKnownVectors() {
        // 标准测试向量，用于锁定实现未被意外改动
        assertThat(Md5Utils.calculateMd5(new byte[0]))
                .isEqualTo("d41d8cd98f00b204e9800998ecf8427e");

        assertThat(Md5Utils.calculateMd5("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("900150983cd24fb0d6963f7d28e17f72");

        assertThat(Md5Utils.calculateMd5(
                "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("9e107d9d372bb6826bd81d3542a419d6");
    }

    @Test
    public void calculateMd5_returnsLowercaseHexOfFixedLength() {
        String md5 = Md5Utils.calculateMd5("hello".getBytes(StandardCharsets.UTF_8));

        assertThat(md5).hasSize(32);
        assertThat(md5).isEqualTo(md5.toLowerCase());
        assertThat(md5).matches("[0-9a-f]{32}");
    }

    @Test
    public void calculateMd5_isDeterministicForSameInput() {
        byte[] data = "repeatable".getBytes(StandardCharsets.UTF_8);

        assertThat(Md5Utils.calculateMd5(data)).isEqualTo(Md5Utils.calculateMd5(data));
    }

    @Test
    public void calculateMd5_differsForDifferentInput() {
        assertThat(Md5Utils.calculateMd5("a".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(Md5Utils.calculateMd5("b".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void calculateMd5_throwsForNullByteArray() {
        assertThatThrownBy(() -> Md5Utils.calculateMd5((byte[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Data cannot be null");
    }

    // ---------- calculateMd5(File) ----------

    @Test
    public void calculateMd5_fileMatchesByteArrayResult() throws IOException {
        byte[] content = "file content".getBytes(StandardCharsets.UTF_8);
        File f = fileWith("a.bin", content);

        assertThat(Md5Utils.calculateMd5(f))
                .isEqualTo(Md5Utils.calculateMd5(content));
    }

    @Test
    public void calculateMd5_handlesFileLargerThanBuffer() throws IOException {
        // BUFFER_SIZE 为 8192，这里用跨缓冲区的数据验证流式读取正确
        byte[] big = new byte[8192 * 3 + 17];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i % 251);
        }
        File f = fileWith("big.bin", big);

        assertThat(Md5Utils.calculateMd5(f)).isEqualTo(Md5Utils.calculateMd5(big));
    }

    @Test
    public void calculateMd5_emptyFileMatchesEmptyByteArray() throws IOException {
        File f = fileWith("empty.bin", new byte[0]);

        assertThat(Md5Utils.calculateMd5(f))
                .isEqualTo(Md5Utils.calculateMd5(new byte[0]));
    }

    @Test
    public void calculateMd5_throwsForNullFile() {
        assertThatThrownBy(() -> Md5Utils.calculateMd5((File) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File cannot be null");
    }

    @Test
    public void calculateMd5_throwsForMissingFile() {
        File missing = new File(tempFolder.getRoot(), "nope.bin");

        assertThatThrownBy(() -> Md5Utils.calculateMd5(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File does not exist");
    }

    @Test
    public void calculateMd5_throwsForDirectory() throws IOException {
        File dir = tempFolder.newFolder("adir");

        assertThatThrownBy(() -> Md5Utils.calculateMd5(dir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path is not a file");
    }

    // ---------- verifyMd5 ----------

    @Test
    public void verifyMd5_trueForMatchingHash() throws IOException {
        File f = fileWith("v.bin", "abc".getBytes(StandardCharsets.UTF_8));

        assertThat(Md5Utils.verifyMd5(f, "900150983cd24fb0d6963f7d28e17f72")).isTrue();
    }

    @Test
    public void verifyMd5_isCaseInsensitive() throws IOException {
        File f = fileWith("v2.bin", "abc".getBytes(StandardCharsets.UTF_8));

        assertThat(Md5Utils.verifyMd5(f, "900150983CD24FB0D6963F7D28E17F72")).isTrue();
    }

    @Test
    public void verifyMd5_falseForMismatchedHash() throws IOException {
        File f = fileWith("v3.bin", "abc".getBytes(StandardCharsets.UTF_8));

        assertThat(Md5Utils.verifyMd5(f, "00000000000000000000000000000000")).isFalse();
    }

    @Test
    public void verifyMd5_throwsForNullOrEmptyExpected() throws IOException {
        File f = fileWith("v4.bin", "abc".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Md5Utils.verifyMd5(f, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected MD5 cannot be null or empty");

        assertThatThrownBy(() -> Md5Utils.verifyMd5(f, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected MD5 cannot be null or empty");
    }

    @Test
    public void verifyMd5_detectsTamperedContent() throws IOException {
        // 模拟补丁被篡改：内容改变后原 MD5 必须校验失败
        File f = fileWith("patch.bin", "original".getBytes(StandardCharsets.UTF_8));
        String originalMd5 = Md5Utils.calculateMd5(f);

        Files.write(f.toPath(), "tampered".getBytes(StandardCharsets.UTF_8));

        assertThat(Md5Utils.verifyMd5(f, originalMd5)).isFalse();
    }
}
