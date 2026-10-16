package com.orange.patchgen;

import com.orange.patchgen.model.PatchResult;
import com.orange.patchgen.parser.ArscFixtureBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端验证：真实 APK 对生成补丁时，patch.json 的资源稳定性标记符合预期。
 *
 * <p>三类场景：
 * <ol>
 *   <li>真实对 app-v1.5→app-v1.6（dex-only 差异，无资源变更）：
 *       不写 resourceIdsStable（该标记对无资源补丁无意义）；</li>
 *   <li>合成「资源变化 + ID 对齐」：resourceIdsStable=true；</li>
 *   <li>合成「资源变化 + ID 漂移」：resourceIdsStable=false。</li>
 * </ol>
 *
 * <p>合成场景用真实 APK 换装 {@link ArscFixtureBuilder} 构造的 arsc 并改一个 res/
 * 文件实现——manifest 与 dex 保持真实，避免手工构造二进制 XML。
 * 测试资产（test_assets/*.apk）不随仓库分发时自动跳过。
 */
public class PatchGeneratorResourceIdsE2ETest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ==================== 场景 1：真实对，无资源变更 ====================

    @Test
    public void realApkPair_withoutResourceChanges_omitsStabilityFlag() throws Exception {
        File base = locate("app-v1.5.apk");
        File updated = locate("app-v1.6.apk");
        if (base == null || updated == null) {
            return;
        }

        String patchJson = generatePatchJson(base, updated);

        // v1.5→v1.6 只有 dex 与 baseline.prof 差异，resources 为空 → 不写标记
        assertThat(patchJson).doesNotContain("resourceIdsStable");
        assertThat(patchJson).doesNotContain("rFieldsFinal");
    }

    // ==================== 场景 2：资源变化 + ID 对齐 ====================

    @Test
    public void syntheticPair_withAlignedResourceIds_writesStableTrue() throws Exception {
        File base = locate("app-v1.5.apk");
        File updated = locate("app-v1.6.apk");
        if (base == null || updated == null) {
            return;
        }
        byte[] arsc = new ArscFixtureBuilder()
                .add("string", "app_name", "hello")
                .add("layout", "main")
                .build();

        File baseApk = repack(tempFolder.getRoot(), base, "base-aligned.apk", arsc, null);
        File newApk = repack(tempFolder.getRoot(), updated, "new-aligned.apk", arsc,
                "MODIFIED-RES-CONTENT");

        String patchJson = generatePatchJson(baseApk, newApk);

        assertThat(patchJson).contains("\"resourceIdsStable\": true");
        // R 类字段实测非 final（AGP 8 产物）
        assertThat(patchJson).contains("\"rFieldsFinal\": false");
    }

    // ==================== 场景 3：资源变化 + ID 漂移 ====================

    @Test
    public void syntheticPair_withDriftedResourceIds_writesStableFalse() throws Exception {
        File base = locate("app-v1.5.apk");
        File updated = locate("app-v1.6.apk");
        if (base == null || updated == null) {
            return;
        }
        // base: string/[app_name, hello]；new: 前插 inserted → 既有资源 ID 全部后移
        byte[] baseArsc = new ArscFixtureBuilder()
                .add("string", "app_name", "hello")
                .build();
        byte[] newArsc = new ArscFixtureBuilder()
                .add("string", "inserted", "app_name", "hello")
                .build();

        File baseApk = repack(tempFolder.getRoot(), base, "base-drift.apk", baseArsc, null);
        File newApk = repack(tempFolder.getRoot(), updated, "new-drift.apk", newArsc,
                "MODIFIED-RES-CONTENT");

        String patchJson = generatePatchJson(baseApk, newApk);

        assertThat(patchJson).contains("\"resourceIdsStable\": false");
    }

    // ==================== helpers ====================

    private String generatePatchJson(File baseApk, File newApk) throws Exception {
        File output = new File(tempFolder.getRoot(),
                "patch-" + baseApk.getName() + "-" + newApk.getName() + ".zip");
        PatchGenerator generator = new PatchGenerator.Builder()
                .baseApk(baseApk)
                .newApk(newApk)
                .output(output)
                .build();

        PatchResult result = generator.generate();

        assertThat(result.isSuccess()).isTrue();
        assertThat(output).exists();
        return readEntry(output, "patch.json");
    }

    /**
     * 以 {@code source} 为基础复制出一个 APK：替换 resources.arsc；
     * {@code resContent} 非 null 时改写 res/-1.xml（制造一个真实的资源变更）。
     */
    private static File repack(File outputDir, File source, String name, byte[] arsc,
                               String resContent) throws IOException {
        File output = new File(outputDir, name);
        try (ZipFile zip = new ZipFile(source);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] data;
                if ("resources.arsc".equals(entry.getName())) {
                    data = arsc;
                } else if (resContent != null && "res/-1.xml".equals(entry.getName())) {
                    data = resContent.getBytes("UTF-8");
                } else {
                    data = readAll(zip.getInputStream(entry));
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                zos.putNextEntry(copy);
                zos.write(data);
                zos.closeEntry();
            }
        }
        return output;
    }

    private static File locate(String name) {
        File file = new File("../test_assets/" + name);
        if (file.exists()) {
            return file;
        }
        file = new File("test_assets/" + name);
        return file.exists() ? file : null;
    }

    private static String readEntry(File zipFile, String name) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(name);
            assertThat(entry).as("补丁内应含 " + name).isNotNull();
            try (InputStream is = zip.getInputStream(entry)) {
                return new String(readAll(is), "UTF-8");
            }
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
