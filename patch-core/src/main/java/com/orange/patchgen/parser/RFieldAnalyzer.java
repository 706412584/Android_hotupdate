package com.orange.patchgen.parser;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;

import java.io.File;
import java.io.IOException;

/**
 * dex 层检测：R 类（{@code R}、{@code R$string}、{@code R$layout}…）的静态 int
 * 字段是否带 {@code ACC_FINAL}。
 *
 * <p><b>为什么查 dex 而不是读 Gradle 配置</b>：这是对**实际产物**的检测。
 * AGP 8+ 默认 {@code android.nonFinalResIds=true}，R 字段非 final，调用点走
 * getter，运行期取 ID；旧版 AGP（或显式设 {@code android.nonFinalResIds=false}）
 * 生成 final 字段，编译器会把资源 ID 作为字面量**内联**进调用点。内联后，
 * 未被差分 dex 覆盖的旧代码永远带着旧版本的 ID——一旦新版重排资源 ID 就静默
 * 显示错误内容（详见 docs/RESOURCE_ID_STABILITY.md）。读构建配置可能因参数
 * 未透传、AGP 行为变化而与实际产物不符，因此以产物为准。
 *
 * <p>纯 JVM 实现，dexlib2 解析，不依赖任何 Android 类。
 */
public final class RFieldAnalyzer {

    private static final int ACC_STATIC = 0x8;
    private static final int ACC_FINAL = 0x10;

    private RFieldAnalyzer() {
        // Prevent instantiation
    }

    /**
     * 扫描目录下全部 {@code classes*.dex}，统计 R 类的静态 int 字段。
     *
     * <p>单个 dex 解析失败会被跳过（本检测是诊断信息，不应让整个生成失败）；
     * 若全部 dex 都无法解析，返回的 Result 中字段数为 0（即「未能判定」）。
     */
    public static Result analyze(File extractDir) {
        Result total = new Result();
        if (extractDir == null || !extractDir.isDirectory()) {
            return total;
        }
        File[] dexFiles = extractDir.listFiles(
                (dir, name) -> name.matches("classes\\d*\\.dex"));
        if (dexFiles == null) {
            return total;
        }
        for (File dexFile : dexFiles) {
            try {
                total.merge(analyzeDex(dexFile));
            } catch (IOException e) {
                // 诊断检测：跳过无法解析的 dex，不影响主流程
            }
        }
        return total;
    }

    /**
     * 扫描单个 dex 文件。
     */
    public static Result analyzeDex(File dexFile) throws IOException {
        Result result = new Result();
        // opcodes 传 null：按 dex 头部自带的版本号解析（兼容 035/038/039）。
        DexFile dex = DexFileFactory.loadDexFile(dexFile, null);
        for (ClassDef classDef : dex.getClasses()) {
            if (!isRClass(classDef.getType())) {
                continue;
            }
            result.rClasses++;
            for (Field field : classDef.getFields()) {
                // 只有静态 int 字段才会被编译期内联（int[] styleable 不适用）。
                if (!"I".equals(field.getType())) {
                    continue;
                }
                if ((field.getAccessFlags() & ACC_STATIC) == 0) {
                    continue;
                }
                if ((field.getAccessFlags() & ACC_FINAL) != 0) {
                    result.finalIntFields++;
                } else {
                    result.nonFinalIntFields++;
                }
            }
        }
        return result;
    }

    /**
     * 类型描述符是否为 R 类：简单名为 {@code R} 或 {@code R$...}。
     * 形如 {@code Lcom/example/R$string;}、{@code Landroidx/appcompat/R$anim;}。
     */
    static boolean isRClass(String type) {
        if (type == null || type.length() < 3) {
            return false;
        }
        int lastSlash = type.lastIndexOf('/');
        String simple = type.substring(lastSlash + 1);
        // 去掉结尾分号
        if (simple.endsWith(";")) {
            simple = simple.substring(0, simple.length() - 1);
        }
        return simple.equals("R") || simple.startsWith("R$");
    }

    /**
     * 检测结果。字段计数跨多个 dex 累加。
     */
    public static final class Result {
        private int rClasses;
        private int finalIntFields;
        private int nonFinalIntFields;

        void merge(Result other) {
            this.rClasses += other.rClasses;
            this.finalIntFields += other.finalIntFields;
            this.nonFinalIntFields += other.nonFinalIntFields;
        }

        /** 发现的 R 类数量。 */
        public int getRClasses() {
            return rClasses;
        }

        /** 带 ACC_FINAL 的静态 int 字段数（会被编译期内联）。 */
        public int getFinalIntFields() {
            return finalIntFields;
        }

        /** 不带 ACC_FINAL 的静态 int 字段数（走 getter，无内联风险）。 */
        public int getNonFinalIntFields() {
            return nonFinalIntFields;
        }

        /** 是否检测到了任何静态 int 字段（false = 未能判定，如 R 类被裁剪）。 */
        public boolean isDetected() {
            return (finalIntFields + nonFinalIntFields) > 0;
        }

        /** 是否存在内联风险：至少一个 R 字段为 final。 */
        public boolean hasInlineRisk() {
            return finalIntFields > 0;
        }

        @Override
        public String toString() {
            return "RFieldResult{rClasses=" + rClasses
                    + ", finalIntFields=" + finalIntFields
                    + ", nonFinalIntFields=" + nonFinalIntFields + "}";
        }
    }
}
