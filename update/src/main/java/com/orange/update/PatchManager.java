package com.orange.update;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 补丁管理器，管理补丁的下载、存储和验证。
 * 
 * 功能：
 * - 下载补丁文件
 * - 验证补丁（签名验证 + MD5 校验）
 * - 删除补丁
 * - 获取已下载和已应用的补丁信息
 * 
 * 集成组件：
 * - SecurityManager: 签名验证和加密存储
 * - PatchDownloader: 文件下载
 * - PatchStorage: 补丁存储管理
 */
public class PatchManager {
    
    private static final String TAG = "PatchManager";
    
    private final Context context;
    private final PatchStorage storage;
    private final PatchDownloader downloader;
    private final SecurityManager securityManager;
    private final UpdateConfig config;
    
    /**
     * 构造函数
     * @param context 应用上下文
     * @param config SDK 配置
     */
    public PatchManager(Context context, UpdateConfig config) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("UpdateConfig cannot be null");
        }
        
        this.context = context.getApplicationContext();
        this.config = config;
        this.securityManager = new SecurityManager(this.context, config.isDebugMode());
        this.storage = new PatchStorage(this.context, this.securityManager);
        this.downloader = new PatchDownloader(config);
    }
    
    /**
     * 构造函数（用于测试，允许注入依赖）
     */
    PatchManager(Context context, UpdateConfig config, PatchStorage storage, 
                 PatchDownloader downloader, SecurityManager securityManager) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.storage = storage;
        this.downloader = downloader;
        this.securityManager = securityManager;
    }

    
    // ==================== 下载功能 ====================
    
    /**
     * 下载补丁
     * @param patchInfo 补丁信息
     * @param callback 下载回调
     */
    public void download(PatchInfo patchInfo, DownloadCallback callback) {
        if (patchInfo == null) {
            notifyError(callback, UpdateErrorCode.ERROR_INVALID_PATCH_FORMAT, 
                    "PatchInfo cannot be null");
            return;
        }
        
        String downloadUrl = patchInfo.getDownloadUrl();
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            notifyError(callback, UpdateErrorCode.ERROR_DOWNLOAD_FAILED, 
                    "Download URL is empty");
            return;
        }
        
        String patchId = patchInfo.getPatchId();
        Log.d(TAG, "Starting download for patch: " + patchId);
        
        // 获取临时下载文件
        File tempFile = new File(storage.getTempDir(), patchId + ".tmp");
        
        // 创建内部回调处理下载完成后的逻辑
        DownloadCallback internalCallback = new DownloadCallback() {
            @Override
            public void onProgress(long current, long total) {
                if (callback != null) {
                    callback.onProgress(current, total);
                }
            }
            
            @Override
            public void onSuccess(File file) {
                // 下载完成后，验证 MD5 并加密存储
                try {
                    // 验证 MD5
                    String expectedMd5 = patchInfo.getMd5();
                    if (expectedMd5 != null && !expectedMd5.isEmpty()) {
                        if (!Md5Utils.verifyMd5(file, expectedMd5)) {
                            Log.e(TAG, "MD5 verification failed for patch: " + patchId);
                            securityManager.secureDelete(file);
                            notifyError(callback, UpdateErrorCode.ERROR_CHECKSUM_MISMATCH,
                                    "MD5 checksum verification failed");
                            return;
                        }
                        Log.d(TAG, "MD5 verification passed for patch: " + patchId);
                    }
                    
                    // 加密存储补丁文件
                    if (!storage.savePatchFromFile(patchId, file)) {
                        Log.e(TAG, "Failed to save patch file: " + patchId);
                        notifyError(callback, UpdateErrorCode.ERROR_FILE_WRITE_FAILED,
                                "Failed to save patch file");
                        return;
                    }
                    
                    // 保存补丁信息
                    storage.savePatchInfo(patchInfo);
                    
                    // 清理临时文件
                    securityManager.secureDelete(file);
                    
                    Log.d(TAG, "Patch downloaded and saved: " + patchId);
                    
                    // 通知成功
                    File savedFile = storage.getPatchFile(patchId);
                    if (callback != null) {
                        callback.onSuccess(savedFile);
                    }
                    
                } catch (IOException e) {
                    Log.e(TAG, "Error processing downloaded patch: " + patchId, e);
                    securityManager.secureDelete(file);
                    notifyError(callback, UpdateErrorCode.ERROR_DOWNLOAD_FAILED,
                            "Error processing downloaded patch: " + e.getMessage());
                }
            }
            
            @Override
            public void onError(int errorCode, String message) {
                Log.e(TAG, "Download failed for patch " + patchId + ": " + message);
                // 清理临时文件
                if (tempFile.exists()) {
                    securityManager.secureDelete(tempFile);
                }
                if (callback != null) {
                    callback.onError(errorCode, message);
                }
            }
        };
        
        // 开始下载
        downloader.download(downloadUrl, tempFile, internalCallback);
    }
    
    /**
     * 取消当前下载
     */
    public void cancelDownload() {
        downloader.cancel();
    }

    
    // ==================== 验证功能 ====================
    
    /**
     * 验证补丁（签名验证 + MD5 校验）
     * @param patchInfo 补丁信息
     * @return 验证是否通过
     */
    public boolean verify(PatchInfo patchInfo) {
        if (patchInfo == null) {
            Log.e(TAG, "PatchInfo is null");
            return false;
        }
        
        String patchId = patchInfo.getPatchId();
        Log.d(TAG, "Verifying patch: " + patchId);
        
        // 检查补丁文件是否存在
        if (!storage.hasPatchFile(patchId)) {
            Log.e(TAG, "Patch file not found: " + patchId);
            return false;
        }
        
        // 读取解密后的补丁数据进行验证
        byte[] patchData = storage.readPatchFile(patchId);
        if (patchData == null) {
            Log.e(TAG, "Failed to read patch file: " + patchId);
            return false;
        }
        
        // 1. MD5 校验
        String expectedMd5 = patchInfo.getMd5();
        if (expectedMd5 != null && !expectedMd5.isEmpty()) {
            String actualMd5 = Md5Utils.calculateMd5(patchData);
            if (!actualMd5.equalsIgnoreCase(expectedMd5)) {
                Log.e(TAG, "MD5 verification failed for patch: " + patchId + 
                        ", expected: " + expectedMd5 + ", actual: " + actualMd5);
                return false;
            }
            Log.d(TAG, "MD5 verification passed for patch: " + patchId);
        }
        
        // 2. 验证补丁格式（基本检查）
        if (!verifyPatchFormat(patchData)) {
            Log.e(TAG, "Invalid patch format: " + patchId);
            return false;
        }
        
        Log.d(TAG, "Patch verification passed: " + patchId);
        return true;
    }
    
    /**
     * 验证补丁签名
     * @param patchInfo 补丁信息
     * @param signature Base64 编码的签名
     * @return 签名是否有效
     */
    public boolean verifySignature(PatchInfo patchInfo, String signature) {
        if (patchInfo == null) {
            Log.e(TAG, "PatchInfo is null");
            return false;
        }
        
        if (signature == null || signature.isEmpty()) {
            // 空签名一律拒绝。
            // 此前 debugMode 下会 return true（视同验证通过），构成验签旁路，已移除。
            Log.e(TAG, "Signature is null or empty"
                    + (config.isDebugMode() ? " (debug mode no longer bypasses verification)" : ""));
            return false;
        }
        
        String patchId = patchInfo.getPatchId();
        
        // 读取解密后的补丁数据
        byte[] patchData = storage.readPatchFile(patchId);
        if (patchData == null) {
            Log.e(TAG, "Failed to read patch file for signature verification: " + patchId);
            return false;
        }
        
        // 验证签名
        boolean valid = securityManager.verifySignature(patchData, signature);
        if (valid) {
            Log.d(TAG, "Signature verification passed for patch: " + patchId);
        } else {
            Log.e(TAG, "Signature verification failed for patch: " + patchId);
        }
        
        return valid;
    }
    
    /**
     * 验证补丁格式
     * @param patchData 补丁数据
     * @return 格式是否有效
     */
    private boolean verifyPatchFormat(byte[] patchData) {
        if (patchData == null || patchData.length == 0) {
            return false;
        }
        
        // 检查 DEX 文件魔数 (dex\n035 或 dex\n036 等)
        if (patchData.length >= 8) {
            // DEX 魔数: 0x64 0x65 0x78 0x0A (dex\n)
            if (patchData[0] == 0x64 && patchData[1] == 0x65 && 
                patchData[2] == 0x78 && patchData[3] == 0x0A) {
                return true;
            }
        }
        
        // 检查 ZIP 文件魔数 (PK)
        if (patchData.length >= 4) {
            if (patchData[0] == 0x50 && patchData[1] == 0x4B) {
                return true;
            }
        }
        
        // 检查自定义补丁格式魔数 (HTUP)
        if (patchData.length >= 4) {
            if (patchData[0] == 'H' && patchData[1] == 'T' && 
                patchData[2] == 'U' && patchData[3] == 'P') {
                return true;
            }
        }
        
        // 如果是调试模式，允许任何格式
        if (config.isDebugMode()) {
            Log.w(TAG, "Unknown patch format, allowed in debug mode");
            return true;
        }
        
        return false;
    }

    
    // ==================== 删除功能 ====================
    
    /**
     * 删除补丁
     * @param patchId 补丁ID
     * @return 是否删除成功
     */
    public boolean deletePatch(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            Log.e(TAG, "Patch ID is null or empty");
            return false;
        }
        
        Log.d(TAG, "Deleting patch: " + patchId);
        return storage.deletePatch(patchId);
    }
    
    /**
     * 清理过期补丁
     * @param keepCount 保留的补丁数量
     * @return 清理的补丁数量
     */
    public int cleanOldPatches(int keepCount) {
        return storage.cleanOldPatches(keepCount);
    }
    
    /**
     * 清理临时文件
     * @return 是否清理成功
     */
    public boolean cleanTempFiles() {
        return storage.cleanTempDirectory();
    }
    
    // ==================== 查询功能 ====================
    
    /**
     * 获取已下载的补丁列表
     * @return 补丁信息列表
     */
    public List<PatchInfo> getDownloadedPatches() {
        return storage.getDownloadedPatches();
    }
    
    /**
     * 获取当前应用的补丁信息
     * @return 当前应用的补丁信息，如果没有返回 null
     */
    public PatchInfo getAppliedPatch() {
        return storage.getAppliedPatchInfo();
    }
    
    /**
     * 获取补丁信息
     * @param patchId 补丁ID
     * @return 补丁信息，如果不存在返回 null
     */
    public PatchInfo getPatchInfo(String patchId) {
        return storage.getPatchInfo(patchId);
    }
    
    /**
     * 检查补丁是否已下载
     * @param patchId 补丁ID
     * @return 是否已下载
     */
    public boolean isPatchDownloaded(String patchId) {
        return storage.hasPatchFile(patchId);
    }
    
    /**
     * 获取补丁文件
     * @param patchId 补丁ID
     * @return 补丁文件
     */
    public File getPatchFile(String patchId) {
        return storage.getPatchFile(patchId);
    }
    
    /**
     * 获取存储空间使用情况
     * @return 已使用的存储空间（字节）
     */
    public long getStorageUsage() {
        return storage.getStorageUsage();
    }
    
    // ==================== 内部方法 ====================
    
    /**
     * 获取 PatchStorage 实例（供 PatchApplier 使用）
     */
    PatchStorage getStorage() {
        return storage;
    }
    
    /**
     * 获取 SecurityManager 实例（供 PatchApplier 使用）
     */
    SecurityManager getSecurityManager() {
        return securityManager;
    }
    
    /**
     * 通知错误
     */
    private void notifyError(DownloadCallback callback, int errorCode, String message) {
        if (callback != null) {
            try {
                callback.onError(errorCode, message);
            } catch (Exception e) {
                Log.e(TAG, "Error in error callback", e);
            }
        }
    }
}
