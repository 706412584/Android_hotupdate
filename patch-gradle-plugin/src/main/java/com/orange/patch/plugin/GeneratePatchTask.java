package com.orange.patch.plugin;

import com.orange.patchgen.PatchGenerator;
import com.orange.patchgen.callback.GeneratorCallback;
import com.orange.patchgen.callback.SimpleGeneratorCallback;
import com.orange.patchgen.config.EngineType;
import com.orange.patchgen.config.PatchMode;
import com.orange.patchgen.config.SigningConfig;
import com.orange.patchgen.model.PatchResult;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.logging.Logger;

import java.io.File;

/**
 * 生成补丁的 Gradle 任务
 * 
 * 集成 PatchGenerator API，支持增量构建。
 * 
 * Requirements: 7.2, 7.6, 7.7
 */
public abstract class GeneratePatchTask extends DefaultTask {

    /**
     * 基线 APK 文件
     */
    @InputFile
    public abstract RegularFileProperty getBaselineApk();

    /**
     * 新版本 APK 文件
     */
    @InputFile
    @Optional
    public abstract RegularFileProperty getNewApk();

    /**
     * 输出目录
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    /**
     * Keystore 文件
     */
    @InputFile
    @Optional
    public abstract RegularFileProperty getKeystoreFile();

    /**
     * Keystore 密码
     */
    @Input
    @Optional
    public abstract Property<String> getKeystorePassword();

    /**
     * Key 别名
     */
    @Input
    @Optional
    public abstract Property<String> getKeyAlias();

    /**
     * Key 密码
     */
    @Input
    @Optional
    public abstract Property<String> getKeyPassword();

    /**
     * 引擎类型: auto, java, native
     */
    @Input
    @Optional
    public abstract Property<String> getEngine();

    /**
     * 补丁模式: full_dex, bsdiff
     */
    @Input
    @Optional
    public abstract Property<String> getPatchMode();

    /**
     * 是否启用补丁生成
     */
    @Input
    @Optional
    public abstract Property<Boolean> getPatchEnabled();

    public GeneratePatchTask() {
        // 设置默认值
        getEngine().convention("auto");
        getPatchMode().convention("full_dex");
        getPatchEnabled().convention(true);
    }

    @TaskAction
    public void generatePatch() {
        Logger logger = getLogger();

        // 检查是否启用
        if (!getPatchEnabled().getOrElse(true)) {
            logger.lifecycle("Patch generation is disabled");
            return;
        }

        warnIfResIdsAreFinal(logger);

        // 获取输入文件
        File baseApk = getBaselineApk().getAsFile().get();
        File newApk = getNewApk().isPresent() ? getNewApk().getAsFile().get() : null;
        
        if (newApk == null) {
            logger.error("New APK is not specified. Please configure newApk or ensure the task is properly linked to an Android variant.");
            throw new RuntimeException("New APK is not specified");
        }

        // 验证输入文件
        if (!baseApk.exists()) {
            throw new RuntimeException("Baseline APK not found: " + baseApk.getAbsolutePath());
        }
        if (!newApk.exists()) {
            throw new RuntimeException("New APK not found: " + newApk.getAbsolutePath());
        }

        // 创建输出目录
        File outputDir = getOutputDir().getAsFile().get();
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // 生成输出文件名
        String patchFileName = generatePatchFileName(baseApk, newApk);
        File outputFile = new File(outputDir, patchFileName);

        logger.lifecycle("Generating patch...");
        logger.lifecycle("  Base APK: {}", baseApk.getAbsolutePath());
        logger.lifecycle("  New APK: {}", newApk.getAbsolutePath());
        logger.lifecycle("  Output: {}", outputFile.getAbsolutePath());

        try {
            // 构建签名配置
            SigningConfig signingConfig = buildSigningConfig();
            
            // 构建生成器
            PatchGenerator.Builder builder = new PatchGenerator.Builder()
                    .baseApk(baseApk)
                    .newApk(newApk)
                    .output(outputFile)
                    .engineType(parseEngineType())
                    .patchMode(parsePatchMode())
                    .callback(createCallback(logger));
            
            if (signingConfig != null) {
                builder.signingConfig(signingConfig);
            }
            
            PatchGenerator generator = builder.build();
            
            // 生成补丁
            PatchResult result = generator.generate();
            
            // 输出结果
            if (result.isSuccess()) {
                if (result.hasPatch()) {
                    logger.lifecycle("Patch generated successfully!");
                    logger.lifecycle("  Patch file: {}", result.getPatchFile().getAbsolutePath());
                    logger.lifecycle("  Patch size: {} bytes", result.getPatchSize());
                    logger.lifecycle("  Generation time: {} ms", result.getGenerateTime());

                    if (result.getDiffSummary() != null) {
                        logger.lifecycle("  Modified classes: {}", result.getDiffSummary().getModifiedClasses());
                        logger.lifecycle("  Added classes: {}", result.getDiffSummary().getAddedClasses());
                        logger.lifecycle("  Deleted classes: {}", result.getDiffSummary().getDeletedClasses());
                        logger.lifecycle("  Modified resources: {}", result.getDiffSummary().getModifiedResources());
                    }

                    // 资源稳定性：含资源的补丁在消费侧必须带 resourceIdsStable=true 才放行
                    if (result.getPatchInfo() != null
                            && result.getPatchInfo().getResourceIdsStable() != null) {
                        if (result.getPatchInfo().getResourceIdsStable()) {
                            logger.lifecycle("  Resource IDs: stable (resourceIdsStable=true)");
                        } else {
                            logger.warn("Resource IDs are NOT stable (resourceIdsStable=false) — "
                                    + "consumer apps will reject this patch if it contains resources. "
                                    + "See docs/RESOURCE_ID_STABILITY.md");
                        }
                    }
                } else {
                    logger.lifecycle("No patch needed - APKs are identical");
                }
            } else {
                throw new RuntimeException("Patch generation failed: " + result.getErrorMessage());
            }
            
        } catch (PatchGenerator.PatchGeneratorException e) {
            logger.error("Patch generation failed: {}", e.getMessage());
            throw new RuntimeException("Patch generation failed", e);
        }
    }

    /**
     * 构建期提示：R 字段是否为 final（资源 ID 内联风险）。
     *
     * <p>AGP 8+ 默认 {@code android.nonFinalResIds=true}（R 字段非 final，调用点走
     * getter，无内联）；旧版 AGP 或显式设为 false 时 R 字段为 final，资源 ID 被
     * 编译期内联进调用点——一旦新版重排资源 ID，未被差分 dex 覆盖的旧代码会静默
     * 引用错误资源（见 docs/RESOURCE_ID_STABILITY.md）。
     *
     * <p>这里读的是构建配置（dex 层实测由 patch-core 在生成时完成，结果写入
     * patch.json 的 rFieldsFinal）。配置读取全部走反射/属性查询，避免硬依赖 AGP 类。
     */
    private void warnIfResIdsAreFinal(Logger logger) {
        String propertyValue = null;
        try {
            Object value = getProject().findProperty("android.nonFinalResIds");
            propertyValue = value != null ? value.toString() : null;
        } catch (Exception e) {
            logger.debug("Could not read android.nonFinalResIds: {}", e.getMessage());
        }

        String agpVersion = null;
        try {
            Class<?> versionClass = Class.forName("com.android.Version");
            Object value = versionClass.getField("ANDROID_GRADLE_PLUGIN_VERSION").get(null);
            agpVersion = value != null ? value.toString() : null;
        } catch (Exception e) {
            logger.debug("Could not determine AGP version: {}", e.getMessage());
        }

        if (shouldWarnAboutFinalResIds(propertyValue, agpVersion)) {
            logger.warn("检测到 R 字段可能为 final（资源 ID 会被内联进 dex）："
                    + "android.nonFinalResIds={}, AGP={}。",
                    propertyValue != null ? propertyValue : "(未设置)",
                    agpVersion != null ? agpVersion : "(未知)");
            logger.warn("含资源的补丁在资源 ID 重排时会静默显示错误内容。"
                    + "建议：升级到 AGP 8+（默认 nonFinalResIds=true），"
                    + "并配合 aapt2 --stable-ids 固定资源 ID。"
                    + "详见 docs/RESOURCE_ID_STABILITY.md");
        }
    }

    /**
     * 判定是否应给出「R 字段为 final」警告。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code android.nonFinalResIds} 显式 false → 警告；</li>
     *   <li>AGP 主版本 &lt; 8（默认非 final 为 false）→ 警告；</li>
     *   <li>属性显式 true 或 AGP 8+ → 不警告。</li>
     * </ul>
     *
     * @param propertyValue {@code android.nonFinalResIds} 的值；未设置传 null
     * @param agpVersion    AGP 版本字符串（如 "8.12.0"）；未知传 null
     */
    static boolean shouldWarnAboutFinalResIds(String propertyValue, String agpVersion) {
        if (propertyValue != null) {
            String normalized = propertyValue.trim().toLowerCase();
            if ("false".equals(normalized)) {
                return true;
            }
            if ("true".equals(normalized)) {
                return false;
            }
        }
        Integer major = parseMajorVersion(agpVersion);
        if (major != null) {
            return major < 8;
        }
        // 两者都不可知：不打扰用户（dex 层实测结果仍会写入 patch.json）
        return false;
    }

    /** 从 "8.12.0" / "8.12.0-alpha01" 提取主版本号；无法解析返回 null。 */
    static Integer parseMajorVersion(String version) {
        if (version == null) {
            return null;
        }
        int dot = version.indexOf('.');
        String head = dot >= 0 ? version.substring(0, dot) : version;
        try {
            return Integer.parseInt(head.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 生成补丁文件名
     */
    private String generatePatchFileName(File baseApk, File newApk) {
        String baseName = baseApk.getName().replace(".apk", "");
        String newName = newApk.getName().replace(".apk", "");
        return String.format("patch_%s_to_%s_%d.patch", 
                baseName, newName, System.currentTimeMillis());
    }

    /**
     * 构建签名配置
     */
    private SigningConfig buildSigningConfig() {
        if (!getKeystoreFile().isPresent()) {
            return null;
        }
        
        File keystoreFile = getKeystoreFile().getAsFile().get();
        if (!keystoreFile.exists()) {
            getLogger().warn("Keystore file not found: {}", keystoreFile.getAbsolutePath());
            return null;
        }
        
        String keystorePassword = getKeystorePassword().getOrElse("");
        String keyAlias = getKeyAlias().getOrElse("");
        String keyPassword = getKeyPassword().getOrElse("");
        
        if (keystorePassword.isEmpty() || keyAlias.isEmpty() || keyPassword.isEmpty()) {
            getLogger().warn("Signing configuration is incomplete, patch will not be signed");
            return null;
        }
        
        return new SigningConfig.Builder()
                .keystoreFile(keystoreFile)
                .keystorePassword(keystorePassword)
                .keyAlias(keyAlias)
                .keyPassword(keyPassword)
                .build();
    }

    /**
     * 解析引擎类型
     */
    private EngineType parseEngineType() {
        String engine = getEngine().getOrElse("auto").toLowerCase();
        switch (engine) {
            case "java":
                return EngineType.JAVA;
            case "native":
                return EngineType.NATIVE;
            case "auto":
            default:
                return EngineType.AUTO;
        }
    }

    /**
     * 解析补丁模式
     */
    private PatchMode parsePatchMode() {
        String mode = getPatchMode().getOrElse("full_dex").toLowerCase();
        switch (mode) {
            case "bsdiff":
                return PatchMode.BSDIFF;
            case "full_dex":
            default:
                return PatchMode.FULL_DEX;
        }
    }

    /**
     * 创建回调
     */
    private GeneratorCallback createCallback(Logger logger) {
        return new SimpleGeneratorCallback() {
            @Override
            public void onParseStart(String apkPath) {
                logger.info("Parsing APK: {}", apkPath);
            }

            @Override
            public void onParseProgress(int current, int total) {
                logger.debug("Parse progress: {}/{}", current, total);
            }

            @Override
            public void onCompareStart() {
                logger.info("Comparing APKs...");
            }

            @Override
            public void onCompareProgress(int current, int total, String currentFile) {
                logger.debug("Compare progress: {}/{} - {}", current, total, currentFile);
            }

            @Override
            public void onPackStart() {
                logger.info("Packing patch...");
            }

            @Override
            public void onPackProgress(long current, long total) {
                logger.debug("Pack progress: {}/{}", current, total);
            }

            @Override
            public void onSignStart() {
                logger.info("Signing patch...");
            }

            @Override
            public void onComplete(PatchResult result) {
                logger.info("Patch generation completed");
            }

            @Override
            public void onError(int errorCode, String message) {
                logger.error("Error [{}]: {}", errorCode, message);
            }
        };
    }
}
