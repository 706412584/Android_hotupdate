package com.orange.patchgen.parser;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ArscParser} 单元测试。
 *
 * 用 {@link ArscFixtureBuilder} 构造最小 arsc 二进制，覆盖：
 * - UTF-8 / UTF-16 两种字符串池；
 * - 多类型、多包下的 ID 提取（0xPPTTEEEE 组装）；
 * - 损坏结构的报错。
 *
 * 另有一条用真实 APK（test_assets/app-v1.5.apk，如存在）的冒烟断言，
 * 验证解析器对 aapt2 实际产物可用。
 */
public class ArscParserTest {

    // ==================== 基本提取 ====================

    @Test
    public void parse_extractsIdToNameMapping() throws IOException, ParseException {
        byte[] arsc = new ArscFixtureBuilder()
                .add("string", "app_name", "hello")
                .add("layout", "main")
                .build();

        ArscResourceIds ids = ArscParser.parse(arsc);

        assertThat(ids.size()).isEqualTo(3);
        // packageId=0x7F, typeId=1(string), entry=0
        assertThat(ids.getName(0x7F010000)).isEqualTo("string/app_name");
        assertThat(ids.getName(0x7F010001)).isEqualTo("string/hello");
        // typeId=2(layout), entry=0
        assertThat(ids.getName(0x7F020000)).isEqualTo("layout/main");
    }

    @Test
    public void parse_buildsReverseMapping() throws IOException, ParseException {
        byte[] arsc = new ArscFixtureBuilder()
                .add("string", "app_name")
                .build();

        ArscResourceIds ids = ArscParser.parse(arsc);

        assertThat(ids.getId("string/app_name")).isEqualTo(0x7F010000);
        assertThat(ids.getId("string/missing")).isNull();
    }

    @Test
    public void parse_supportsUtf16StringPools() throws IOException, ParseException {
        byte[] arsc = new ArscFixtureBuilder()
                .utf8Pools(false)
                .add("string", "app_name", "中文资源名")
                .build();

        ArscResourceIds ids = ArscParser.parse(arsc);

        assertThat(ids.getName(0x7F010000)).isEqualTo("string/app_name");
        assertThat(ids.getName(0x7F010001)).isEqualTo("string/中文资源名");
    }

    @Test
    public void parse_supportsLongStringsRequiringTwoByteLengthPrefix() throws IOException, ParseException {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longName.append('a');
        }
        byte[] arsc = new ArscFixtureBuilder()
                .add("string", longName.toString())
                .build();

        ArscResourceIds ids = ArscParser.parse(arsc);

        assertThat(ids.getName(0x7F010000)).isEqualTo("string/" + longName);
    }

    // ==================== 多类型 ====================

    @Test
    public void parse_handlesManyTypesWithCorrectTypeIds() throws IOException, ParseException {
        byte[] arsc = new ArscFixtureBuilder()
                .add("anim", "fade")
                .add("attr", "alpha")
                .add("color", "red", "green")
                .add("string", "name")
                .build();

        ArscResourceIds ids = ArscParser.parse(arsc);

        assertThat(ids.getName(0x7F010000)).isEqualTo("anim/fade");
        assertThat(ids.getName(0x7F020000)).isEqualTo("attr/alpha");
        assertThat(ids.getName(0x7F030000)).isEqualTo("color/red");
        assertThat(ids.getName(0x7F030001)).isEqualTo("color/green");
        assertThat(ids.getName(0x7F040000)).isEqualTo("string/name");
    }

    // ==================== 错误处理 ====================

    @Test
    public void parse_rejectsEmptyInput() {
        assertThatThrownBy(() -> ArscParser.parse(new byte[0]))
                .isInstanceOf(ParseException.class);
        assertThatThrownBy(() -> ArscParser.parse((byte[]) null))
                .isInstanceOf(ParseException.class);
    }

    @Test
    public void parse_rejectsNonResTableHeader() {
        byte[] junk = new byte[64];
        junk[0] = 0x50; // 'P'，不是 ResTable
        junk[1] = 0x4B;

        assertThatThrownBy(() -> ArscParser.parse(junk))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("ResTable");
    }

    @Test
    public void parse_rejectsTruncatedChunk() throws IOException {
        byte[] valid = new ArscFixtureBuilder().add("string", "a").build();
        byte[] truncated = new byte[valid.length / 2];
        System.arraycopy(valid, 0, truncated, 0, truncated.length);

        assertThatThrownBy(() -> ArscParser.parse(truncated))
                .isInstanceOf(ParseException.class);
    }

    // ==================== 真实产物冒烟 ====================

    @Test
    public void parse_realApkArsc_whenFixtureAvailable() throws Exception {
        java.io.File apk = new java.io.File("test_assets/app-v1.5.apk");
        if (!apk.exists()) {
            // 测试工作目录是模块目录，测试资产在仓库根
            apk = new java.io.File("../test_assets/app-v1.5.apk");
        }
        if (!apk.exists()) {
            return; // 测试资产不随仓库分发时可跳过
        }
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apk)) {
            java.util.zip.ZipEntry entry = zip.getEntry("resources.arsc");
            assertThat(entry).isNotNull();
            try (java.io.InputStream is = zip.getInputStream(entry)) {
                byte[] data = readAll(is);
                ArscResourceIds ids = ArscParser.parse(data);
                // 实测：app-v1.5.apk 有 5137 个资源 ID（与自研解析比对一致）
                assertThat(ids.size()).isGreaterThan(5000);
                assertThat(ids.getId("string/app_name")).isNotNull();
            }
        }
    }

    /**
     * 真实数据：app-v1.5.apk → app-v1.6.apk 的 5137 个资源 ID 全部对齐
     * （实测 0 漂移，对应 patch.json 的 resourceIdsStable=true 路径）。
     */
    @Test
    public void isStableAgainst_realApkPair_isStable() throws Exception {
        java.io.File base = new java.io.File("../test_assets/app-v1.5.apk");
        java.io.File updated = new java.io.File("../test_assets/app-v1.6.apk");
        if (!base.exists() || !updated.exists()) {
            return;
        }
        ArscResourceIds baseIds = ArscParser.parse(readArscFromApk(base));
        ArscResourceIds newIds = ArscParser.parse(readArscFromApk(updated));

        assertThat(ArscResourceIds.isStableAgainst(baseIds, newIds)).isTrue();
        assertThat(ArscResourceIds.findMoved(baseIds, newIds, 10)).isEmpty();
    }

    private static byte[] readArscFromApk(java.io.File apk) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apk)) {
            java.util.zip.ZipEntry entry = zip.getEntry("resources.arsc");
            try (java.io.InputStream is = zip.getInputStream(entry)) {
                return readAll(is);
            }
        }
    }

    private static byte[] readAll(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
