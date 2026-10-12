package com.orange.patchgen;

import com.orange.patchgen.callback.GeneratorCallback;
import com.orange.patchgen.callback.GeneratorErrorCode;
import com.orange.patchgen.callback.SimpleGeneratorCallback;
import com.orange.patchgen.config.EngineType;
import com.orange.patchgen.config.GeneratorConfig;
import com.orange.patchgen.config.PatchMode;
import com.orange.patchgen.config.SigningConfig;
import com.orange.patchgen.differ.DexDiffException;
import com.orange.patchgen.differ.DexDiffResult;
import com.orange.patchgen.differ.DexDiffer;
import com.orange.patchgen.differ.FileChange;
import com.orange.patchgen.differ.ResourceDiffException;
import com.orange.patchgen.differ.ResourceDiffResult;
import com.orange.patchgen.differ.ResourceDiffer;
import com.orange.patchgen.model.ApkInfo;
import com.orange.patchgen.model.DiffSummary;
import com.orange.patchgen.model.PatchChanges;
import com.orange.patchgen.model.PatchInfo;
import com.orange.patchgen.model.PatchResult;
import com.orange.patchgen.packer.PackContent;
import com.orange.patchgen.packer.PatchPackException;
import com.orange.patchgen.packer.PatchPacker;
import com.orange.patchgen.parser.ApkParser;
import com.orange.patchgen.parser.ParseException;
import com.orange.patchgen.signer.PatchSigner;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 补丁生成器主类
 * 
 * 统一的补丁生成入口，支持链式配置。
 * 集成 ApkParser、DexDiffer、ResourceDiffer、PatchPacker、PatchSigner。
 * 
 * Requirements: 1.1-1.6
 */
public class PatchGenerator {

    private final File baseApk;
    private final File newApk;
    private final File outputFile;
    private final SigningConfig signingConfig;
    private final EngineType engineType;
    private final PatchMode patchMode;
    private final GeneratorCallback callback;
    private final GeneratorConfig config;
    
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private ExecutorService executor;
    private Future<?> currentTask;

    /** 匹配 classes.dex / classes2.dex / classes12.dex */
    private static final Pattern DEX_NAME_PATTERN = Pattern.compile("classes(\\d*)\\.dex");

    /**
     * dex 文件名的自然顺序：classes.dex < classes2.dex < classes3.dex < ...
     * 注意不能按字符串比较，否则 classes10.dex 会排在 classes2.dex 前面。
     */
    static final Comparator<String> DEX_NAME_ORDER = (a, b) -> {
        Matcher ma = DEX_NAME_PATTERN.matcher(a);
        Matcher mb = DEX_NAME_PATTERN.matcher(b);
        boolean okA = ma.matches();
        boolean okB = mb.matches();
        if (okA && okB) {
            // classes.dex 无数字，等价于序号 1
            int na = ma.group(1).isEmpty() ? 1 : Integer.parseInt(ma.group(1));
            int nb = mb.group(1).isEmpty() ? 1 : Integer.parseInt(mb.group(1));
            if (na != nb) {
                return Integer.compare(na, nb);
            }
        } else if (okA != okB) {
            // 能识别的排前面
            return okA ? -1 : 1;
        }
        return a.compareTo(b);
    };

    private PatchGenerator(Builder builder) {
        this.baseApk = builder.baseApk;
        this.newApk = builder.newApk;
        this.outputFile = builder.outputFile;
        this.signingConfig = builder.signingConfig;
        this.engineType = builder.engineType;
        this.patchMode = builder.patchMode;
        this.callback = builder.callback != null ? builder.callback : new SimpleGeneratorCallback();
        this.config = builder.config != null ? builder.config : GeneratorConfig.builder().build();
    }

    /**
     * 同步生成补丁
     * 
     * @return 生成结果
     * @throws PatchGeneratorException 生成失败时抛出
     */
    public PatchResult generate() throws PatchGeneratorException {
        long startTime = System.currentTimeMillis();
        File tempDir = null;
        
        try {
            // 验证输入参数
            validateInputs();
            
            // 创建临时目录
            tempDir = createTempDir();
            
            // 1. 解析 APK
            ApkInfo baseApkInfo = parseApk(baseApk, "base");
            if (cancelled.get()) {
                return PatchResult.failure(GeneratorErrorCode.ERROR_CANCELLED, "Operation cancelled");
            }
            
            ApkInfo newApkInfo = parseApk(newApk, "new");
            if (cancelled.get()) {
                return PatchResult.failure(GeneratorErrorCode.ERROR_CANCELLED, "Operation cancelled");
            }
            
            // 2. 比较差异
            callback.onCompareStart();
            
            // 解压 APK 用于详细比较
            File baseExtractDir = new File(tempDir, "base");
            File newExtractDir = new File(tempDir, "new");
            
            ApkParser parser = new ApkParser();
            parser.extract(baseApk, baseExtractDir);
            parser.extract(newApk, newExtractDir);
            
            baseApkInfo.setExtractedDir(baseExtractDir);
            newApkInfo.setExtractedDir(newExtractDir);
            
            // 比较 Dex 差异
            List<DexDiffResult> dexDiffs = compareDex(baseExtractDir, newExtractDir);
            if (cancelled.get()) {
                return PatchResult.failure(GeneratorErrorCode.ERROR_CANCELLED, "Operation cancelled");
            }
            
            // 比较资源差异
            ResourceDiffResult resDiff = compareResources(baseExtractDir, newExtractDir);
            if (cancelled.get()) {
                return PatchResult.failure(GeneratorErrorCode.ERROR_CANCELLED, "Operation cancelled");
            }
            
            // 比较 Assets 差异
            ResourceDiffResult assetsDiff = compareAssets(baseExtractDir, newExtractDir);
            if (cancelled.get()) {
                return PatchResult.failure(GeneratorErrorCode.ERROR_CANCELLED, "Operation cancelled");
            }
            
            // 3. 检查是否有差异
            DiffSummary diffSummary = buildDiffSummary(dexDiffs, resDiff, assetsDiff);
            
            if (!diffSummary.hasChanges()) {
                // 没有差异，不需要生成补丁
                PatchResult result = PatchResult.noPatchNeeded();
                result.setGenerateTime(System.currentTimeMillis() - startTime);
                result.setBaseApkSize(baseApk.length());
                result.setNewApkSize(newApk.length());
                callback.onComplete(result);
                return result;
            }

            // 4. 生成补丁内容
            callback.onPackStart();
            
            // 生成补丁 Dex 文件
            File patchDexDir = new File(tempDir, "patch_dex");
            patchDexDir.mkdirs();
            List<File> patchDexFiles = generatePatchDexFiles(dexDiffs, newExtractDir, patchDexDir);
            
            // 复制修改的资源文件
            File patchResDir = new File(tempDir, "patch_res");
            copyChangedResources(resDiff, newExtractDir, patchResDir, "res");
            
            // 复制修改的 Assets 文件
            File patchAssetsDir = new File(tempDir, "patch_assets");
            copyChangedResources(assetsDiff, newExtractDir, patchAssetsDir, "assets");
            
            // 复制 resources.arsc（资源热更新必需）
            File resourcesArsc = null;
            if (resDiff != null && resDiff.hasChanges()) {
                File newResourcesArsc = new File(newExtractDir, "resources.arsc");
                if (newResourcesArsc.exists()) {
                    System.out.println("[PatchGenerator] 复制 resources.arsc");
                    System.out.println("  源文件: " + newResourcesArsc.getAbsolutePath());
                    System.out.println("  源文件大小: " + newResourcesArsc.length() + " bytes (" + (newResourcesArsc.length() / 1024) + " KB)");
                    
                    resourcesArsc = new File(tempDir, "resources.arsc");
                    FileUtils.copyFile(newResourcesArsc, resourcesArsc);
                    
                    System.out.println("  目标文件: " + resourcesArsc.getAbsolutePath());
                    System.out.println("  目标文件大小: " + resourcesArsc.length() + " bytes (" + (resourcesArsc.length() / 1024) + " KB)");
                } else {
                    System.out.println("[PatchGenerator] ⚠️ resources.arsc 不存在: " + newResourcesArsc.getAbsolutePath());
                }
            } else {
                System.out.println("[PatchGenerator] ⚠️ 没有资源变化，跳过 resources.arsc");
            }
            
            // 5. 创建 PatchInfo
            PatchInfo patchInfo = createPatchInfo(baseApkInfo, newApkInfo, dexDiffs, resDiff, assetsDiff);
            
            // 6. 打包补丁
            PackContent packContent = new PackContent.Builder()
                    .patchInfo(patchInfo)
                    .dexFiles(patchDexFiles)
                    .resDir(patchResDir.exists() && patchResDir.listFiles() != null ? patchResDir : null)
                    .resourcesArsc(resourcesArsc)
                    .assetsDir(patchAssetsDir.exists() && patchAssetsDir.listFiles() != null ? patchAssetsDir : null)
                    .build();
            
            PatchPacker packer = new PatchPacker();
            File patchFile = packer.pack(packContent, outputFile);
            
            if (cancelled.get()) {
                return PatchResult.failure(GeneratorErrorCode.ERROR_CANCELLED, "Operation cancelled");
            }
            
            // 7. 签名补丁
            if (signingConfig != null && signingConfig.isValid()) {
                callback.onSignStart();
                PatchSigner signer = new PatchSigner(signingConfig);
                signer.sign(patchFile);
            }
            
            // 8. 构建结果
            PatchResult result = PatchResult.success(patchFile, patchInfo, diffSummary);
            result.setGenerateTime(System.currentTimeMillis() - startTime);
            result.setBaseApkSize(baseApk.length());
            result.setNewApkSize(newApk.length());
            result.setPatchSize(patchFile.length());
            result.calculateCompressionRatio();
            
            callback.onComplete(result);
            return result;
            
        } catch (ParseException e) {
            int errorCode = e.getErrorCode();
            String message = "APK parse failed: " + e.getMessage();
            callback.onError(errorCode, message);
            throw new PatchGeneratorException(message, errorCode, e);
            
        } catch (DexDiffException e) {
            int errorCode = e.getErrorCode();
            String message = "Dex comparison failed: " + e.getMessage();
            callback.onError(errorCode, message);
            throw new PatchGeneratorException(message, errorCode, e);
            
        } catch (ResourceDiffException e) {
            int errorCode = e.getErrorCode();
            String message = "Resource comparison failed: " + e.getMessage();
            callback.onError(errorCode, message);
            throw new PatchGeneratorException(message, errorCode, e);
            
        } catch (PatchPackException e) {
            int errorCode = GeneratorErrorCode.ERROR_FILE_WRITE_FAILED;
            String message = "Patch packing failed: " + e.getMessage();
            callback.onError(errorCode, message);
            throw new PatchGeneratorException(message, errorCode, e);
            
        } catch (PatchSigner.SigningException e) {
            int errorCode = GeneratorErrorCode.ERROR_SIGNING_FAILED;
            String message = "Patch signing failed: " + e.getMessage();
            callback.onError(errorCode, message);
            throw new PatchGeneratorException(message, errorCode, e);
            
        } catch (IOException e) {
            int errorCode = GeneratorErrorCode.ERROR_FILE_READ_FAILED;
            String message = "IO error: " + e.getMessage();
            callback.onError(errorCode, message);
            throw new PatchGeneratorException(message, errorCode, e);
            
        } finally {
            // 清理临时目录
            if (tempDir != null && tempDir.exists()) {
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }
        }
    }


    /**
     * 异步生成补丁
     * 
     * @param resultCallback 结果回调
     */
    public void generateAsync(ResultCallback resultCallback) {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
        
        currentTask = executor.submit(() -> {
            try {
                PatchResult result = generate();
                if (resultCallback != null) {
                    resultCallback.onResult(result);
                }
            } catch (PatchGeneratorException e) {
                if (resultCallback != null) {
                    resultCallback.onError(e);
                }
            }
        });
    }

    /**
     * 取消生成
     */
    public void cancel() {
        cancelled.set(true);
        if (currentTask != null && !currentTask.isDone()) {
            currentTask.cancel(true);
        }
    }

    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 关闭生成器，释放资源
     */
    public void shutdown() {
        cancel();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    // ==================== Private Methods ====================

    /**
     * 验证输入参数
     */
    private void validateInputs() throws PatchGeneratorException {
        if (baseApk == null) {
            throw new PatchGeneratorException("Base APK is null", 
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!baseApk.exists()) {
            throw new PatchGeneratorException("Base APK not found: " + baseApk.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!baseApk.canRead()) {
            throw new PatchGeneratorException("Cannot read base APK: " + baseApk.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_READ_FAILED);
        }
        
        if (newApk == null) {
            throw new PatchGeneratorException("New APK is null",
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!newApk.exists()) {
            throw new PatchGeneratorException("New APK not found: " + newApk.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!newApk.canRead()) {
            throw new PatchGeneratorException("Cannot read new APK: " + newApk.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_READ_FAILED);
        }
        
        if (outputFile == null) {
            throw new PatchGeneratorException("Output file is null",
                    GeneratorErrorCode.ERROR_FILE_WRITE_FAILED);
        }
        
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new PatchGeneratorException("Cannot create output directory: " + outputDir.getAbsolutePath(),
                        GeneratorErrorCode.ERROR_FILE_WRITE_FAILED);
            }
        }
    }

    /**
     * 创建临时目录
     */
    private File createTempDir() throws PatchGeneratorException {
        File tempDir = config.getTempDir();
        if (tempDir == null) {
            tempDir = new File(System.getProperty("java.io.tmpdir"));
        }
        
        File workDir = new File(tempDir, "patch_gen_" + System.currentTimeMillis());
        if (!workDir.mkdirs()) {
            throw new PatchGeneratorException("Cannot create temp directory: " + workDir.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_WRITE_FAILED);
        }
        return workDir;
    }

    /**
     * 解析 APK
     */
    private ApkInfo parseApk(File apkFile, String name) throws ParseException {
        callback.onParseStart(apkFile.getAbsolutePath());
        ApkParser parser = new ApkParser();
        ApkInfo apkInfo = parser.parse(apkFile);
        callback.onParseProgress(1, 1);
        return apkInfo;
    }


    /**
     * 比较 Dex 差异
     */
    private List<DexDiffResult> compareDex(File baseExtractDir, File newExtractDir) 
            throws DexDiffException {
        DexDiffer dexDiffer = new DexDiffer();
        List<DexDiffResult> results = new ArrayList<>();
        
        // 获取所有 dex 文件
        File[] baseDexFiles = baseExtractDir.listFiles((dir, name) -> 
                name.matches("classes\\d*\\.dex"));
        File[] newDexFiles = newExtractDir.listFiles((dir, name) -> 
                name.matches("classes\\d*\\.dex"));
        
        // 收集所有 dex 文件名
        java.util.Set<String> allDexNames = new java.util.HashSet<>();
        if (baseDexFiles != null) {
            for (File f : baseDexFiles) {
                allDexNames.add(f.getName());
            }
        }
        if (newDexFiles != null) {
            for (File f : newDexFiles) {
                allDexNames.add(f.getName());
            }
        }
        
        int current = 0;
        int total = allDexNames.size();
        
        for (String dexName : allDexNames) {
            File baseDex = new File(baseExtractDir, dexName);
            File newDex = new File(newExtractDir, dexName);
            
            callback.onCompareProgress(++current, total, dexName);
            
            if (!baseDex.exists() && newDex.exists()) {
                // 新增的 dex 文件
                DexDiffResult result = new DexDiffResult(dexName);
                // 所有类都是新增的 - 需要解析 dex 获取类列表
                result.addAddedClass("*"); // 标记整个 dex 为新增
                results.add(result);
            } else if (baseDex.exists() && !newDex.exists()) {
                // 删除的 dex 文件
                DexDiffResult result = new DexDiffResult(dexName);
                result.addDeletedClass("*"); // 标记整个 dex 为删除
                results.add(result);
            } else if (baseDex.exists() && newDex.exists()) {
                // 两个都存在，进行详细比较
                DexDiffResult result = dexDiffer.compare(baseDex, newDex);
                if (result.hasChanges()) {
                    results.add(result);
                }
            }
            
            if (cancelled.get()) {
                break;
            }
        }
        
        return results;
    }

    /**
     * 比较资源差异
     */
    private ResourceDiffResult compareResources(File baseExtractDir, File newExtractDir) 
            throws ResourceDiffException {
        ResourceDiffer resourceDiffer = new ResourceDiffer();
        File baseResDir = new File(baseExtractDir, "res");
        File newResDir = new File(newExtractDir, "res");
        return resourceDiffer.compare(baseResDir, newResDir);
    }

    /**
     * 比较 Assets 差异
     */
    private ResourceDiffResult compareAssets(File baseExtractDir, File newExtractDir) 
            throws ResourceDiffException {
        ResourceDiffer resourceDiffer = new ResourceDiffer();
        File baseAssetsDir = new File(baseExtractDir, "assets");
        File newAssetsDir = new File(newExtractDir, "assets");
        return resourceDiffer.compareAssets(baseAssetsDir, newAssetsDir);
    }

    /**
     * 构建差异摘要
     */
    private DiffSummary buildDiffSummary(List<DexDiffResult> dexDiffs, 
                                          ResourceDiffResult resDiff,
                                          ResourceDiffResult assetsDiff) {
        DiffSummary summary = new DiffSummary();
        
        // Dex 差异统计
        for (DexDiffResult dexDiff : dexDiffs) {
            if (dexDiff.getModifiedClasses() != null) {
                summary.setModifiedClasses(summary.getModifiedClasses() + dexDiff.getModifiedClasses().size());
                for (String className : dexDiff.getModifiedClasses()) {
                    summary.addModifiedFile("dex:" + className);
                }
            }
            if (dexDiff.getAddedClasses() != null) {
                summary.setAddedClasses(summary.getAddedClasses() + dexDiff.getAddedClasses().size());
            }
            if (dexDiff.getDeletedClasses() != null) {
                summary.setDeletedClasses(summary.getDeletedClasses() + dexDiff.getDeletedClasses().size());
            }
        }
        
        // 资源差异统计
        if (resDiff != null) {
            if (resDiff.getModifiedFiles() != null) {
                summary.setModifiedResources(resDiff.getModifiedFiles().size());
                for (FileChange change : resDiff.getModifiedFiles()) {
                    summary.addModifiedFile("res:" + change.getRelativePath());
                }
            }
            if (resDiff.getAddedFiles() != null) {
                summary.setAddedResources(resDiff.getAddedFiles().size());
            }
            if (resDiff.getDeletedFiles() != null) {
                summary.setDeletedResources(resDiff.getDeletedFiles().size());
            }
        }
        
        // Assets 差异统计
        if (assetsDiff != null) {
            if (assetsDiff.getModifiedFiles() != null) {
                summary.setModifiedAssets(assetsDiff.getModifiedFiles().size());
                for (FileChange change : assetsDiff.getModifiedFiles()) {
                    summary.addModifiedFile("assets:" + change.getRelativePath());
                }
            }
            if (assetsDiff.getAddedFiles() != null) {
                summary.setAddedAssets(assetsDiff.getAddedFiles().size());
            }
            if (assetsDiff.getDeletedFiles() != null) {
                summary.setDeletedAssets(assetsDiff.getDeletedFiles().size());
            }
        }
        
        return summary;
    }


    /**
     * 生成补丁 Dex 文件
     *
     * 重要：补丁 zip 内的 dex 条目名必须从 classes.dex 开始连续编号
     * (classes.dex, classes2.dex, classes3.dex, ...)，不能出现跳号。
     * ART 只会按 classes.dex -> classes2.dex -> ... 的顺序连续查找，
     * 一旦遇到缺口（例如只有 classes8.dex、classes12.dex）就会停止，
     * 导致补丁中的类全部无法加载。
     *
     * 因此这里在生成/复制出补丁 dex 后，按原始 dex 的确定性顺序
     * 重新编号为连续序列。同时保证同一对 APK 每次生成的映射一致。
     */
    private List<File> generatePatchDexFiles(List<DexDiffResult> dexDiffs,
                                              File newExtractDir,
                                              File outputDir) throws DexDiffException {
        List<File> patchDexFiles = new ArrayList<>();
        DexDiffer dexDiffer = new DexDiffer();

        // 按原始 dex 名排序，保证生成顺序确定（HashSet 顺序不稳定）
        List<DexDiffResult> sortedDiffs = new ArrayList<>(dexDiffs);
        sortedDiffs.sort(Comparator.comparing(DexDiffResult::getDexName, DEX_NAME_ORDER));

        for (DexDiffResult diff : sortedDiffs) {
            if (!diff.hasChanges()) {
                continue;
            }

            // 如果是整个 dex 新增，直接复制
            if (diff.getAddedClasses() != null && diff.getAddedClasses().contains("*")) {
                File newDex = new File(newExtractDir, diff.getDexName());
                if (newDex.exists()) {
                    File patchDex = new File(outputDir, diff.getDexName());
                    try {
                        FileUtils.copyFile(newDex, patchDex);
                        patchDexFiles.add(patchDex);
                    } catch (IOException e) {
                        throw new DexDiffException("Failed to copy dex file: " + e.getMessage(),
                                GeneratorErrorCode.ERROR_FILE_WRITE_FAILED, e);
                    }
                }
                continue;
            }

            // 如果是整个 dex 删除，跳过（删除信息记录在 metadata 中）
            if (diff.getDeletedClasses() != null && diff.getDeletedClasses().contains("*")) {
                continue;
            }

            // 生成包含修改和新增类的补丁 dex
            File newDex = new File(newExtractDir, diff.getDexName());
            if (newDex.exists()) {
                File patchDex = dexDiffer.generatePatchDex(diff, newDex, outputDir);
                if (patchDex != null && patchDex.exists()) {
                    patchDexFiles.add(patchDex);
                }
            }
        }

        // 关键：重命名为连续的 classes.dex / classes2.dex / ...
        return renumberPatchDexFiles(patchDexFiles);
    }

    /**
     * 将补丁 dex 重命名为连续的 classes.dex, classes2.dex, ...
     *
     * @param patchDexFiles 已生成的补丁 dex 文件（顺序需已确定）
     * @return 重命名后的文件列表
     * @throws DexDiffException 重命名失败时抛出
     */
    static List<File> renumberPatchDexFiles(List<File> patchDexFiles) throws DexDiffException {
        List<File> renumbered = new ArrayList<>();

        for (int i = 0; i < patchDexFiles.size(); i++) {
            File source = patchDexFiles.get(i);
            String targetName = (i == 0) ? "classes.dex" : "classes" + (i + 1) + ".dex";

            if (source.getName().equals(targetName)) {
                renumbered.add(source);
                continue;
            }

            File target = new File(source.getParentFile(), targetName);
            // 先删除可能存在的旧文件，避免重命名失败
            if (target.exists() && !target.equals(source)) {
                target.delete();
            }

            if (!source.renameTo(target)) {
                // renameTo 在某些平台上跨卷失败，回退到复制
                try {
                    FileUtils.copyFile(source, target);
                    source.delete();
                } catch (IOException e) {
                    throw new DexDiffException("Failed to renumber patch dex: " + source.getName()
                            + " -> " + targetName + ": " + e.getMessage(),
                            GeneratorErrorCode.ERROR_FILE_WRITE_FAILED, e);
                }
            }

            renumbered.add(target);
        }

        return renumbered;
    }

    /**
     * 复制修改的资源文件
     */
    private void copyChangedResources(ResourceDiffResult diffResult, 
                                       File sourceExtractDir, 
                                       File targetDir,
                                       String subDir) throws IOException {
        if (diffResult == null || !diffResult.hasChanges()) {
            return;
        }
        
        File sourceDir = new File(sourceExtractDir, subDir);
        if (!sourceDir.exists()) {
            return;
        }
        
        // 复制修改的文件
        if (diffResult.getModifiedFiles() != null) {
            for (FileChange change : diffResult.getModifiedFiles()) {
                copyResourceFile(sourceDir, targetDir, change.getRelativePath());
            }
        }
        
        // 复制新增的文件
        if (diffResult.getAddedFiles() != null) {
            for (FileChange change : diffResult.getAddedFiles()) {
                copyResourceFile(sourceDir, targetDir, change.getRelativePath());
            }
        }
    }

    /**
     * 复制单个资源文件
     */
    private void copyResourceFile(File sourceDir, File targetDir, String relativePath) 
            throws IOException {
        File sourceFile = new File(sourceDir, relativePath);
        File targetFile = new File(targetDir, relativePath);
        
        if (sourceFile.exists()) {
            File targetParent = targetFile.getParentFile();
            if (targetParent != null && !targetParent.exists()) {
                targetParent.mkdirs();
            }
            FileUtils.copyFile(sourceFile, targetFile);
        }
    }

    /**
     * 创建 PatchInfo
     */
    private PatchInfo createPatchInfo(ApkInfo baseApkInfo, 
                                       ApkInfo newApkInfo,
                                       List<DexDiffResult> dexDiffs,
                                       ResourceDiffResult resDiff,
                                       ResourceDiffResult assetsDiff) {
        PatchInfo patchInfo = new PatchInfo();
        
        // 基本信息
        patchInfo.setPatchId("patch_" + System.currentTimeMillis() + "_" + 
                UUID.randomUUID().toString().substring(0, 8));
        patchInfo.setPatchVersion(newApkInfo.getVersionName());
        patchInfo.setPackageName(newApkInfo.getPackageName());
        patchInfo.setBaseVersion(baseApkInfo.getVersionName());
        patchInfo.setBaseVersionCode(baseApkInfo.getVersionCode());
        patchInfo.setTargetVersion(newApkInfo.getVersionName());
        patchInfo.setTargetVersionCode(newApkInfo.getVersionCode());
        patchInfo.setPatchMode(patchMode.name().toLowerCase());
        patchInfo.setCreateTime(System.currentTimeMillis());
        
        // 变更信息
        PatchChanges changes = new PatchChanges();
        
        // Dex 变更
        for (DexDiffResult dexDiff : dexDiffs) {
            if (dexDiff.getModifiedClasses() != null) {
                for (String className : dexDiff.getModifiedClasses()) {
                    changes.getDex().addModified(className);
                }
            }
            if (dexDiff.getAddedClasses() != null) {
                for (String className : dexDiff.getAddedClasses()) {
                    changes.getDex().addAdded(className);
                }
            }
            if (dexDiff.getDeletedClasses() != null) {
                for (String className : dexDiff.getDeletedClasses()) {
                    changes.getDex().addDeleted(className);
                }
            }
        }
        
        // 资源变更
        if (resDiff != null) {
            if (resDiff.getModifiedFiles() != null) {
                for (FileChange change : resDiff.getModifiedFiles()) {
                    changes.getResources().addModified("res/" + change.getRelativePath());
                }
            }
            if (resDiff.getAddedFiles() != null) {
                for (FileChange change : resDiff.getAddedFiles()) {
                    changes.getResources().addAdded("res/" + change.getRelativePath());
                }
            }
            if (resDiff.getDeletedFiles() != null) {
                for (String path : resDiff.getDeletedFiles()) {
                    changes.getResources().addDeleted("res/" + path);
                }
            }
        }
        
        // Assets 变更
        if (assetsDiff != null) {
            if (assetsDiff.getModifiedFiles() != null) {
                for (FileChange change : assetsDiff.getModifiedFiles()) {
                    changes.getAssets().addModified(change.getRelativePath());
                }
            }
            if (assetsDiff.getAddedFiles() != null) {
                for (FileChange change : assetsDiff.getAddedFiles()) {
                    changes.getAssets().addAdded(change.getRelativePath());
                }
            }
            if (assetsDiff.getDeletedFiles() != null) {
                for (String path : assetsDiff.getDeletedFiles()) {
                    changes.getAssets().addDeleted(path);
                }
            }
        }
        
        patchInfo.setChanges(changes);
        
        // MD5 和文件大小会在打包后更新
        patchInfo.setMd5("00000000000000000000000000000000"); // 占位符
        
        return patchInfo;
    }


    // ==================== Builder ====================

    /**
     * PatchGenerator 构建器
     */
    public static class Builder {
        private File baseApk;
        private File newApk;
        private File outputFile;
        private SigningConfig signingConfig;
        private EngineType engineType = EngineType.AUTO;
        private PatchMode patchMode = PatchMode.FULL_DEX;
        private GeneratorCallback callback;
        private GeneratorConfig config;

        public Builder() {
        }

        /**
         * 设置基准 APK
         */
        public Builder baseApk(File apk) {
            this.baseApk = apk;
            return this;
        }

        /**
         * 设置新版本 APK
         */
        public Builder newApk(File apk) {
            this.newApk = apk;
            return this;
        }

        /**
         * 设置输出文件
         */
        public Builder output(File output) {
            this.outputFile = output;
            return this;
        }

        /**
         * 设置签名配置
         */
        public Builder signingConfig(SigningConfig config) {
            this.signingConfig = config;
            return this;
        }

        /**
         * 设置引擎类型
         */
        public Builder engineType(EngineType type) {
            this.engineType = type;
            return this;
        }

        /**
         * 设置补丁模式
         */
        public Builder patchMode(PatchMode mode) {
            this.patchMode = mode;
            return this;
        }

        /**
         * 设置回调
         */
        public Builder callback(GeneratorCallback callback) {
            this.callback = callback;
            return this;
        }

        /**
         * 设置生成器配置
         */
        public Builder config(GeneratorConfig config) {
            this.config = config;
            return this;
        }

        /**
         * 构建 PatchGenerator
         */
        public PatchGenerator build() {
            return new PatchGenerator(this);
        }
    }

    // ==================== Result Callback ====================

    /**
     * 异步结果回调接口
     */
    public interface ResultCallback {
        /**
         * 生成成功
         */
        void onResult(PatchResult result);

        /**
         * 生成失败
         */
        void onError(PatchGeneratorException e);
    }

    // ==================== Exception ====================

    /**
     * 补丁生成异常
     */
    public static class PatchGeneratorException extends Exception {
        private final int errorCode;

        public PatchGeneratorException(String message, int errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public PatchGeneratorException(String message, int errorCode, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }
}
