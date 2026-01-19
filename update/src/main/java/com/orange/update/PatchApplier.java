package com.orange.update;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
 * 补丁应用器，负责将补丁应用到应用程序。
 * 
 * 功能：
 * - 应用补丁（解密 -> DexPatcher 注入）
 * - 回滚到上一版本
 * - 应用启动时加载已应用的补丁
 * 
 * 集成组件：
 * - SecurityManager: 补丁解密
 * - DexPatcher: Dex 注入
 * - ResourcePatcher: 资源替换
 * - PatchStorage: 补丁存储管理
 */
public class PatchApplier {
    
    private static final String TAG = "PatchApplier";
    
    private final Context context;
    private final PatchStorage storage;
    private final SecurityManager securityManager;
    
    /**
     * 构造函数
     * @param context 应用上下文
     */
    public PatchApplier(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        this.securityManager = new SecurityManager(this.context);
        this.storage = new PatchStorage(this.context, this.securityManager);
    }
    
    /**
     * 构造函数（用于测试，允许注入依赖）
     */
    PatchApplier(Context context, PatchStorage storage, SecurityManager securityManager) {
        this.context = context.getApplicationContext();
        this.storage = storage;
        this.securityManager = securityManager;
    }
    
    /**
     * 构造函数（与 PatchManager 共享存储）
     * @param context 应用上下文
     * @param storage 补丁存储实例
     */
    public PatchApplier(Context context, PatchStorage storage) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (storage == null) {
            throw new IllegalArgumentException("PatchStorage cannot be null");
        }
        this.context = context.getApplicationContext();
        this.storage = storage;
        this.securityManager = storage.getSecurityManager();
    }

    
    // ==================== 应用补丁 ====================
    
    /**
     * 应用补丁
     * 流程：解密补丁 -> DexPatcher 注入 -> 更新状态
     * 
     * @param patchInfo 补丁信息
     * @return 是否应用成功
     */
    public boolean apply(PatchInfo patchInfo) {
        if (patchInfo == null) {
            Log.e(TAG, "PatchInfo is null");
            return false;
        }
        
        String patchId = patchInfo.getPatchId();
        Log.d(TAG, "Applying patch: " + patchId);
        
        // 检查补丁文件是否存在
        if (!storage.hasPatchFile(patchId)) {
            Log.e(TAG, "Patch file not found: " + patchId);
            return false;
        }
        
        // 检查是否已经应用了相同的补丁（通过 MD5 校验）
        String currentAppliedId = storage.getAppliedPatchId();
        if (patchId.equals(currentAppliedId)) {
            Log.w(TAG, "Patch already applied: " + patchId);
            
            // 额外验证：检查补丁文件的 MD5 是否匹配
            PatchInfo currentPatchInfo = storage.getAppliedPatchInfo();
            if (currentPatchInfo != null && 
                patchInfo.getMd5() != null && 
                patchInfo.getMd5().equals(currentPatchInfo.getMd5())) {
                Log.i(TAG, "Patch MD5 matches, skipping duplicate application to prevent crash");
                return true;
            }
            
            // 如果 MD5 不匹配，说明是不同的补丁，继续应用
            Log.w(TAG, "Patch ID matches but MD5 differs, applying new patch");
        }
        
        try {
            // 1. 备份当前补丁（如果有）
            if (currentAppliedId != null) {
                Log.d(TAG, "Backing up current patch: " + currentAppliedId);
                if (!storage.backupCurrentPatch()) {
                    Log.w(TAG, "Failed to backup current patch, continuing anyway");
                }
            }
            
            // 2. 解密补丁到应用目录
            File appliedPatchFile = storage.decryptPatchToApplied(patchId);
            if (appliedPatchFile == null || !appliedPatchFile.exists()) {
                Log.e(TAG, "Failed to decrypt patch: " + patchId);
                return false;
            }
            
            // 3. 注入补丁 Dex
            String patchPath = appliedPatchFile.getAbsolutePath();
            try {
                DexPatcher.injectPatchDex(context, patchPath);
                Log.d(TAG, "Dex patch injected successfully");
            } catch (DexPatcher.PatchException e) {
                Log.e(TAG, "Failed to inject dex patch", e);
                // 清理解密的文件
                securityManager.secureDelete(appliedPatchFile);
                return false;
            }
            
            // 4. 如果包含资源，进行资源合并（Tinker 的方式）
            try {
                if (hasResourcePatch(appliedPatchFile)) {
                    Log.d(TAG, "Patch contains resources, merging with original APK");
                    
                    // 使用 ResourceMerger 合并资源
                    File mergedResourceFile = new File(
                        storage.getAppliedDir(), "merged_resources.apk");
                    
                    boolean merged = ResourceMerger.mergeResources(
                        context, appliedPatchFile, mergedResourceFile);
                    
                    if (merged && mergedResourceFile.exists()) {
                        Log.i(TAG, "Resources merged successfully, size: " + mergedResourceFile.length());
                        // 使用合并后的完整资源包加载资源
                        patchPath = mergedResourceFile.getAbsolutePath();
                    } else {
                        Log.w(TAG, "Failed to merge resources, using patch directly");
                    }
                    
                    // 加载资源补丁（使用合并后的完整资源包）
                    ResourcePatcher.loadPatchResources(context, patchPath);
                    Log.d(TAG, "Resource patch loaded successfully");
                }
            } catch (ResourcePatcher.PatchResourceException e) {
                // 资源加载失败不影响 Dex 补丁
                Log.w(TAG, "Failed to load resource patch, continuing with dex only", e);
            }
            
            // 5. 更新应用状态
            storage.saveAppliedPatchId(patchId);
            storage.savePatchInfo(patchInfo);
            
            Log.i(TAG, "Patch applied successfully: " + patchId);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply patch: " + patchId, e);
            // 尝试回滚
            rollbackOnFailure();
            return false;
        }
    }
    
    /**
     * 检查补丁是否包含资源
     */
    private boolean hasResourcePatch(File patchFile) {
        // 检查文件扩展名或内容
        String fileName = patchFile.getName().toLowerCase(Locale.ROOT);
        
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
     * 应用失败时的回滚处理
     */
    private void rollbackOnFailure() {
        try {
            // 清理已应用的补丁文件
            File appliedFile = storage.getAppliedPatchFile();
            if (appliedFile.exists()) {
                securityManager.secureDelete(appliedFile);
            }
            
            // 恢复之前的状态
            String previousPatchId = storage.getPreviousPatchId();
            if (previousPatchId != null) {
                storage.saveAppliedPatchId(previousPatchId);
            } else {
                storage.saveAppliedPatchId(null);
            }
            
            Log.d(TAG, "Rollback on failure completed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to rollback on failure", e);
        }
    }

    
    // ==================== 回滚功能 ====================
    
    /**
     * 回滚到上一版本
     * 
     * @return 是否回滚成功
     */
    public boolean rollback() {
        Log.d(TAG, "Starting rollback");
        
        String currentPatchId = storage.getAppliedPatchId();
        String previousPatchId = storage.getPreviousPatchId();
        
        try {
            // 1. 清理当前应用的补丁文件
            File appliedFile = storage.getAppliedPatchFile();
            if (appliedFile.exists()) {
                securityManager.secureDelete(appliedFile);
                Log.d(TAG, "Cleared current applied patch file");
            }
            
            // 2. 如果有上一个补丁，恢复它
            if (previousPatchId != null) {
                String restoredPatchId = storage.restoreBackupPatch();
                if (restoredPatchId != null) {
                    Log.d(TAG, "Restored previous patch: " + restoredPatchId);
                    
                    // 重新注入上一个补丁
                    File restoredFile = storage.getAppliedPatchFile();
                    if (restoredFile.exists()) {
                        try {
                            DexPatcher.injectPatchDex(context, restoredFile.getAbsolutePath());
                            Log.d(TAG, "Previous patch re-injected");
                        } catch (DexPatcher.PatchException e) {
                            Log.w(TAG, "Failed to re-inject previous patch", e);
                            // 继续回滚到原始状态
                            storage.saveAppliedPatchId(null);
                            storage.clearBackup();
                        }
                    }
                } else {
                    // 恢复失败，回滚到原始状态
                    Log.w(TAG, "Failed to restore backup, rolling back to original state");
                    storage.saveAppliedPatchId(null);
                }
            } else {
                // 没有上一个补丁，回滚到原始状态
                Log.d(TAG, "No previous patch, rolling back to original state");
                storage.saveAppliedPatchId(null);
            }
            
            // 3. 清理备份
            storage.clearBackup();
            
            Log.i(TAG, "Rollback completed successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Rollback failed", e);
            return false;
        }
    }
    
    /**
     * 回滚到原始状态（清除所有补丁）
     * 
     * @return 是否回滚成功
     */
    public boolean rollbackToOriginal() {
        Log.d(TAG, "Rolling back to original state");
        
        try {
            // 1. 清理已应用的补丁文件
            File appliedFile = storage.getAppliedPatchFile();
            if (appliedFile.exists()) {
                securityManager.secureDelete(appliedFile);
            }
            
            // 2. 清除应用状态
            storage.saveAppliedPatchId(null);
            storage.savePreviousPatchId(null);
            
            // 3. 清理备份
            storage.clearBackup();
            
            Log.i(TAG, "Rolled back to original state");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to rollback to original state", e);
            return false;
        }
    }

    
    // ==================== 启动时加载 ====================
    
    /**
     * 加载已应用的补丁（应用启动时调用）
     * 
     * 此方法应在 Application.onCreate() 中尽早调用，
     * 以确保补丁在应用启动时生效。
     */
    public void loadAppliedPatch() {
        String appliedPatchId = storage.getAppliedPatchId();
        
        if (appliedPatchId == null || appliedPatchId.isEmpty()) {
            Log.d(TAG, "No applied patch to load");
            return;
        }
        
        Log.d(TAG, "Loading applied patch: " + appliedPatchId);
        
        // 检查已应用的补丁文件是否存在
        File appliedFile = storage.getAppliedPatchFile();
        if (!appliedFile.exists()) {
            Log.w(TAG, "Applied patch file not found, trying to restore from encrypted storage");
            
            // 尝试从加密存储恢复
            File decryptedFile = storage.decryptPatchToApplied(appliedPatchId);
            if (decryptedFile == null || !decryptedFile.exists()) {
                Log.e(TAG, "Failed to restore applied patch, clearing state");
                storage.saveAppliedPatchId(null);
                return;
            }
            appliedFile = decryptedFile;
        }
        
        // 注入补丁
        try {
            String patchPath = appliedFile.getAbsolutePath();
            
            // 检查补丁是否已经注入
            if (DexPatcher.isPatchInjected(context, patchPath)) {
                Log.d(TAG, "Patch already injected, skipping");
                return;
            }
            
            // 注入 Dex 补丁
            DexPatcher.injectPatchDex(context, patchPath);
            Log.d(TAG, "Dex patch loaded successfully");
            
            // 加载资源补丁（如果存在）
            if (hasResourcePatch(appliedFile)) {
                try {
                    ResourcePatcher.loadPatchResources(context, patchPath);
                    Log.d(TAG, "Resource patch loaded successfully");
                } catch (ResourcePatcher.PatchResourceException e) {
                    Log.w(TAG, "Failed to load resource patch", e);
                }
            }
            
            Log.i(TAG, "Applied patch loaded: " + appliedPatchId);
            
        } catch (DexPatcher.PatchException e) {
            Log.e(TAG, "Failed to load applied patch", e);
            
            // 加载失败，清理状态
            handleLoadFailure(appliedPatchId);
        }
    }
    
    /**
     * 处理补丁加载失败
     */
    private void handleLoadFailure(String patchId) {
        Log.w(TAG, "Handling load failure for patch: " + patchId);
        
        // 清理已应用的补丁文件
        File appliedFile = storage.getAppliedPatchFile();
        if (appliedFile.exists()) {
            securityManager.secureDelete(appliedFile);
        }
        
        // 尝试恢复上一个补丁
        String previousPatchId = storage.getPreviousPatchId();
        if (previousPatchId != null && !previousPatchId.equals(patchId)) {
            Log.d(TAG, "Trying to restore previous patch: " + previousPatchId);
            String restoredId = storage.restoreBackupPatch();
            if (restoredId != null) {
                // 递归加载恢复的补丁
                loadAppliedPatch();
                return;
            }
        }
        
        // 无法恢复，清除状态
        storage.saveAppliedPatchId(null);
        storage.clearBackup();
        Log.w(TAG, "Cleared patch state due to load failure");
    }
    
    // ==================== 查询功能 ====================
    
    /**
     * 获取当前应用的补丁信息
     * @return 当前应用的补丁信息，如果没有返回 null
     */
    public PatchInfo getAppliedPatchInfo() {
        return storage.getAppliedPatchInfo();
    }
    
    /**
     * 获取当前应用的补丁ID
     * @return 当前应用的补丁ID，如果没有返回 null
     */
    public String getAppliedPatchId() {
        return storage.getAppliedPatchId();
    }
    
    /**
     * 检查是否有已应用的补丁
     * @return 是否有已应用的补丁
     */
    public boolean hasAppliedPatch() {
        String patchId = storage.getAppliedPatchId();
        return patchId != null && !patchId.isEmpty();
    }
    
    /**
     * 检查补丁是否已应用
     * @param patchId 补丁ID
     * @return 是否已应用
     */
    public boolean isPatchApplied(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            return false;
        }
        String appliedId = storage.getAppliedPatchId();
        return patchId.equals(appliedId);
    }
    
    /**
     * 获取已应用的补丁文件
     * @return 已应用的补丁文件，如果没有返回 null
     */
    public File getAppliedPatchFile() {
        File file = storage.getAppliedPatchFile();
        return file.exists() ? file : null;
    }
    
    // ==================== 兼容性检查 ====================
    
    /**
     * 检查当前设备是否支持热更新
     * @return 是否支持
     */
    public static boolean isSupported() {
        return DexPatcher.isSupported();
    }
    
    /**
     * 获取兼容性级别描述
     * @return 兼容性级别描述
     */
    public static String getCompatibilityLevel() {
        return DexPatcher.getCompatibilityLevel();
    }
    
    /**
     * 检查是否需要特殊处理
     * @return 是否需要特殊处理
     */
    public static boolean requiresSpecialHandling() {
        return DexPatcher.requiresSpecialHandling();
    }
}
