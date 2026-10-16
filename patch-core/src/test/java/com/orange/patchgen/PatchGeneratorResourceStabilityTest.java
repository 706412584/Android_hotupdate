package com.orange.patchgen;

import com.orange.patchgen.parser.ArscFixtureBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资源 ID 稳定性判定的单元测试。
 *
 * 语义（与 {@code ArscResourceIds.isStableAgainst} 一致）：
 * - 共有资源名必须映射到同一 ID（漂移即不稳定）；
 * - 新增资源不破坏稳定性（占用新 ID，不影响旧内联值）；
 * - 删除资源视为不稳定（旧代码引用它会失败，交由消费侧拒绝）。
 *
 * 不稳定时 patch.json 必须写 {@code resourceIdsStable=false}，消费侧
 * （ACS：optBoolean("resourceIdsStable", false)）据此拒绝含资源的补丁。
 */
public class PatchGeneratorResourceStabilityTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ==================== 四种情形 ====================

    @Test
    public void stable_whenSameResourcesKeepSameIds() throws Exception {
        File base = writeArsc("base.arsc", new ArscFixtureBuilder()
                .add("string", "app_name", "hello")
                .add("layout", "main"));
        File updated = writeArsc("updated.arsc", new ArscFixtureBuilder()
                .add("string", "app_name", "hello")
                .add("layout", "main"));

        PatchGenerator.ResourceIdStability result =
                PatchGenerator.checkResourceIdStability(base, updated);

        assertThat(result.stable).isTrue();
        assertThat(result.isUnstable()).isFalse();
    }

    @Test
    public void unstable_whenExistingResourceMovesToDifferentId() throws Exception {
        // base: string/[app_name, hello] -> app_name=0x7F010000
        File base = writeArsc("base.arsc", new ArscFixtureBuilder()
                .add("string", "app_name", "hello"));
        // new: 在 string 前部插入新资源，旧资源整体后移（真实漂移场景）
        File updated = writeArsc("updated.arsc", new ArscFixtureBuilder()
                .add("string", "inserted", "app_name", "hello"));

        PatchGenerator.ResourceIdStability result =
                PatchGenerator.checkResourceIdStability(base, updated);

        assertThat(result.stable).isFalse();
        assertThat(result.isUnstable()).isTrue();
        assertThat(result.detail).contains("string/app_name");
    }

    @Test
    public void stable_whenNewResourcesAreAppended() throws Exception {
        // 新增资源占用新 ID，不改变旧资源的 ID —— 补丁仍然安全
        File base = writeArsc("base.arsc", new ArscFixtureBuilder()
                .add("string", "app_name", "hello"));
        File updated = writeArsc("updated.arsc", new ArscFixtureBuilder()
                .add("string", "app_name", "hello", "brand_new"));

        PatchGenerator.ResourceIdStability result =
                PatchGenerator.checkResourceIdStability(base, updated);

        assertThat(result.stable).isTrue();
    }

    @Test
    public void unstable_whenResourceDeleted() throws Exception {
        File base = writeArsc("base.arsc", new ArscFixtureBuilder()
                .add("string", "app_name", "hello"));
        File updated = writeArsc("updated.arsc", new ArscFixtureBuilder()
                .add("string", "app_name"));

        PatchGenerator.ResourceIdStability result =
                PatchGenerator.checkResourceIdStability(base, updated);

        assertThat(result.stable).isFalse();
        assertThat(result.detail).contains("string/hello");
    }

    // ==================== 失败路径 ====================

    @Test
    public void unstable_whenArscMissing() throws Exception {
        File base = writeArsc("base.arsc", new ArscFixtureBuilder()
                .add("string", "app_name"));
        File missing = new File(tempFolder.getRoot(), "does-not-exist.arsc");

        PatchGenerator.ResourceIdStability result =
                PatchGenerator.checkResourceIdStability(base, missing);

        assertThat(result.stable).isFalse();
        assertThat(result.detail).contains("解析失败");
    }

    @Test
    public void unstable_whenArscCorrupted() throws Exception {
        File base = writeArsc("base.arsc", new ArscFixtureBuilder()
                .add("string", "app_name"));
        File corrupted = tempFolder.newFile("corrupted.arsc");
        Files.write(corrupted.toPath(), new byte[]{0x00, 0x01, 0x02, 0x03});

        PatchGenerator.ResourceIdStability result =
                PatchGenerator.checkResourceIdStability(base, corrupted);

        assertThat(result.stable).isFalse();
    }

    // ==================== helpers ====================

    private File writeArsc(String name, ArscFixtureBuilder builder) throws IOException {
        File file = tempFolder.newFile(name);
        Files.write(file.toPath(), builder.build());
        return file;
    }
}
