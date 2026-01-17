package com.orange.update;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * 热更新 Application
 * 
 * 在 attachBaseContext 中加载补丁，确保：
 * 1. DEX 补丁在类加载前注入
 * 2. 资源补丁在 Activity 创建前加载
 * 3. 所有组件都能使用更新后的代码和资源
 */
public class PatchApplication extends Application {
    
    private static final String TAG = "PatchApplication";
    private static final String KEY_NEED_RECOVERY = "need_patch_recovery";
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 在最早的时机加载补丁
        loadPatchIfNeeded();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 在 onCreate 中检查是否需要恢复补丁
        // 此时 Application Context 已完全初始化，可以使用 SecurityManager
        recoverPatchIfNeeded();
    }
    
    /**
     * 加载已应用的补丁
     * 
     * 1. 检查是否有已应用的补丁
     * 2. 验证补丁完整性（防止篡改）
     * 3. 如果补丁包含资源，使用 ResourceMerger 合并原始 APK 和补丁资源
     * 4. 生成完整资源包到 merged_resources.apk
     * 5. 加载完整资源包（而不是直接使用补丁）
     * 6. 加载 DEX 补丁
     */
    private void loadPatchIfNeeded() {
        try {
            // 注意：在 attachBaseContext 中不能使用 getApplicationContext()
            // 因为 Application 还没有完全初始化，需要手动创建 SharedPreferences
            android.content.SharedPreferences prefs = getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
            String appliedPatchId = prefs.getString("applied_patch_id", null);
            
            if (appliedPatchId == null || appliedPatchId.isEmpty()) {
                Log.d(TAG, "No applied patch to load");
                return;
            }
            
            Log.d(TAG, "Loading applied patch: " + appliedPatchId);
            
            // 获取已应用的补丁文件
            java.io.File updateDir = new java.io.File(getFilesDir(), "update");
            java.io.File appliedDir = new java.io.File(updateDir, "applied");
            java.io.File appliedFile = new java.io.File(appliedDir, "current_patch.zip");
            
            if (!appliedFile.exists()) {
                Log.w(TAG, "Applied patch file not found: " + appliedFile.getAbsolutePath());
                return;
            }
            
            // ✅ 验证补丁完整性（防止篡改）
            if (!verifyPatchIntegrity(appliedFile, prefs)) {
                Log.e(TAG, "⚠️ Patch integrity verification failed");
                
                // 在 attachBaseContext 中无法使用 SecurityManager（需要完整的 Application Context）
                // 因此我们只记录篡改并清除补丁，不尝试恢复
                handleTamperedPatch(appliedPatchId, appliedFile, prefs);
                return;
            }
            
            String patchPath = appliedFile.getAbsolutePath();
            
            // 检查补丁是否包含资源
            if (hasResourcePatch(appliedFile)) {
                Log.d(TAG, "Patch contains resources, merging with original APK");
                
                // 使用 ResourceMerger 合并资源（Tinker 的方式）
                java.io.File mergedResourceFile = new java.io.File(appliedDir, "merged_resources.apk");
                
                boolean merged = ResourceMerger.mergeResources(
                    this, appliedFile, mergedResourceFile);
                
                if (merged && mergedResourceFile.exists()) {
                    Log.i(TAG, "Resources merged successfully, size: " + mergedResourceFile.length());
                    // 使用合并后的完整资源包
                    patchPath = mergedResourceFile.getAbsolutePath();
                } else {
                    Log.w(TAG, "Failed to merge resources, using patch directly");
                }
            }
            
            // 注入 DEX 补丁
            if (!DexPatcher.isPatchInjected(this, patchPath)) {
                DexPatcher.injectPatchDex(this, patchPath);
                Log.d(TAG, "Dex patch loaded successfully");
            }
            
            // 加载资源补丁（使用合并后的完整资源包）
            try {
                ResourcePatcher.loadPatchResources(this, patchPath);
                Log.d(TAG, "Resource patch loaded successfully");
            } catch (ResourcePatcher.PatchResourceException e) {
                Log.w(TAG, "Failed to load resource patch", e);
            }
            
            Log.i(TAG, "✅ Patch loading completed with integrity verification");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load patch in attachBaseContext", e);
        }
    }
    
    /**
     * 检查补丁是否包含资源
     */
    private boolean hasResourcePatch(java.io.File patchFile) {
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
            Log.d(TAG, "✅ Patch integrity verified: " + currentHash.substring(0, 16) + "...");
        } else {
            Log.e(TAG, "⚠️ PATCH INTEGRITY CHECK FAILED!");
            Log.e(TAG, "Expected: " + savedHash);
            Log.e(TAG, "Actual:   " + currentHash);
        }
        
        return valid;
    }
    
    /**
     * 处理被篡改的补丁
     * 
     * 注意：在 attachBaseContext 中无法使用 SecurityManager 进行恢复
     * 因为 SecurityManager 需要访问 KeyStore，而 KeyStore 需要完整的 Application Context
     * 
     * 策略：
     * 1. 记录篡改次数
     * 2. 清除被篡改的补丁文件
     * 3. 标记需要恢复（在 onCreate 中执行）
     * 4. 超过 3 次后清除所有补丁元数据
     */
    private void handleTamperedPatch(String patchId, java.io.File appliedFile, android.content.SharedPreferences prefs) {
        int tamperCount = prefs.getInt("tamper_count", 0) + 1;
        prefs.edit().putInt("tamper_count", tamperCount).apply();
        
        Log.e(TAG, "⚠️ Patch tampered! Attempt: " + tamperCount + "/3");
        
        // 删除被篡改的文件
        if (appliedFile.exists()) {
            appliedFile.delete();
            Log.d(TAG, "Deleted tampered patch file");
        }
        
        // 超过限制，清除所有补丁元数据
        if (tamperCount >= 3) {
            Log.e(TAG, "⚠️ Too many tamper attempts (" + tamperCount + "), clearing all patch metadata");
            prefs.edit()
                .remove("applied_patch_id")
                .remove("applied_patch_hash")
                .remove("tamper_count")
                .remove(KEY_NEED_RECOVERY)
                .apply();
            
            // 清除 merged_resources.apk
            java.io.File updateDir = new java.io.File(getFilesDir(), "update");
            java.io.File appliedDir = new java.io.File(updateDir, "applied");
            java.io.File mergedFile = new java.io.File(appliedDir, "merged_resources.apk");
            if (mergedFile.exists()) {
                mergedFile.delete();
            }
            
            Log.e(TAG, "⚠️ All patch data cleared. User needs to re-apply patch.");
        } else {
            // 标记需要恢复（在 onCreate 中执行）
            prefs.edit().putBoolean(KEY_NEED_RECOVERY, true).apply();
            Log.w(TAG, "⚠️ Patch cleared. Will attempt recovery in onCreate()");
        }
    }
    
    /**
     * 在 onCreate 中恢复被篡改的补丁
     * 
     * 此时 Application Context 已完全初始化，可以使用 SecurityManager
     */
    private void recoverPatchIfNeeded() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
            
            // 检查是否需要恢复
            boolean needRecovery = prefs.getBoolean(KEY_NEED_RECOVERY, false);
            if (!needRecovery) {
                return;
            }
            
            String appliedPatchId = prefs.getString("applied_patch_id", null);
            if (appliedPatchId == null || appliedPatchId.isEmpty()) {
                Log.d(TAG, "No patch ID to recover");
                prefs.edit().remove(KEY_NEED_RECOVERY).apply();
                return;
            }
            
            Log.i(TAG, "🔄 Attempting to recover patch from encrypted storage: " + appliedPatchId);
            
            // 使用 PatchStorage 从加密存储恢复
            PatchStorage storage = new PatchStorage(this);
            java.io.File recoveredFile = storage.decryptPatchToApplied(appliedPatchId);
            
            if (recoveredFile != null && recoveredFile.exists()) {
                // 验证恢复的补丁
                String newHash = calculateSHA256(recoveredFile);
                String savedHash = prefs.getString("applied_patch_hash", null);
                
                if (newHash != null && newHash.equals(savedHash)) {
                    Log.i(TAG, "✅ Patch recovered successfully from encrypted storage");
                    Log.i(TAG, "✅ Hash verified: " + newHash.substring(0, 16) + "...");
                    
                    // 重置篡改计数和恢复标记
                    prefs.edit()
                        .putInt("tamper_count", 0)
                        .remove(KEY_NEED_RECOVERY)
                        .apply();
                    
                    // 提示用户重启应用以加载恢复的补丁
                    Log.i(TAG, "⚠️ Please restart the app to load the recovered patch");
                    
                    // 可选：显示 Toast 提示用户
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            android.widget.Toast.makeText(
                                PatchApplication.this,
                                "补丁已恢复，请重启应用",
                                android.widget.Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                } else {
                    Log.e(TAG, "❌ Recovered patch hash mismatch");
                    Log.e(TAG, "Expected: " + savedHash);
                    Log.e(TAG, "Actual:   " + newHash);
                    
                    // 恢复失败，增加篡改计数
                    int tamperCount = prefs.getInt("tamper_count", 0) + 1;
                    prefs.edit().putInt("tamper_count", tamperCount).apply();
                    
                    if (tamperCount >= 3) {
                        Log.e(TAG, "⚠️ Too many failed recovery attempts, clearing all patch data");
                        prefs.edit()
                            .remove("applied_patch_id")
                            .remove("applied_patch_hash")
                            .remove("tamper_count")
                            .remove(KEY_NEED_RECOVERY)
                            .apply();
                    }
                }
            } else {
                Log.e(TAG, "❌ Failed to recover patch from encrypted storage");
                prefs.edit().remove(KEY_NEED_RECOVERY).apply();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to recover patch in onCreate", e);
        }
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
            Log.e(TAG, "Failed to calculate SHA-256", e);
            return null;
        }
    }
    
    /**
     * 复制文件
     */
    private void copyFile(java.io.File source, java.io.File target) throws java.io.IOException {
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
}
