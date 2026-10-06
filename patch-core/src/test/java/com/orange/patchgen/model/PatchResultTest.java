package com.orange.patchgen.model;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PatchResult 单元测试
 *
 * 覆盖三个静态工厂方法、hasPatch() 与压缩比计算。
 */
public class PatchResultTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void success_setsFieldsAndPatchSize() throws IOException {
        File patch = tempFolder.newFile("patch.zip");
        PatchInfo info = new PatchInfo();
        DiffSummary summary = new DiffSummary();

        PatchResult result = PatchResult.success(patch, info, summary);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPatchFile()).isEqualTo(patch);
        assertThat(result.getPatchInfo()).isSameAs(info);
        assertThat(result.getDiffSummary()).isSameAs(summary);
        assertThat(result.getPatchSize()).isEqualTo(patch.length());
    }

    @Test
    public void success_toleratesNullPatchFile() {
        PatchResult result = PatchResult.success(null, new PatchInfo(), new DiffSummary());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPatchFile()).isNull();
        assertThat(result.getPatchSize()).isZero();
    }

    @Test
    public void failure_recordsErrorCodeAndMessage() {
        PatchResult result = PatchResult.failure(1001, "File not found");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(1001);
        assertThat(result.getErrorMessage()).isEqualTo("File not found");
    }

    @Test
    public void noPatchNeeded_isSuccessfulWithEmptySummary() {
        PatchResult result = PatchResult.noPatchNeeded();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDiffSummary()).isNotNull();
        assertThat(result.getDiffSummary().hasChanges()).isFalse();
        assertThat(result.getPatchFile()).isNull();
    }

    @Test
    public void hasPatch_trueOnlyWhenSuccessfulAndFileExists() throws IOException {
        File patch = tempFolder.newFile("patch.zip");
        PatchResult ok = PatchResult.success(patch, new PatchInfo(), new DiffSummary());
        assertThat(ok.hasPatch()).isTrue();

        // 成功但补丁文件不存在 -> false
        PatchResult missingFile = PatchResult.success(
                new File(tempFolder.getRoot(), "does-not-exist.zip"),
                new PatchInfo(), new DiffSummary());
        assertThat(missingFile.hasPatch()).isFalse();

        // 失败结果 -> false
        assertThat(PatchResult.failure(1001, "x").hasPatch()).isFalse();
    }

    @Test
    public void hasPatch_falseAfterFileDeleted() throws IOException {
        File patch = tempFolder.newFile("patch.zip");
        PatchResult result = PatchResult.success(patch, new PatchInfo(), new DiffSummary());
        assertThat(result.hasPatch()).isTrue();

        assertThat(patch.delete()).isTrue();

        assertThat(result.hasPatch()).isFalse();
    }

    @Test
    public void calculateCompressionRatio_computesPatchOverNewApk() {
        PatchResult result = new PatchResult();
        result.setNewApkSize(1000L);
        result.setPatchSize(250L);

        result.calculateCompressionRatio();

        assertThat(result.getCompressionRatio()).isEqualTo(0.25f);
    }

    @Test
    public void calculateCompressionRatio_leavesZeroWhenSizesUnset() {
        PatchResult result = new PatchResult();

        result.calculateCompressionRatio();

        assertThat(result.getCompressionRatio()).isZero();
    }
}
