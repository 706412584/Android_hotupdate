package com.orange.patchgen.parser;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableField;
import org.jf.dexlib2.immutable.value.ImmutableIntEncodedValue;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RFieldAnalyzer} 单元测试。
 *
 * 用 dexlib2 构造带/不带 ACC_FINAL 的假 R 类 dex，验证检测逻辑：
 * - final 静态 int 字段（旧版 AGP / nonFinalResIds=false）→ hasInlineRisk()；
 * - 非 final（AGP 8+ 默认）→ 无风险；
 * - 非 R 类、非 int 字段、非静态字段不参与统计。
 *
 * 真实产物验证：test_assets/app-v1.5.apk 的 R 类字段全部非 final
 * （AGP 8.12 默认 nonFinalResIds=true，实测 13444 个静态 int 字段 0 个 final）。
 */
public class RFieldAnalyzerTest {

    private static final int ACC_PUBLIC = 0x1;
    private static final int ACC_STATIC = 0x8;
    private static final int ACC_FINAL = 0x10;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ==================== final 检测 ====================

    @Test
    public void analyzeDex_detectsFinalRFields() throws IOException {
        File dex = writeDexWithRClass("final", ACC_PUBLIC | ACC_STATIC | ACC_FINAL);

        RFieldAnalyzer.Result result = RFieldAnalyzer.analyzeDex(dex);

        assertThat(result.getRClasses()).isEqualTo(1);
        assertThat(result.getFinalIntFields()).isEqualTo(2);
        assertThat(result.getNonFinalIntFields()).isZero();
        assertThat(result.hasInlineRisk()).isTrue();
        assertThat(result.isDetected()).isTrue();
    }

    @Test
    public void analyzeDex_detectsNonFinalRFields() throws IOException {
        // AGP 8+ 默认 nonFinalResIds=true：public static int（无 final）
        File dex = writeDexWithRClass("nonfinal", ACC_PUBLIC | ACC_STATIC);

        RFieldAnalyzer.Result result = RFieldAnalyzer.analyzeDex(dex);

        assertThat(result.getFinalIntFields()).isZero();
        assertThat(result.getNonFinalIntFields()).isEqualTo(2);
        assertThat(result.hasInlineRisk()).isFalse();
        assertThat(result.isDetected()).isTrue();
    }

    @Test
    public void analyzeDex_countsBothKindsSeparately() throws IOException {
        // 混合场景：一个 R$string 为 final，一个 R$id 为非 final
        DexPool pool = new DexPool(Opcodes.forDexVersion(38));
        pool.internClass(rClass("Lcom/example/R$string;", ACC_PUBLIC | ACC_STATIC | ACC_FINAL));
        pool.internClass(rClass("Lcom/example/R$id;", ACC_PUBLIC | ACC_STATIC));
        File dex = writePool(pool, "mixed.dex");

        RFieldAnalyzer.Result result = RFieldAnalyzer.analyzeDex(dex);

        assertThat(result.getRClasses()).isEqualTo(2);
        assertThat(result.getFinalIntFields()).isEqualTo(2);
        assertThat(result.getNonFinalIntFields()).isEqualTo(2);
        assertThat(result.hasInlineRisk()).isTrue();
    }

    // ==================== 排除项 ====================

    @Test
    public void analyzeDex_ignoresNonRClasses() throws IOException {
        DexPool pool = new DexPool(Opcodes.forDexVersion(38));
        List<Field> fields = new ArrayList<>();
        fields.add(new ImmutableField("Lcom/example/Other;", "VALUE", "I",
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL, new ImmutableIntEncodedValue(1), null, null));
        pool.internClass(new ImmutableClassDef("Lcom/example/Other;", ACC_PUBLIC,
                "Ljava/lang/Object;", null, null, null, fields, null, null, null));
        File dex = writePool(pool, "other.dex");

        RFieldAnalyzer.Result result = RFieldAnalyzer.analyzeDex(dex);

        assertThat(result.getRClasses()).isZero();
        assertThat(result.isDetected()).isFalse();
    }

    @Test
    public void analyzeDex_ignoresNonIntAndNonStaticFields() throws IOException {
        DexPool pool = new DexPool(Opcodes.forDexVersion(38));
        List<Field> fields = new ArrayList<>();
        // int[] styleable：不是 int，不参与（数组不会被编译期内联）
        fields.add(new ImmutableField("Lcom/example/R$styleable;", "Button", "[I",
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL, null, null, null));
        // 实例字段（非 static）
        fields.add(new ImmutableField("Lcom/example/R$styleable;", "instance", "I",
                ACC_PUBLIC | ACC_FINAL, new ImmutableIntEncodedValue(1), null, null));
        pool.internClass(new ImmutableClassDef("Lcom/example/R$styleable;", ACC_PUBLIC,
                "Ljava/lang/Object;", null, null, null, fields, null, null, null));
        File dex = writePool(pool, "styleable.dex");

        RFieldAnalyzer.Result result = RFieldAnalyzer.analyzeDex(dex);

        assertThat(result.getRClasses()).isEqualTo(1);
        assertThat(result.getFinalIntFields()).isZero();
        assertThat(result.getNonFinalIntFields()).isZero();
        assertThat(result.isDetected()).isFalse();
    }

    // ==================== 目录聚合 ====================

    @Test
    public void analyze_aggregatesAcrossDexFiles() throws IOException {
        File dir = tempFolder.newFolder("extract");
        writePoolToDir(dir, "classes.dex", ACC_PUBLIC | ACC_STATIC | ACC_FINAL);
        writePoolToDir(dir, "classes2.dex", ACC_PUBLIC | ACC_STATIC);
        // 非 classes*.dex 应被忽略
        writePoolToDir(dir, "other.dex", ACC_PUBLIC | ACC_STATIC | ACC_FINAL);

        RFieldAnalyzer.Result result = RFieldAnalyzer.analyze(dir);

        assertThat(result.getFinalIntFields()).isEqualTo(2);   // 只有 classes.dex
        assertThat(result.getNonFinalIntFields()).isEqualTo(2); // 只有 classes2.dex
        assertThat(result.hasInlineRisk()).isTrue();
    }

    @Test
    public void analyze_returnsEmptyForMissingDir() {
        RFieldAnalyzer.Result result = RFieldAnalyzer.analyze(new File("no-such-dir"));

        assertThat(result.isDetected()).isFalse();
        assertThat(result.hasInlineRisk()).isFalse();
    }

    // ==================== 类型描述符判定 ====================

    @Test
    public void isRClass_matchesExpectedDescriptors() {
        assertThat(RFieldAnalyzer.isRClass("Lcom/example/R;")).isTrue();
        assertThat(RFieldAnalyzer.isRClass("Lcom/example/R$string;")).isTrue();
        assertThat(RFieldAnalyzer.isRClass("Landroidx/appcompat/R$anim;")).isTrue();
        assertThat(RFieldAnalyzer.isRClass("Lcom/example/Run;")).isFalse();
        assertThat(RFieldAnalyzer.isRClass("Lcom/example/AR;")).isFalse();
        assertThat(RFieldAnalyzer.isRClass("Lcom/example/Rx$foo;")).isFalse();
        assertThat(RFieldAnalyzer.isRClass(null)).isFalse();
    }

    // ==================== 真实产物验证 ====================

    @Test
    public void analyze_realApkDexes_reportsNonFinal() throws Exception {
        // app-v1.5.apk 由 AGP 8.12 构建（默认 nonFinalResIds=true）
        File apk = new File("../test_assets/app-v1.5.apk");
        if (!apk.exists()) {
            return; // 测试资产不随仓库分发时可跳过
        }
        File dir = tempFolder.newFolder("real-dex");
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apk)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (!entry.getName().matches("classes\\d*\\.dex")) {
                    continue;
                }
                File out = new File(dir, entry.getName());
                try (java.io.InputStream is = zip.getInputStream(entry);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        fos.write(buf, 0, n);
                    }
                }
            }
        }

        RFieldAnalyzer.Result result = RFieldAnalyzer.analyze(dir);

        // 实测：195 个 R 类、13444 个静态 int 字段、0 个 final
        assertThat(result.isDetected()).isTrue();
        assertThat(result.getNonFinalIntFields()).isGreaterThan(10_000);
        assertThat(result.getFinalIntFields()).isZero();
        assertThat(result.hasInlineRisk()).isFalse();
    }

    // ==================== helpers ====================

    private File writeDexWithRClass(String name, int fieldFlags) throws IOException {
        DexPool pool = new DexPool(Opcodes.forDexVersion(38));
        pool.internClass(rClass("Lcom/example/R$string;", fieldFlags));
        return writePool(pool, name + ".dex");
    }

    private static ClassDef rClass(String type, int fieldFlags) {
        List<Field> fields = new ArrayList<>();
        fields.add(new ImmutableField(type, "app_name", "I", fieldFlags,
                new ImmutableIntEncodedValue(0x7F010000), null, null));
        fields.add(new ImmutableField(type, "hello", "I", fieldFlags,
                new ImmutableIntEncodedValue(0x7F010001), null, null));
        return new ImmutableClassDef(type, ACC_PUBLIC, "Ljava/lang/Object;",
                null, null, null, fields, null, null, null);
    }

    private File writePool(DexPool pool, String name) throws IOException {
        File out = new File(tempFolder.getRoot(), name);
        pool.writeTo(new FileDataStore(out));
        return out;
    }

    private void writePoolToDir(File dir, String name, int fieldFlags) throws IOException {
        DexPool pool = new DexPool(Opcodes.forDexVersion(38));
        pool.internClass(rClass("Lcom/example/R$string;", fieldFlags));
        pool.writeTo(new FileDataStore(new File(dir, name)));
    }
}
