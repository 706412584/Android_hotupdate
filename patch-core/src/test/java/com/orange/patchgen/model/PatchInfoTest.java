package com.orange.patchgen.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PatchInfo 单元测试
 *
 * 覆盖 patch.json 的序列化往返与 isValid() 的字段校验规则。
 */
public class PatchInfoTest {

    private static PatchInfo validPatchInfo() {
        PatchInfo info = new PatchInfo();
        info.setPatchId("patch_1");
        info.setPatchVersion("1.1");
        info.setBaseVersion("1.0");
        info.setTargetVersion("1.1");
        info.setMd5("0123456789abcdef0123456789abcdef"); // 32 位
        return info;
    }

    @Test
    public void constructor_initializesChangesAndCreateTime() {
        PatchInfo info = new PatchInfo();

        assertThat(info.getChanges()).isNotNull();
        assertThat(info.getCreateTime()).isGreaterThan(0);
    }

    @Test
    public void isValid_trueForFullyPopulatedInfo() {
        assertThat(validPatchInfo().isValid()).isTrue();
    }

    @Test
    public void isValid_falseWhenPatchIdMissing() {
        PatchInfo info = validPatchInfo();
        info.setPatchId(null);
        assertThat(info.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenPatchIdEmpty() {
        PatchInfo info = validPatchInfo();
        info.setPatchId("");
        assertThat(info.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenPatchVersionMissing() {
        PatchInfo info = validPatchInfo();
        info.setPatchVersion(null);
        assertThat(info.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenBaseVersionMissing() {
        PatchInfo info = validPatchInfo();
        info.setBaseVersion(null);
        assertThat(info.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenTargetVersionMissing() {
        PatchInfo info = validPatchInfo();
        info.setTargetVersion(null);
        assertThat(info.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenMd5Missing() {
        PatchInfo info = validPatchInfo();
        info.setMd5(null);
        assertThat(info.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenMd5WrongLength() {
        // isValid() 要求 md5 长度恰为 32；过短或过长都应判定无效
        PatchInfo tooShort = validPatchInfo();
        tooShort.setMd5("abc");
        assertThat(tooShort.isValid()).isFalse();

        PatchInfo tooLong = validPatchInfo();
        tooLong.setMd5("0123456789abcdef0123456789abcdef00"); // 34 位
        assertThat(tooLong.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenChangesNull() {
        PatchInfo info = validPatchInfo();
        info.setChanges(null);
        assertThat(info.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenCreateTimeNotPositive() {
        PatchInfo info = validPatchInfo();
        info.setCreateTime(0);
        assertThat(info.isValid()).isFalse();
    }

    @Test
    public void jsonRoundTrip_preservesScalarFields() {
        PatchInfo original = validPatchInfo();
        original.setPackageName("com.example.app");
        original.setBaseVersionCode(10);
        original.setTargetVersionCode(11);
        original.setPatchMode("full_dex");
        original.setSha256("deadbeef");
        original.setDescription("测试补丁");
        original.setFileSize(12345L);
        original.setCreateTime(1700000000000L);

        PatchInfo restored = PatchInfo.fromJson(original.toJson());

        assertThat(restored.getPatchId()).isEqualTo(original.getPatchId());
        assertThat(restored.getPatchVersion()).isEqualTo(original.getPatchVersion());
        assertThat(restored.getPackageName()).isEqualTo(original.getPackageName());
        assertThat(restored.getBaseVersion()).isEqualTo(original.getBaseVersion());
        assertThat(restored.getBaseVersionCode()).isEqualTo(original.getBaseVersionCode());
        assertThat(restored.getTargetVersion()).isEqualTo(original.getTargetVersion());
        assertThat(restored.getTargetVersionCode()).isEqualTo(original.getTargetVersionCode());
        assertThat(restored.getPatchMode()).isEqualTo(original.getPatchMode());
        assertThat(restored.getMd5()).isEqualTo(original.getMd5());
        assertThat(restored.getSha256()).isEqualTo(original.getSha256());
        assertThat(restored.getDescription()).isEqualTo(original.getDescription());
        assertThat(restored.getFileSize()).isEqualTo(original.getFileSize());
        assertThat(restored.getCreateTime()).isEqualTo(original.getCreateTime());
    }

    @Test
    public void jsonRoundTrip_preservesNestedChanges() {
        PatchInfo original = validPatchInfo();
        original.getChanges().getDex().addModified("com.example.A");
        original.getChanges().getDex().addAdded("com.example.B");
        original.getChanges().getDex().addDeleted("com.example.C");
        original.getChanges().getResources().addModified("res/layout/main.xml");

        PatchInfo restored = PatchInfo.fromJson(original.toJson());

        assertThat(restored.getChanges()).isNotNull();
        assertThat(restored.getChanges().getDex().getModified()).containsExactly("com.example.A");
        assertThat(restored.getChanges().getDex().getAdded()).containsExactly("com.example.B");
        assertThat(restored.getChanges().getDex().getDeleted()).containsExactly("com.example.C");
        assertThat(restored.getChanges().getResources().getModified())
                .containsExactly("res/layout/main.xml");
    }
}
