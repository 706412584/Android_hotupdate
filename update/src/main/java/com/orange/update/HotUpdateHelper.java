package com.orange.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 热更新辅助类 - 提供简单易用的热更新 API
 * 
 * 这是一个高层封装类，简化了补丁应用流程，提供友好的回调接口。
 * 
 * 功能：
 * - 应用本地补丁文件
 * - 加载已应用的补丁
 * - 清除补丁（回滚）
 * - 查询补丁状态
 * - 进度和结果回调
 * 
 * 使用示例：
 * <pre>
 * // 1. 创建实例
 * HotUpdateHelper helper = new HotUpdateHelper(context);
 * 
 * // 2. 应用补丁
 * helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
 *     {@literal @}Override
 *     public void onProgress(int percent, String message) {
 *         Log.d(TAG, "进度: " + percent + "% - " + message);
 *     }
 *     
 *     {@literal @}Override
 *     public void onSuccess(PatchResult result) {
 *         Log.i(TAG, "热更新成功！");
 *     }
 *     
 *     {@literal @}Override
 *     public void onError(String message) {
 *         Log.e(TAG, "热更新失败: " + message);
 *     }
 * });
 * 
 * // 3. 在 Application.attachBaseContext() 中加载补丁
 * helper.loadAppliedPatch();
 * </pre>
 */
public class HotUpdateHelper {
    
    private static final String TAG = "HotUpdateHelper";
    
    private final Context context;
    private PatchStorage storage;  // 延迟初始化
    private PatchApplier applier;  // 延迟初始化
    private SecurityManager securityManager;  // 延迟初始化
    private final PatchSigner patchSigner;  // 使用 apksig 进行签名验证
    private ExecutorService executor;  // 延迟初始化
    private final SharedPreferences securityPrefs;
    
    // 日志回调
    private static LogCallback globalLogCallback;
    private LogCallback logCallback;
    
    // 安全策略配置键
    private static final String PREFS_SECURITY = "security_policy";
    private static final String KEY_REQUIRE_SIGNATURE = "require_signature";
    private static final String KEY_REQUIRE_ENCRYPTION = "require_encryption";
    
    /**
     * 构造函数
     * @param context 应用上下文
     */
    public HotUpdateHelper(Context context) {
        // 在 attachBaseContext 阶段，getApplicationContext() 返回 null
        // 所以直接使用传入的 context
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        this.patchSigner = new PatchSigner(this.context);  // 使用 apksig
        this.securityPrefs = this.context.getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE);
        
        // SecurityManager、PatchStorage、PatchApplier 延迟初始化
        // 因为 SecurityManager 需要 Android KeyStore，在 attachBaseContext 阶段无法使用
        // 只有在调用 applyPatch() 等需要加密功能的方法时才初始化
    }
    
    /**
     * 设置全局日志回调（所有 HotUpdateHelper 实例共享）
     * 
     * @param callback 日志回调接口
     */
    public static void setGlobalLogCallback(LogCallback callback) {
        globalLogCallback = callback;
    }
    
    /**
     * 设置实例日志回调（仅当前实例使用）
     * 
     * @param callback 日志回调接口
     */
    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }
    
    /**
     * 内部日志方法
     */
    private void logD(String message) {
        if (logCallback != null) {
            logCallback.onLog(LogLevel.DEBUG, TAG, message);
        } else if (globalLogCallback != null) {
            globalLogCallback.onLog(LogLevel.DEBUG, TAG, message);
        } else {
            Log.d(TAG, message);
        }
    }
    
    private void logI(String message) {
        if (logCallback != null) {
            logCallback.onLog(LogLevel.INFO, TAG, message);
        } else if (globalLogCallback != null) {
            globalLogCallback.onLog(LogLevel.INFO, TAG, message);
        } else {
            Log.i(TAG, message);
        }
    }
    
    private void logW(String message) {
        if (logCallback != null) {
            logCallback.onLog(LogLevel.WARN, TAG, message);
        } else if (globalLogCallback != null) {
            globalLogCallback.onLog(LogLevel.WARN, TAG, message);
        } else {
            Log.w(TAG, message);
        }
    }
    
    private void logE(String message) {
        if (logCallback != null) {
            logCallback.onLog(LogLevel.ERROR, TAG, message);
        } else if (globalLogCallback != null) {
            globalLogCallback.onLog(LogLevel.ERROR, TAG, message);
        } else {
            Log.e(TAG, message);
        }
    }
    
    private void logE(String message, Throwable throwable) {
        if (logCallback != null) {
            logCallback.onLog(LogLevel.ERROR, TAG, message + "\n" + Log.getStackTraceString(throwable));
        } else if (globalLogCallback != null) {
            globalLogCallback.onLog(LogLevel.ERROR, TAG, message + "\n" + Log.getStackTraceString(throwable));
        } else {
            Log.e(TAG, message, throwable);
        }
    }
    
    /**
     * 确保 SecurityManager 已初始化（延迟初始化）
     */
    private void ensureSecurityManagerInitialized() {
        if (securityManager == null) {
            securityManager = new SecurityManager(context);
        }
    }
    
    /**
     * 确保 PatchStorage 已初始化（延迟初始化）
     */
    private void ensureStorageInitialized() {
        if (storage == null) {
            ensureSecurityManagerInitialized();
            storage = new PatchStorage(context, securityManager);
        }
    }
    
    /**
     * 确保 PatchApplier 已初始化（延迟初始化）
     */
    private void ensureApplierInitialized() {
        if (applier == null) {
            ensureStorageInitialized();
            applier = new PatchApplier(context, storage);
        }
    }
    
    /**
     * 确保 ExecutorService 已初始化（延迟初始化）
     */
    private void ensureExecutorInitialized() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
    }
    
    /**
     * 应用补丁（异步）
     * 
     * @param patchFile 补丁文件
     * @param callback 回调接口
     */
    public void applyPatch(File patchFile, Callback callback) {
        ensureExecutorInitialized();
        ensureStorageInitialized();
        
        if (patchFile == null || !patchFile.exists()) {
            if (callback != null) {
                callback.onError("补丁文件不存在");
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                // 通知开始
                if (callback != null) {
                    callback.onProgress(5, "准备应用补丁...");
                }
                
                // 1. 检查安全策略（对于加密文件，签名验证将在解密后进行）
                String securityError = checkSecurityPolicy(patchFile);
                if (securityError != null) {
                    if (callback != null) {
                        callback.onError(securityError);
                    }
                    return;
                }
                
                // 2. 处理 AES 加密（如果是 .enc 文件）
                File actualPatchFile = patchFile;
                File tempDecryptedFile = null;
                boolean isAesEncrypted = patchFile.getName().endsWith(".enc");
                
                if (isAesEncrypted) {
                    logD("检测到 AES 加密补丁");
                    
                    if (callback != null) {
                        callback.onProgress(10, "解密补丁...");
                    }
                    
                    try {
                        // 解密 AES 加密的补丁（先尝试使用默认密钥）
                        tempDecryptedFile = new File(context.getCacheDir(), "temp_decrypt_" + System.currentTimeMillis() + ".zip");
                        byte[] encryptedData = readFileToBytes(patchFile);
                        byte[] decryptedData = securityManager.decrypt(encryptedData);
                        
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(tempDecryptedFile);
                        fos.write(decryptedData);
                        fos.close();
                        
                        actualPatchFile = tempDecryptedFile;
                        logD("✓ AES 解密成功（使用默认密钥）");
                        
                    } catch (Exception e) {
                        logE("AES 解密失败（默认密钥）: " + e.getMessage());
                        
                        // 清理临时文件
                        if (tempDecryptedFile != null && tempDecryptedFile.exists()) {
                            tempDecryptedFile.delete();
                        }
                        
                        // 通知 UI 需要用户输入密码
                        if (callback != null) {
                            callback.onAesPasswordRequired(patchFile);
                        }
                        return;
                    }
                    
                    // 解密成功后验证签名（如果要求签名）
                    boolean requireSignature = securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
                    if (requireSignature) {
                        if (callback != null) {
                            callback.onProgress(15, "验证补丁签名...");
                        }
                        
                        boolean hasSignature = checkHasSignature(actualPatchFile);
                        if (!hasSignature) {
                            if (tempDecryptedFile != null && tempDecryptedFile.exists()) {
                                tempDecryptedFile.delete();
                            }
                            if (callback != null) {
                                callback.onError("当前安全策略要求补丁必须签名！此补丁未签名，拒绝应用。");
                            }
                            return;
                        }
                        
                        // 验证签名与应用签名匹配
                        boolean signatureValid = patchSigner.verifyPatchSignatureMatchesApp(actualPatchFile);
                        if (!signatureValid) {
                            if (tempDecryptedFile != null && tempDecryptedFile.exists()) {
                                tempDecryptedFile.delete();
                            }
                            if (callback != null) {
                                callback.onError("⚠️ APK 签名验证失败: " + patchSigner.getError());
                            }
                            return;
                        }
                        
                        logD("✅ APK 签名验证通过（解密后）");
                    }
                }
                
                if (callback != null) {
                    callback.onProgress(20, "检查 ZIP 密码保护...");
                }
                
                // 3. 检查并处理 ZIP 密码加密
                ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
                
                if (zipPasswordManager.isEncrypted(actualPatchFile)) {
                    logD("检测到 ZIP 密码加密");
                    
                    if (callback != null) {
                        callback.onProgress(25, "检查 ZIP 密码保护...");
                    }
                    
                    // 检查是否有密码提示文件（.zippwd）
                    File zipPasswordFile = new File(patchFile.getPath() + ".zippwd");
                    boolean hasCustomPassword = zipPasswordFile.exists();
                    
                    if (hasCustomPassword) {
                        Log.d(TAG, "检测到自定义 ZIP 密码，需要用户输入");
                        
                        // 清理临时解密文件
                        if (tempDecryptedFile != null && tempDecryptedFile.exists()) {
                            tempDecryptedFile.delete();
                        }
                        
                        // 通知 UI 需要用户输入密码
                        if (callback != null) {
                            callback.onZipPasswordRequired(patchFile);
                        }
                        return; // 等待用户输入密码后调用 applyPatchWithZipPassword()
                    }
                    
                    // 没有自定义密码，直接保存加密文件到 applied 目录
                    // 应用启动时会自动使用派生密码解密
                    logD("使用派生密码，补丁将以加密状态保存");
                }
                
                if (callback != null) {
                    callback.onProgress(30, "准备应用补丁...");
                }
                
                // 4. 继续应用补丁流程
                File finalTempFile = tempDecryptedFile;
                try {
                    applyPatchInternal(actualPatchFile, patchFile, callback);
                } finally {
                    // 清理临时解密文件
                    if (finalTempFile != null && finalTempFile.exists()) {
                        finalTempFile.delete();
                        Log.d(TAG, "✓ 清理临时解密文件");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "应用补丁失败", e);
                if (callback != null) {
                    callback.onError("应用补丁失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 读取文件内容到字节数组
     */
    private byte[] readFileToBytes(File file) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return data;
        } catch (Exception e) {
            logE("Failed to read file", e);
            return null;
        }
    }
    
    /**
     * 检查补丁是否包含资源
     */
    private boolean hasResourcePatch(File patchFile) {
        // 检查文件扩展名或内容
        String fileName = patchFile.getName().toLowerCase(java.util.Locale.ROOT);
        
        // 如果是 APK 或 ZIP 文件，可能包含资源
        if (fileName.endsWith(".apk") || fileName.endsWith(".zip")) {
            return true;
        }
        
        // 如果是纯 DEX 文件，不包含资源
        if (fileName.endsWith(".dex")) {
            return false;
        }
        
        // 检查文件魔数
        try {
            byte[] header = new byte[4];
            java.io.FileInputStream fis = new java.io.FileInputStream(patchFile);
            fis.read(header);
            fis.close();
            
            // ZIP/APK 魔数: PK (0x50 0x4B)
            if (header[0] == 0x50 && header[1] == 0x4B) {
                return true;
            }
            
            // DEX 魔数: dex\n (0x64 0x65 0x78 0x0A)
            if (header[0] == 0x64 && header[1] == 0x65 && 
                header[2] == 0x78 && header[3] == 0x0A) {
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check patch file type", e);
        }
        
        return false;
    }
    
    /**
     * 应用补丁（同步）
     * 
     * @param patchFile 补丁文件
     * @return 是否应用成功
     */
    public boolean applyPatchSync(File patchFile) {
        ensureApplierInitialized();
        
        if (patchFile == null || !patchFile.exists()) {
            return false;
        }
        
        try {
            PatchInfo patchInfo = createPatchInfo(patchFile);
            return applier.apply(patchInfo);
        } catch (Exception e) {
            logE("应用补丁失败", e);
            return false;
        }
    }
    
    /**
     * 加载已应用的补丁（在 Application.attachBaseContext 中调用）
     * 
     * 此方法应在 Application.attachBaseContext() 中调用
     */
    public void loadAppliedPatch() {
        ensureApplierInitialized();
        applier.loadAppliedPatch();
    }
    
    /**
     * 加载已应用的补丁（完整版本，包含完整性验证和签名验证）
     * 
     * 推荐在 Application.attachBaseContext() 中调用此方法，而不是 loadAppliedPatch()
     * 
     * 功能：
     * 1. 检查是否有已应用的补丁
     * 2. 验证补丁完整性（防止篡改）
     * 3. 验证 APK 签名（如果补丁有签名）
     * 4. 自动解密 ZIP 密码保护的补丁
     * 5. 如果补丁包含资源，使用 ResourceMerger 合并原始 APK 和补丁资源
     * 6. 加载完整资源包和 DEX 补丁
     */
    public void loadPatchIfNeeded() {
        try {
            // 注意：在 attachBaseContext 中不能使用 getApplicationContext()
            // 因为 Application 还没有完全初始化，需要手动创建 SharedPreferences
            android.content.SharedPreferences prefs = context.getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
            String appliedPatchId = prefs.getString("applied_patch_id", null);

            if (appliedPatchId == null || appliedPatchId.isEmpty()) {
                logD("No applied patch to load");
                return;
            }

            logD("Loading applied patch: " + appliedPatchId);

            // 获取已应用的补丁文件
            java.io.File updateDir = new java.io.File(context.getFilesDir(), "update");
            java.io.File appliedDir = new java.io.File(updateDir, "applied");
            java.io.File appliedFile = new java.io.File(appliedDir, "current_patch.zip");

            if (!appliedFile.exists()) {
                logW("Applied patch file not found: " + appliedFile.getAbsolutePath());
                return;
            }

            // ✅ 验证补丁完整性（防止篡改）
            if (!verifyPatchIntegrity(appliedFile, prefs)) {
                logE("⚠️ Patch integrity verification failed");

                // 尝试恢复
                if (!recoverPatch(appliedPatchId, appliedFile, prefs)) {
                    logE("⚠️ Patch recovery failed, patch has been cleared");
                    return;
                }
            }
            
            // ✅ APK 签名验证（启动时验证）- 使用 apksig
            // 检查安全策略是否要求签名
            boolean requireSignature = securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
            
            // 检查补丁在应用时是否有签名（防止攻击者删除签名文件）
            boolean hadSignatureWhenApplied = prefs.getBoolean("patch_had_signature", false);
            boolean hasSignatureNow = hasApkSignatureInternal(appliedFile);
            
            // 漏洞修复1：如果安全策略要求签名，但补丁没有签名，拒绝加载
            if (requireSignature && !hasSignatureNow) {
                logE("⚠️ 安全策略要求补丁必须签名，但当前补丁没有签名！");
                clearPatchCompletely(prefs, appliedFile, appliedPatchId);
                logE("⚠️ 已清除不符合安全策略的补丁");
                return;
            }
            
            // 漏洞修复2：如果补丁应用时有签名，但现在没有了，说明被删除了
            if (hadSignatureWhenApplied && !hasSignatureNow) {
                logE("⚠️ 安全警告：补丁签名文件被删除！这是一次攻击行为。");
                clearPatchCompletely(prefs, appliedFile, appliedPatchId);
                logE("⚠️ 已清除被篡改的补丁");
                return;
            }
            
            // 如果补丁有签名，验证签名
            if (hasSignatureNow) {
                logD("检测到 APK 签名，开始验证...");
                boolean signatureValid = patchSigner.verifyPatchSignatureMatchesApp(appliedFile);
                
                if (!signatureValid) {
                    logE("⚠️ APK 签名验证失败: " + patchSigner.getError());
                    clearPatchCompletely(prefs, appliedFile, appliedPatchId);
                    logE("⚠️ 已清除被篡改的补丁");
                    return;
                }
                
                logD("✅ APK 签名验证通过（启动时）");
            }

            // 检查补丁是否是 ZIP 密码保护的
            java.io.File actualPatchFile = appliedFile;
            if (isZipPasswordProtectedInternal(appliedFile)) {
                logD("Patch is ZIP password protected, decrypting...");
                
                // 获取保存的自定义密码（如果有）
                String customPassword = prefs.getString("custom_zip_password", null);
                
                // 自动解密 ZIP 密码保护的补丁
                actualPatchFile = decryptZipPatchOnLoad(appliedFile, customPassword);
                
                if (actualPatchFile == null) {
                    logE("Failed to decrypt ZIP password protected patch");
                    return;
                }
                
                logD("✓ ZIP password protected patch decrypted");
            }

            String patchPath = actualPatchFile.getAbsolutePath();

            // 检查补丁是否包含资源
            if (hasResourcePatchInternal(actualPatchFile)) {
                logD("Patch contains resources, merging with original APK");

                // 使用 ResourceMerger 合并资源（Tinker 的方式）
                java.io.File mergedResourceFile = new java.io.File(appliedDir, "merged_resources.apk");

                boolean merged = ResourceMerger.mergeResources(
                    context, actualPatchFile, mergedResourceFile);

                if (merged && mergedResourceFile.exists()) {
                    logI("Resources merged successfully, size: " + mergedResourceFile.length());
                    // 使用合并后的完整资源包
                    patchPath = mergedResourceFile.getAbsolutePath();
                } else {
                    logW("Failed to merge resources, using patch directly");
                }
            }

            // 注入 DEX 补丁
            if (!DexPatcher.isPatchInjected(context, patchPath)) {
                DexPatcher.injectPatchDex(context, patchPath);
                logD("Dex patch loaded successfully");
            }

            // 加载资源补丁（使用合并后的完整资源包）
            try {
                ResourcePatcher.loadPatchResources(context, patchPath);
                logD("Resource patch loaded successfully");
            } catch (ResourcePatcher.PatchResourceException e) {
                logW("Failed to load resource patch");
            }

            logI("✅ Patch loading completed with integrity verification");

        } catch (Exception e) {
            logE("Failed to load patch in attachBaseContext", e);
        }
    }
    
    /**
     * 检查补丁是否包含资源
     */
    private boolean hasResourcePatchInternal(java.io.File patchFile) {
        String fileName = patchFile.getName().toLowerCase(java.util.Locale.ROOT);

        // 如果是 APK 或 ZIP 文件，可能包含资源
        if (fileName.endsWith(".apk") || fileName.endsWith(".zip")) {
            return true;
        }

        // 如果是纯 DEX 文件，不包含资源
        if (fileName.endsWith(".dex")) {
            return false;
        }

        // 检查文件魔数
        try {
            byte[] header = new byte[4];
            java.io.FileInputStream fis = new java.io.FileInputStream(patchFile);
            fis.read(header);
            fis.close();

            // ZIP/APK 魔数: PK (0x50 0x4B)
            if (header[0] == 0x50 && header[1] == 0x4B) {
                return true;
            }

            // DEX 魔数: dex\n (0x64 0x65 0x78 0x0A)
            if (header[0] == 0x64 && header[1] == 0x65 &&
                header[2] == 0x78 && header[3] == 0x0A) {
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check patch file type", e);
        }

        return false;
    }

    /**
     * 验证补丁完整性
     */
    private boolean verifyPatchIntegrity(java.io.File patchFile, android.content.SharedPreferences prefs) {
        if (!patchFile.exists()) {
            return false;
        }

        String savedHash = prefs.getString("applied_patch_hash", null);
        if (savedHash == null || savedHash.isEmpty()) {
            Log.w(TAG, "No saved hash, patch may be from old version (backward compatible)");
            return true; // 向后兼容
        }

        String currentHash = calculateSHA256(patchFile);
        if (currentHash == null) {
            Log.e(TAG, "Failed to calculate current hash");
            return false;
        }

        boolean valid = savedHash.equals(currentHash);

        if (valid) {
            logD("✅ Patch integrity verified: " + currentHash.substring(0, 16) + "...");
        } else {
            logE("⚠️ PATCH INTEGRITY CHECK FAILED!");
            logE("Expected: " + savedHash);
            logE("Actual:   " + currentHash);
        }

        return valid;
    }

    /**
     * 恢复补丁
     */
    private boolean recoverPatch(String patchId, java.io.File appliedFile, android.content.SharedPreferences prefs) {
        int tamperCount = prefs.getInt("tamper_count", 0) + 1;
        prefs.edit().putInt("tamper_count", tamperCount).apply();

        logE("⚠️ Patch tampered! Attempt: " + tamperCount + "/3");

        // 超过限制，清除补丁
        if (tamperCount >= 3) {
            logE("⚠️ Too many tamper attempts, clearing patch");
            prefs.edit()
                .remove("applied_patch_id")
                .remove("applied_patch_hash")
                .apply();

            if (appliedFile.exists()) {
                appliedFile.delete();
            }
            return false;
        }

        // 尝试从加密存储恢复
        logI("Attempting to recover from encrypted storage...");

        try {
            java.io.File updateDir = new java.io.File(context.getFilesDir(), "update");
            java.io.File patchesDir = new java.io.File(updateDir, "patches");
            java.io.File encryptedFile = new java.io.File(patchesDir, patchId + ".enc");

            if (!encryptedFile.exists()) {
                Log.e(TAG, "Encrypted patch not found");
                return false;
            }

            // 使用 SecurityManager 解密
            java.io.File decryptedFile = securityManager.decryptPatch(encryptedFile);

            // 替换被篡改的文件
            if (appliedFile.exists()) {
                appliedFile.delete();
            }

            if (!decryptedFile.renameTo(appliedFile)) {
                // 复制文件
                copyFileInternal(decryptedFile, appliedFile);
                decryptedFile.delete();
            }

            // 重新计算哈希
            String newHash = calculateSHA256(appliedFile);
            if (newHash != null) {
                prefs.edit().putString("applied_patch_hash", newHash).apply();
            }

            // 验证恢复结果
            if (verifyPatchIntegrity(appliedFile, prefs)) {
                logI("✅ Patch recovered successfully");
                prefs.edit().putInt("tamper_count", 0).apply();
                return true;
            }

        } catch (Exception e) {
            logE("Failed to recover patch", e);
        }

        return false;
    }

    /**
     * 计算 SHA-256 哈希
     */
    private String calculateSHA256(java.io.File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            fis.close();

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logE("Failed to calculate SHA-256", e);
            return null;
        }
    }
    
    /**
     * 完整清除补丁（包括所有相关文件和配置）
     * 
     * @param prefs SharedPreferences 实例
     * @param appliedFile 补丁文件
     * @param patchId 补丁 ID
     */
    private void clearPatchCompletely(android.content.SharedPreferences prefs, java.io.File appliedFile, String patchId) {
        // 1. 清除 SharedPreferences 中的所有补丁相关键
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.remove("applied_patch_id");
        editor.remove("applied_patch_hash");
        editor.remove("patch_had_signature");
        editor.remove("is_zip_password_protected");
        editor.remove("custom_zip_password");
        editor.remove("tamper_count");
        
        // 清除补丁信息
        if (patchId != null) {
            editor.remove("patch_info_" + patchId);
        }
        
        editor.apply();
        
        // 2. 删除补丁文件
        if (appliedFile != null && appliedFile.exists()) {
            appliedFile.delete();
            logD("✓ 删除补丁文件: " + appliedFile.getName());
        }
        
        // 3. 删除合并的资源文件
        if (appliedFile != null) {
            java.io.File appliedDir = appliedFile.getParentFile();
            if (appliedDir != null) {
                java.io.File mergedResourceFile = new java.io.File(appliedDir, "merged_resources.apk");
                if (mergedResourceFile.exists()) {
                    mergedResourceFile.delete();
                    logD("✓ 删除合并资源文件");
                }
                
                // 删除 oat 目录
                java.io.File oatDir = new java.io.File(appliedDir, "oat");
                if (oatDir.exists()) {
                    deleteDirectoryInternal(oatDir);
                    logD("✓ 删除 oat 目录");
                }
            }
        }
        
        // 4. 清理缓存中的临时解密文件
        java.io.File cacheDir = context.getCacheDir();
        if (cacheDir != null && cacheDir.exists()) {
            java.io.File[] cacheFiles = cacheDir.listFiles();
            if (cacheFiles != null) {
                for (java.io.File file : cacheFiles) {
                    String name = file.getName();
                    if (name.startsWith("patch_decrypted_") || name.startsWith("patch_load_")) {
                        if (file.isDirectory()) {
                            deleteDirectoryInternal(file);
                        } else {
                            file.delete();
                        }
                        logD("✓ 清理缓存文件: " + name);
                    }
                }
            }
        }
        
        logI("✅ 补丁已完全清除");
    }

    /**
     * 复制文件
     */
    private void copyFileInternal(java.io.File source, java.io.File target) throws java.io.IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(source);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(target);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        fos.flush();
        fos.close();
        fis.close();
    }

    /**
     * 检查补丁是否是 ZIP 密码保护的
     */
    private boolean isZipPasswordProtectedInternal(java.io.File patchFile) {
        try {
            net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(patchFile);
            return zipFile.isEncrypted();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查补丁是否有 APK 签名
     */
    private boolean hasApkSignatureInternal(java.io.File patchFile) {
        // 方法1: 检查 zip 内部是否有 META-INF/ 签名文件（新方案）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 检查是否有 META-INF/ 签名文件
                if (name.startsWith("META-INF/") && 
                    (name.endsWith(".SF") || name.endsWith(".RSA") || 
                     name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    zipFile.close();
                    Log.d(TAG, "✓ 检测到 APK 签名文件: " + name);
                    return true;
                }
            }
            zipFile.close();
        } catch (Exception e) {
            Log.d(TAG, "检查 META-INF/ 签名失败: " + e.getMessage());
        }
        
        // 方法2: 检查 zip 内部是否有 signature.sig 标记文件（向后兼容）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.zip.ZipEntry sigEntry = zipFile.getEntry("signature.sig");
            zipFile.close();
            if (sigEntry != null) {
                logD("✓ 检测到 zip 内部的签名标记文件");
                return true;
            }
        } catch (Exception e) {
            logD("检查 zip 内部签名标记失败: " + e.getMessage());
        }
        
        // 方法3: 检查外部 .sig 文件（向后兼容）
        java.io.File signatureFile = new java.io.File(patchFile.getPath() + ".sig");
        if (signatureFile.exists()) {
            logD("✓ 检测到外部签名文件");
            return true;
        }
        
        return false;
    }
    
    /**
     * 在加载时解密 ZIP 密码保护的补丁
     * 使用从应用签名派生的密码或保存的自定义密码自动解密
     * 
     * @param encryptedPatch 加密的补丁文件
     * @param customPassword 自定义密码（如果有）
     */
    private java.io.File decryptZipPatchOnLoad(java.io.File encryptedPatch, String customPassword) {
        try {
            // 创建 ZipPasswordManager 实例
            ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
            
            // 获取密码：优先使用自定义密码，否则使用派生密码
            String zipPassword;
            if (customPassword != null && !customPassword.isEmpty()) {
                Log.d(TAG, "Using custom ZIP password");
                zipPassword = customPassword;
            } else {
                Log.d(TAG, "Using derived ZIP password");
                zipPassword = zipPasswordManager.getZipPassword();
            }
            
            // 解密到临时目录
            java.io.File tempDir = new java.io.File(context.getCacheDir(), "patch_load_" + System.currentTimeMillis());
            tempDir.mkdirs();
            
            boolean extracted = zipPasswordManager.extractEncryptedZip(encryptedPatch, tempDir, zipPassword);
            
            if (!extracted) {
                Log.e(TAG, "Failed to extract encrypted ZIP");
                deleteDirectoryInternal(tempDir);
                return null;
            }
            
            // 重新打包为未加密的 ZIP（临时文件）
            java.io.File decryptedZip = new java.io.File(context.getCacheDir(), "patch_decrypted_" + System.currentTimeMillis() + ".zip");
            repackZipInternal(tempDir, decryptedZip);
            
            // 清理临时目录
            deleteDirectoryInternal(tempDir);
            
            return decryptedZip;
            
        } catch (Exception e) {
            logE("Failed to decrypt ZIP password protected patch", e);
            return null;
        }
    }
    
    /**
     * 删除目录及其所有内容
     */
    private void deleteDirectoryInternal(java.io.File directory) {
        if (directory.exists()) {
            java.io.File[] files = directory.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectoryInternal(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * 重新打包目录为 ZIP 文件（不加密，不压缩）
     */
    private void repackZipInternal(java.io.File sourceDir, java.io.File destZip) throws Exception {
        net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(destZip);
        net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
        params.setCompressionMethod(net.lingala.zip4j.model.enums.CompressionMethod.STORE);
        
        // 添加目录中的所有文件和文件夹
        java.io.File[] files = sourceDir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    zipFile.addFolder(file, params);
                } else {
                    zipFile.addFile(file, params);
                }
            }
        }
    }
    
    /**
     * 清除补丁（回滚）
     * 
     * @return 是否清除成功
     */
    public boolean clearPatch() {
        ensureStorageInitialized();
        String appliedPatchId = storage.getAppliedPatchId();
        if (appliedPatchId != null) {
            return storage.deletePatch(appliedPatchId);
        }
        return true;
    }
    
    /**
     * 检查是否有已应用的补丁
     * 
     * @return 是否有已应用的补丁
     */
    public boolean hasAppliedPatch() {
        ensureStorageInitialized();
        return storage.getAppliedPatchId() != null;
    }
    
    /**
     * 获取已应用的补丁信息
     * 
     * @return 补丁信息，如果没有返回 null
     */
    public PatchInfo getAppliedPatchInfo() {
        ensureStorageInitialized();
        return storage.getAppliedPatchInfo();
    }
    
    /**
     * 获取已应用的补丁 ID
     * 
     * @return 补丁 ID，如果没有返回 null
     */
    public String getAppliedPatchId() {
        PatchInfo patchInfo = getAppliedPatchInfo();
        return patchInfo != null ? patchInfo.getPatchId() : null;
    }
    
    /**
     * 获取补丁应用时间
     * 
     * @return 补丁应用时间戳（毫秒），如果没有返回 0
     */
    public long getPatchTime() {
        PatchInfo patchInfo = getAppliedPatchInfo();
        return patchInfo != null ? patchInfo.getCreateTime() : 0;
    }
    
    /**
     * 检查 DEX 是否已注入
     * 
     * @return 是否已注入 DEX
     */
    public boolean isDexInjected() {
        return hasAppliedPatch() && DexPatcher.isSupported();
    }
    
    /**
     * 获取补丁后的版本号
     * 
     * @return 补丁版本号，如果没有返回 null
     */
    public String getPatchedVersion() {
        PatchInfo patchInfo = storage.getAppliedPatchInfo();
        return patchInfo != null ? patchInfo.getPatchVersion() : null;
    }
    
    /**
     * 获取补丁后的版本代码
     * 
     * @return 补丁版本代码，如果没有返回 null
     */
    public String getPatchedVersionCode() {
        PatchInfo patchInfo = storage.getAppliedPatchInfo();
        if (patchInfo != null && patchInfo.getTargetAppVersion() != null) {
            return patchInfo.getTargetAppVersion();
        }
        return null;
    }
    
    /**
     * 检查是否有已应用的补丁（别名方法，兼容旧API）
     * 
     * @return 是否有已应用的补丁
     */
    public boolean isPatchApplied() {
        return hasAppliedPatch();
    }
    
    /**
     * 获取显示版本
     * 
     * @param originalVersion 原始版本号
     * @return 显示版本（如果有补丁则显示补丁版本）
     */
    public String getDisplayVersion(String originalVersion) {
        if (hasAppliedPatch()) {
            PatchInfo patchInfo = storage.getAppliedPatchInfo();
            if (patchInfo != null && patchInfo.getPatchVersion() != null) {
                return patchInfo.getPatchVersion() + " (热更新)";
            }
        }
        return originalVersion;
    }
    
    /**
     * 获取兼容性信息
     * 
     * @return 兼容性信息描述
     */
    public String getCompatibilityInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("热更新支持: ").append(DexPatcher.isSupported() ? "✓" : "✗").append("\n");
        sb.append("兼容级别: ").append(DexPatcher.getCompatibilityLevel()).append("\n");
        sb.append("SO 热更新: ").append(SoPatcher.isSupported() ? "✓" : "✗").append("\n");
        sb.append("资源热更新: ").append(ResourcePatcher.isSupported() ? "✓" : "✗");
        return sb.toString();
    }
    
    /**
     * 检查当前设备是否支持热更新
     * 
     * @return 是否支持
     */
    public static boolean isSupported() {
        return DexPatcher.isSupported();
    }
    
    /**
     * 获取兼容性级别
     * 
     * @return 兼容性级别描述
     */
    public static String getCompatibilityLevel() {
        return DexPatcher.getCompatibilityLevel();
    }
    
    /**
     * 设置是否强制要求补丁签名
     * 
     * @param required 是否要求签名
     */
    public void setRequireSignature(boolean required) {
        securityPrefs.edit().putBoolean(KEY_REQUIRE_SIGNATURE, required).apply();
    }
    
    /**
     * 设置是否强制要求补丁加密
     * 
     * @param required 是否要求加密
     */
    public void setRequireEncryption(boolean required) {
        securityPrefs.edit().putBoolean(KEY_REQUIRE_ENCRYPTION, required).apply();
    }
    
    /**
     * 检查是否要求补丁签名
     * 
     * @return 是否要求签名
     */
    public boolean isRequireSignature() {
        return securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
    }
    
    /**
     * 检查是否要求补丁加密
     * 
     * @return 是否要求加密
     */
    public boolean isRequireEncryption() {
        return securityPrefs.getBoolean(KEY_REQUIRE_ENCRYPTION, false);
    }
    
    /**
     * 获取 SecurityManager 实例
     * 
     * @return SecurityManager 实例
     */
    public SecurityManager getSecurityManager() {
        ensureSecurityManagerInitialized();
        return securityManager;
    }
    
    /**
     * 检查安全策略
     * 
     * @param patchFile 补丁文件
     * @return 错误消息，如果通过返回 null
     */
    private String checkSecurityPolicy(File patchFile) {
        ensureStorageInitialized();
        
        boolean requireSignature = securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
        boolean requireEncryption = securityPrefs.getBoolean(KEY_REQUIRE_ENCRYPTION, false);
        
        // 检查两种加密方式：AES 加密（.enc）或 ZIP 密码加密
        boolean isAesEncrypted = patchFile.getName().endsWith(".enc");
        boolean isZipPasswordEncrypted = false;
        
        // 检查 ZIP 密码加密
        if (!isAesEncrypted) {
            try {
                ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
                isZipPasswordEncrypted = zipPasswordManager.isEncrypted(patchFile);
            } catch (Exception e) {
                logD("检查 ZIP 密码加密失败: " + e.getMessage());
            }
        }
        
        boolean isEncrypted = isAesEncrypted || isZipPasswordEncrypted;
        
        logD("安全策略 - 要求签名: " + requireSignature + ", 要求加密: " + requireEncryption);
        logD("补丁状态 - AES加密: " + isAesEncrypted + ", ZIP密码加密: " + isZipPasswordEncrypted + ", 已加密: " + isEncrypted);
        
        // 对于加密文件，签名验证将在解密后进行
        // 这里只检查加密要求
        if (requireEncryption && !isEncrypted) {
            return "当前安全策略要求补丁必须加密！此补丁未加密，拒绝应用。\n\n支持的加密方式：\n1. AES 加密（.enc 文件）\n2. ZIP 密码加密";
        }
        
        // 对于未加密文件，直接检查签名
        if (!isEncrypted) {
            boolean hasSignature = checkHasSignature(patchFile);
            logD("补丁状态 - 有签名: " + hasSignature);
            
            if (requireSignature && !hasSignature) {
                return "当前安全策略要求补丁必须签名！此补丁未签名，拒绝应用。";
            }
            
            // APK 签名验证（如果补丁有签名）- 使用 apksig
            if (hasSignature) {
                logD("检测到补丁签名，开始验证 APK 签名...");
                boolean signatureValid = patchSigner.verifyPatchSignatureMatchesApp(patchFile);
                
                if (!signatureValid) {
                    return "⚠️ APK 签名验证失败: " + patchSigner.getError();
                }
                logD("✅ APK 签名验证通过");
            }
        } else {
            // 对于加密文件，签名验证将在解密后进行
            logD("补丁已加密，签名验证将在解密后进行");
        }
        
        return null;
    }
    
    /**
     * 检查补丁是否有签名
     * 
     * @param patchFile 补丁文件
     * @return 是否有签名
     */
    private boolean checkHasSignature(File patchFile) {
        // 方法1: 检查 zip 内部是否有 META-INF/ 签名文件（新方案）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 检查是否有 META-INF/ 签名文件
                if (name.startsWith("META-INF/") && 
                    (name.endsWith(".SF") || name.endsWith(".RSA") || 
                     name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    zipFile.close();
                    Log.d(TAG, "✓ 检测到 APK 签名文件: " + name);
                    return true;
                }
            }
            zipFile.close();
        } catch (Exception e) {
            Log.d(TAG, "检查 META-INF/ 签名失败: " + e.getMessage());
        }
        
        // 方法2: 检查 zip 内部是否有 signature.sig 标记文件（向后兼容）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.zip.ZipEntry sigEntry = zipFile.getEntry("signature.sig");
            zipFile.close();
            if (sigEntry != null) {
                Log.d(TAG, "✓ 检测到 zip 内部的签名标记文件");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "检查 zip 内部签名标记失败: " + e.getMessage());
        }
        
        // 方法3: 检查外部 .sig 文件（向后兼容）
        File signatureFile = new File(patchFile.getPath() + ".sig");
        if (signatureFile.exists()) {
            Log.d(TAG, "✓ 检测到外部签名文件");
            return true;
        }
        
        return false;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
    
    /**
     * 从文件创建 PatchInfo
     */
    private PatchInfo createPatchInfo(File patchFile) {
        PatchInfo patchInfo = new PatchInfo();
        patchInfo.setPatchId("patch_" + System.currentTimeMillis());
        patchInfo.setPatchVersion("1.0");
        patchInfo.setDownloadUrl("file://" + patchFile.getAbsolutePath());
        patchInfo.setFileSize(patchFile.length());
        
        // 计算 MD5
        try {
            String md5 = Md5Utils.calculateMd5(patchFile);
            patchInfo.setMd5(md5);
        } catch (Exception e) {
            Log.w(TAG, "Failed to calculate MD5", e);
            patchInfo.setMd5("unknown");
        }
        
        patchInfo.setCreateTime(System.currentTimeMillis());
        
        return patchInfo;
    }
    
    /**
     * 回调接口
     */
    public interface Callback {
        /**
         * 进度回调
         * 
         * @param percent 进度百分比 (0-100)
         * @param message 进度消息
         */
        void onProgress(int percent, String message);
        
        /**
         * 成功回调
         * 
         * @param result 补丁结果
         */
        void onSuccess(PatchResult result);
        
        /**
         * 错误回调
         * 
         * @param message 错误消息
         */
        void onError(String message);
        
        /**
         * 需要 ZIP 密码回调
         * 
         * 当检测到补丁使用了自定义 ZIP 密码时调用此方法。
         * UI 应该弹出对话框让用户输入密码，然后调用 applyPatchWithZipPassword() 继续应用补丁。
         * 
         * @param patchFile 补丁文件
         */
        default void onZipPasswordRequired(File patchFile) {
            // 默认实现：不处理，直接报错
            onError("补丁需要 ZIP 密码，但未提供密码输入接口");
        }
        
        /**
         * 需要 AES 密码回调
         * 
         * 当 AES 解密失败时调用此方法（可能是使用了自定义密码）。
         * UI 应该弹出对话框让用户输入密码，然后调用 applyPatchWithAesPassword() 继续应用补丁。
         * 
         * @param patchFile 补丁文件
         */
        default void onAesPasswordRequired(File patchFile) {
            // 默认实现：不处理，直接报错
            onError("补丁需要 AES 密码，但未提供密码输入接口");
        }
    }
    
    /**
     * 补丁结果
     */
    public static class PatchResult {
        /** 是否成功 */
        public boolean success;
        
        /** 补丁 ID */
        public String patchId;
        
        /** 旧版本号 */
        public String oldVersion;
        
        /** 新版本号 */
        public String patchVersion;
        
        /** 新版本代码 */
        public String newVersionCode;
        
        /** 补丁大小（字节） */
        public long patchSize;
        
        /** DEX 是否已注入 */
        public boolean dexInjected;
        
        /** SO 库是否已加载 */
        public boolean soLoaded;
        
        /** 资源是否已加载 */
        public boolean resourcesLoaded;
        
        /** 是否需要重启才能看到资源更新 */
        public boolean needsRestart;
        
        @Override
        public String toString() {
            return "PatchResult{" +
                    "success=" + success +
                    ", patchId='" + patchId + '\'' +
                    ", oldVersion='" + oldVersion + '\'' +
                    ", patchVersion='" + patchVersion + '\'' +
                    ", newVersionCode='" + newVersionCode + '\'' +
                    ", patchSize=" + patchSize +
                    ", dexInjected=" + dexInjected +
                    ", soLoaded=" + soLoaded +
                    ", resourcesLoaded=" + resourcesLoaded +
                    ", needsRestart=" + needsRestart +
                    '}';
        }
    }
    
    /**
     * 应用补丁（使用自定义 ZIP 密码）
     * 
     * 当 onZipPasswordRequired() 被调用后，UI 应该获取用户输入的密码，
     * 然后调用此方法继续应用补丁。
     * 
     * @param patchFile 补丁文件
     * @param zipPassword 用户输入的 ZIP 密码
     * @param callback 回调接口
     */
    public void applyPatchWithZipPassword(File patchFile, String zipPassword, Callback callback) {
        ensureExecutorInitialized();
        ensureStorageInitialized();
        
        if (patchFile == null || !patchFile.exists()) {
            if (callback != null) {
                callback.onError("补丁文件不存在");
            }
            return;
        }
        
        if (zipPassword == null || zipPassword.isEmpty()) {
            if (callback != null) {
                callback.onError("ZIP 密码不能为空");
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                // 通知开始
                if (callback != null) {
                    callback.onProgress(5, "准备应用补丁...");
                }
                
                // 1. 检查安全策略
                String securityError = checkSecurityPolicy(patchFile);
                if (securityError != null) {
                    if (callback != null) {
                        callback.onError(securityError);
                    }
                    return;
                }
                
                if (callback != null) {
                    callback.onProgress(10, "验证 ZIP 密码...");
                }
                
                // 2. 验证 ZIP 密码
                ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
                boolean passwordValid = zipPasswordManager.verifyPassword(patchFile, zipPassword);
                
                if (!passwordValid) {
                    if (callback != null) {
                        callback.onError("⚠️ ZIP 密码验证失败！密码错误或补丁已被篡改。");
                    }
                    return;
                }
                
                logD("✓ ZIP 密码验证成功");
                
                // 3. 保存自定义密码到 SharedPreferences（用于应用启动时解密）
                android.content.SharedPreferences prefs = context.getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
                prefs.edit()
                    .putBoolean("is_zip_password_protected", true)
                    .putString("custom_zip_password", zipPassword)
                    .apply();
                
                // 4. 继续应用补丁流程（保存加密文件到 applied 目录）
                applyPatchInternal(patchFile, patchFile, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "应用补丁失败", e);
                if (callback != null) {
                    callback.onError("应用补丁失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 应用补丁（使用自定义 AES 密码）
     * 
     * 当 onAesPasswordRequired() 被调用后，UI 应该获取用户输入的密码，
     * 然后调用此方法继续应用补丁。
     * 
     * @param patchFile 补丁文件（.enc 文件）
     * @param aesPassword 用户输入的 AES 密码
     * @param callback 回调接口
     */
    public void applyPatchWithAesPassword(File patchFile, String aesPassword, Callback callback) {
        ensureExecutorInitialized();
        ensureSecurityManagerInitialized();
        
        if (patchFile == null || !patchFile.exists()) {
            if (callback != null) {
                callback.onError("补丁文件不存在");
            }
            return;
        }
        
        if (aesPassword == null || aesPassword.isEmpty()) {
            if (callback != null) {
                callback.onError("AES 密码不能为空");
            }
            return;
        }
        
        executor.execute(() -> {
            File tempDecryptedFile = null;
            try {
                // 通知开始
                if (callback != null) {
                    callback.onProgress(5, "准备应用补丁...");
                }
                
                // 1. 检查安全策略（对于加密文件，签名验证将在解密后进行）
                String securityError = checkSecurityPolicy(patchFile);
                if (securityError != null) {
                    if (callback != null) {
                        callback.onError(securityError);
                    }
                    return;
                }
                
                if (callback != null) {
                    callback.onProgress(10, "使用密码解密补丁...");
                }
                
                // 2. 使用密码解密 AES 加密的补丁
                tempDecryptedFile = securityManager.decryptPatchWithPassword(patchFile, aesPassword);
                
                logD("✓ AES 解密成功（使用自定义密码）");
                
                // 3. 解密后验证签名（如果要求签名）
                boolean requireSignature = securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
                if (requireSignature) {
                    if (callback != null) {
                        callback.onProgress(15, "验证补丁签名...");
                    }
                    
                    boolean hasSignature = checkHasSignature(tempDecryptedFile);
                    if (!hasSignature) {
                        if (tempDecryptedFile.exists()) {
                            tempDecryptedFile.delete();
                        }
                        if (callback != null) {
                            callback.onError("当前安全策略要求补丁必须签名！此补丁未签名，拒绝应用。");
                        }
                        return;
                    }
                    
                    // 验证签名与应用签名匹配
                    boolean signatureValid = patchSigner.verifyPatchSignatureMatchesApp(tempDecryptedFile);
                    if (!signatureValid) {
                        if (tempDecryptedFile.exists()) {
                            tempDecryptedFile.delete();
                        }
                        if (callback != null) {
                            callback.onError("⚠️ APK 签名验证失败: " + patchSigner.getError());
                        }
                        return;
                    }
                    
                    Log.d(TAG, "✅ APK 签名验证通过（解密后）");
                }
                
                if (callback != null) {
                    callback.onProgress(20, "检查 ZIP 密码保护...");
                }
                
                // 4. 检查并处理 ZIP 密码加密
                ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
                
                if (zipPasswordManager.isEncrypted(tempDecryptedFile)) {
                    Log.d(TAG, "检测到 ZIP 密码加密");
                    
                    // 检查是否有密码提示文件（.zippwd）
                    File zipPasswordFile = new File(patchFile.getPath() + ".zippwd");
                    boolean hasCustomPassword = zipPasswordFile.exists();
                    
                    if (hasCustomPassword) {
                        Log.d(TAG, "检测到自定义 ZIP 密码，需要用户输入");
                        
                        // 清理临时解密文件
                        if (tempDecryptedFile.exists()) {
                            tempDecryptedFile.delete();
                        }
                        
                        // 通知 UI 需要用户输入密码
                        if (callback != null) {
                            callback.onZipPasswordRequired(patchFile);
                        }
                        return;
                    }
                }
                
                if (callback != null) {
                    callback.onProgress(30, "准备应用补丁...");
                }
                
                // 5. 继续应用补丁流程
                File finalTempFile = tempDecryptedFile;
                try {
                    applyPatchInternal(tempDecryptedFile, patchFile, callback);
                } finally {
                    // 清理临时解密文件
                    if (finalTempFile != null && finalTempFile.exists()) {
                        finalTempFile.delete();
                        Log.d(TAG, "✓ 清理临时解密文件");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "应用补丁失败", e);
                if (tempDecryptedFile != null && tempDecryptedFile.exists()) {
                    tempDecryptedFile.delete();
                }
                if (callback != null) {
                    callback.onError("应用补丁失败: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 解密 ZIP 补丁
     * 
     * @param patchFile 补丁文件
     * @param zipPassword ZIP 密码
     * @param callback 回调接口
     * @return 解密后的补丁文件，如果失败返回 null
     */
    private File decryptZipPatch(File patchFile, String zipPassword, Callback callback) {
        try {
            ZipPasswordManager zipPasswordManager = storage.getZipPasswordManager();
            
            // 验证密码
            boolean passwordValid = zipPasswordManager.verifyPassword(patchFile, zipPassword);
            
            if (!passwordValid) {
                if (callback != null) {
                    callback.onError("⚠️ ZIP 密码验证失败！密码错误或补丁已被篡改。");
                }
                return null;
            }
            
            Log.d(TAG, "✓ ZIP 密码验证成功");
            
            if (callback != null) {
                callback.onProgress(18, "解密 ZIP 文件...");
            }
            
            // 解密 ZIP 到临时文件
            File tempDir = new File(context.getCacheDir(), "patch_decrypt_" + System.currentTimeMillis());
            tempDir.mkdirs();
            
            boolean extracted = zipPasswordManager.extractEncryptedZip(patchFile, tempDir, zipPassword);
            
            if (!extracted) {
                if (callback != null) {
                    callback.onError("ZIP 解密失败");
                }
                // 清理临时目录
                deleteDirectory(tempDir);
                return null;
            }
            
            // 重新打包为未加密的 ZIP（临时文件）
            File decryptedZip = new File(context.getCacheDir(), "patch_decrypted_" + System.currentTimeMillis() + ".zip");
            repackZip(tempDir, decryptedZip);
            
            // 清理临时目录
            deleteDirectory(tempDir);
            
            Log.d(TAG, "✓ ZIP 解密完成");
            return decryptedZip;
            
        } catch (Exception e) {
            logE("解密 ZIP 失败", e);
            if (callback != null) {
                callback.onError("解密 ZIP 失败: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * 应用补丁的内部实现（在 ZIP 解密之后）
     * 
     * @param actualPatchFile 实际的补丁文件（可能是解密后的）
     * @param originalPatchFile 原始补丁文件
     * @param callback 回调接口
     */
    private void applyPatchInternal(File actualPatchFile, File originalPatchFile, Callback callback) {
        try {
            if (callback != null) {
                callback.onProgress(20, "准备应用补丁...");
            }
            
            // 判断原始补丁是否是 ZIP 密码保护的
            boolean isZipPasswordProtected = isZipPasswordProtected(originalPatchFile);
            
            // APK 签名验证（应用时再次验证）- 使用 apksig
            if (checkHasSignature(actualPatchFile)) {
                if (callback != null) {
                    callback.onProgress(22, "验证 APK 签名...");
                }
                
                boolean signatureValid = patchSigner.verifyPatchSignatureMatchesApp(actualPatchFile);
                if (!signatureValid) {
                    if (callback != null) {
                        callback.onError("⚠️ APK 签名验证失败: " + patchSigner.getError());
                    }
                    return;
                }
                
                logD("✅ APK 签名验证通过（应用时）");
            }
            
            // 3. 创建 PatchInfo
            PatchInfo patchInfo = createPatchInfo(actualPatchFile);
            
            // 3.5 检查是否重复应用相同补丁（防止 SIGBUS 崩溃）
            PatchInfo currentPatchInfo = storage.getAppliedPatchInfo();
            if (currentPatchInfo != null && 
                patchInfo.getMd5() != null && 
                patchInfo.getMd5().equals(currentPatchInfo.getMd5())) {
                logI("⚠️ 检测到重复补丁（MD5: " + patchInfo.getMd5().substring(0, 8) + "...），跳过应用以防止崩溃");
                
                if (callback != null) {
                    callback.onProgress(100, "补丁已应用，无需重复操作");
                    
                    // 创建成功结果
                    PatchResult result = new PatchResult();
                    result.success = true;
                    result.patchId = currentPatchInfo.getPatchId();
                    result.patchVersion = currentPatchInfo.getPatchVersion();
                    result.patchSize = originalPatchFile.length();
                    result.dexInjected = DexPatcher.isSupported();
                    result.soLoaded = false;
                    result.resourcesLoaded = hasResourcePatch(actualPatchFile);
                    result.needsRestart = false; // 已经应用过，不需要重启
                    
                    callback.onSuccess(result);
                }
                return;
            }
            
            if (callback != null) {
                callback.onProgress(25, "验证补丁文件...");
            }
            
            // 4. 读取补丁文件内容
            // 如果是 ZIP 密码保护的，保存原始加密文件；否则保存实际文件
            File fileToSave = isZipPasswordProtected ? originalPatchFile : actualPatchFile;
            byte[] patchData = readFileToBytes(fileToSave);
            if (patchData == null) {
                if (callback != null) {
                    callback.onError("读取补丁文件失败");
                }
                return;
            }
            
            // 5. 保存补丁文件到存储（ZIP 密码保护的补丁保持加密状态）
            boolean saved = storage.savePatchFile(patchInfo.getPatchId(), patchData);
            if (!saved) {
                if (callback != null) {
                    callback.onError("保存补丁文件失败");
                }
                return;
            }
            
            // 保存 ZIP 密码标记（如果是 ZIP 密码保护的）
            if (isZipPasswordProtected) {
                // 保存一个标记，表示这个补丁是 ZIP 密码保护的
                android.content.SharedPreferences prefs = context.getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("is_zip_password_protected", true).apply();
                logD("✓ 补丁已保存为加密状态到 applied 目录");
            }
            
            if (callback != null) {
                callback.onProgress(40, "应用补丁...");
            }
            
            // 6. 应用补丁（PatchApplier 会自动处理解密和资源合并）
            // 检查是否包含资源，如果包含则显示资源合并进度
            boolean hasResources = hasResourcePatch(actualPatchFile);
            if (hasResources && callback != null) {
                callback.onProgress(50, "合并资源文件...");
            }
            
            boolean success = applier.apply(patchInfo);
            
            if (success) {
                // 记录补丁是否有签名（用于启动时验证）
                boolean hasSignature = checkHasSignature(actualPatchFile);
                android.content.SharedPreferences prefs = context.getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("patch_had_signature", hasSignature).apply();
                logD("✓ 记录补丁签名状态: " + (hasSignature ? "有签名" : "无签名"));
                
                if (hasResources && callback != null) {
                    callback.onProgress(80, "资源合并完成");
                }
                
                // 获取补丁信息
                PatchInfo appliedPatch = storage.getAppliedPatchInfo();
                
                // 创建结果
                PatchResult result = new PatchResult();
                result.success = true;
                result.patchId = appliedPatch != null ? appliedPatch.getPatchId() : null;
                result.patchVersion = appliedPatch != null ? appliedPatch.getPatchVersion() : null;
                result.patchSize = originalPatchFile.length();
                
                // 检查补丁内容类型
                result.dexInjected = DexPatcher.isSupported(); // DEX 热更新是否支持
                result.soLoaded = false; // SO 库加载状态（暂不支持检测）
                result.resourcesLoaded = hasResourcePatch(actualPatchFile); // 检查是否包含资源
                result.needsRestart = result.resourcesLoaded; // 资源更新需要重启
                
                if (callback != null) {
                    callback.onProgress(100, "热更新完成！");
                    callback.onSuccess(result);
                }
            } else {
                if (callback != null) {
                    callback.onError("补丁应用失败");
                }
            }
            
            // 清理临时解密文件
            if (actualPatchFile != originalPatchFile && actualPatchFile.exists()) {
                actualPatchFile.delete();
            }
            
        } catch (Exception e) {
            logE("应用补丁失败", e);
            if (callback != null) {
                callback.onError("应用补丁失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 删除目录及其所有内容
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
    
    /**
     * 检查补丁文件是否是 ZIP 密码保护的
     * 通过检查是否存在 .zippwd 标记文件来判断
     */
    private boolean isZipPasswordProtected(File patchFile) {
        // 检查是否有 .zippwd 标记文件
        File zipPwdFile = new File(patchFile.getPath() + ".zippwd");
        if (zipPwdFile.exists()) {
            return true;
        }
        
        // 或者尝试用 zip4j 检查是否加密
        try {
            net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(patchFile);
            return zipFile.isEncrypted();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 重新打包目录为 ZIP 文件（不加密，不压缩）
     */
    private void repackZip(File sourceDir, File destZip) throws Exception {
        net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(destZip);
        net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
        params.setCompressionMethod(net.lingala.zip4j.model.enums.CompressionMethod.STORE);
        
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    zipFile.addFolder(file, params);
                } else {
                    zipFile.addFile(file, params);
                }
            }
        }
    }
    
    /**
     * 日志级别
     */
    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
    
    /**
     * 日志回调接口
     * 
     * 使用示例：
     * <pre>
     * // 方式1：设置全局日志回调（所有 HotUpdateHelper 实例共享）
     * HotUpdateHelper.setGlobalLogCallback(new HotUpdateHelper.LogCallback() {
     *     {@literal @}Override
     *     public void onLog(LogLevel level, String tag, String message) {
     *         // 自定义日志处理，例如：
     *         // - 写入文件
     *         // - 上传到服务器
     *         // - 显示在 UI 上
     *         // - 过滤敏感信息
     *         switch (level) {
     *             case DEBUG:
     *                 Log.d(tag, message);
     *                 break;
     *             case INFO:
     *                 Log.i(tag, message);
     *                 break;
     *             case WARN:
     *                 Log.w(tag, message);
     *                 break;
     *             case ERROR:
     *                 Log.e(tag, message);
     *                 // 上报错误到服务器
     *                 reportError(message);
     *                 break;
     *         }
     *     }
     * });
     * 
     * // 方式2：设置实例日志回调（仅当前实例使用）
     * HotUpdateHelper helper = new HotUpdateHelper(context);
     * helper.setLogCallback(new HotUpdateHelper.LogCallback() {
     *     {@literal @}Override
     *     public void onLog(LogLevel level, String tag, String message) {
     *         // 自定义日志处理
     *     }
     * });
     * </pre>
     */
    public interface LogCallback {
        /**
         * 日志回调
         * 
         * @param level 日志级别
         * @param tag 日志标签
         * @param message 日志消息
         */
        void onLog(LogLevel level, String tag, String message);
    }
}

