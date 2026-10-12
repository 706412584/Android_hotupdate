package com.orange.patchgen;

import com.orange.patchgen.differ.DexDiffException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 补丁 dex 连续编号的回归测试。
 *
 * 背景：ART 只按 classes.dex -> classes2.dex -> ... 连续查找 dex。
 * 若补丁 zip 内出现跳号（例如直接沿用 base APK 的 classes8.dex、
 * classes12.dex），补丁中的类会全部无法加载。
 *
 * 因此 generatePatchDexFiles 必须把补丁 dex 重编号为连续序列。
 */
public class PatchGeneratorDexNumberingTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ==================== 自然顺序 ====================

    @Test
    public void dexNameOrder_ordersByNumberNotLexicographically() {
        List<String> names = new ArrayList<>(Arrays.asList(
                "classes10.dex", "classes.dex", "classes2.dex", "classes12.dex", "classes8.dex"));

        names.sort(PatchGenerator.DEX_NAME_ORDER);

        assertThat(names).containsExactly(
                "classes.dex", "classes2.dex", "classes8.dex", "classes10.dex", "classes12.dex");
    }

    @Test
    public void dexNameOrder_treatsPlainClassesAsFirst() {
        assertThat(PatchGenerator.DEX_NAME_ORDER.compare("classes.dex", "classes2.dex"))
                .isNegative();
        assertThat(PatchGenerator.DEX_NAME_ORDER.compare("classes2.dex", "classes.dex"))
                .isPositive();
    }

    // ==================== 重编号 ====================

    @Test
    public void renumber_closesGapFromBaseApkNames() throws IOException, DexDiffException {
        File dir = tempFolder.newFolder("patch_dex");
        // 模拟根因场景：base APK 只有 classes8.dex 和 classes12.dex 有变更
        List<File> input = Arrays.asList(
                createFile(dir, "classes8.dex", "eight"),
                createFile(dir, "classes12.dex", "twelve"));

        List<File> result = PatchGenerator.renumberPatchDexFiles(input);

        assertThat(result).extracting(File::getName)
                .containsExactly("classes.dex", "classes2.dex");
        assertThat(dir.listFiles()).extracting(File::getName)
                .containsExactlyInAnyOrder("classes.dex", "classes2.dex");
    }

    @Test
    public void renumber_preservesContentOfEachDex() throws IOException, DexDiffException {
        File dir = tempFolder.newFolder("patch_dex");
        List<File> input = Arrays.asList(
                createFile(dir, "classes8.dex", "eight"),
                createFile(dir, "classes12.dex", "twelve"));

        List<File> result = PatchGenerator.renumberPatchDexFiles(input);

        assertThat(read(result.get(0))).isEqualTo("eight");
        assertThat(read(result.get(1))).isEqualTo("twelve");
    }

    @Test
    public void renumber_singleDexBecomesClassesDex() throws IOException, DexDiffException {
        File dir = tempFolder.newFolder("patch_dex");
        List<File> input = Arrays.asList(createFile(dir, "classes12.dex", "only"));

        List<File> result = PatchGenerator.renumberPatchDexFiles(input);

        assertThat(result).extracting(File::getName).containsExactly("classes.dex");
        assertThat(read(result.get(0))).isEqualTo("only");
    }

    @Test
    public void renumber_alreadyContiguousIsNoOp() throws IOException, DexDiffException {
        File dir = tempFolder.newFolder("patch_dex");
        List<File> input = Arrays.asList(
                createFile(dir, "classes.dex", "one"),
                createFile(dir, "classes2.dex", "two"));

        List<File> result = PatchGenerator.renumberPatchDexFiles(input);

        assertThat(result).extracting(File::getName)
                .containsExactly("classes.dex", "classes2.dex");
        assertThat(read(result.get(0))).isEqualTo("one");
        assertThat(read(result.get(1))).isEqualTo("two");
    }

    @Test
    public void renumber_emptyInputReturnsEmpty() throws DexDiffException {
        assertThat(PatchGenerator.renumberPatchDexFiles(new ArrayList<>())).isEmpty();
    }

    // ==================== helpers ====================

    private static File createFile(File dir, String name, String content) throws IOException {
        File file = new File(dir, name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
