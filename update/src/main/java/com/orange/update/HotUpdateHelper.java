package com.orange.update;

import android.content.Context;
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
    private final PatchStorage storage;
    private final PatchApplier applier;
    private final ExecutorService executor;
    
    /**
     * 构造函数
     * @param context 应用上下文
     */
    public HotUpdateHelper(Context context) {
        this.context = context.getApplicationContext();
        this.storage = new PatchStorage(this.context);
        this.applier = new PatchApplier(this.context, storage);
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 应用补丁（异步）
     * 
     * @param patchFile 补丁文件
     * @param callback 回调接口
     */
    public void applyPatch(File patchFile, Callback callback) {
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
                    callback.onProgress(10, "准备应用补丁...");
                }
                
                // 创建 PatchInfo
                PatchInfo patchInfo = createPatchInfo(patchFile);
                
                if (callback != null) {
                    callback.onProgress(20, "验证补丁文件...");
                }
                
                // 应用补丁
                boolean success = applier.apply(patchInfo);
                
                if (success) {
                    // 获取补丁信息
                    PatchInfo appliedPatch = storage.getAppliedPatchInfo();
                    
                    // 创建结果
                    PatchResult result = new PatchResult();
                    result.success = true;
                    result.patchId = appliedPatch != null ? appliedPatch.getPatchId() : null;
                    result.patchVersion = appliedPatch != null ? appliedPatch.getPatchVersion() : null;
                    result.patchSize = patchFile.length();
                    result.needsRestart = true; // 资源更新需要重启
                    
                    if (callback != null) {
                        callback.onProgress(100, "热更新完成！");
                        callback.onSuccess(result);
                    }
                } else {
                    if (callback != null) {
                        callback.onError("补丁应用失败");
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
     * 应用补丁（同步）
     * 
     * @param patchFile 补丁文件
     * @return 是否应用成功
     */
    public boolean applyPatchSync(File patchFile) {
        if (patchFile == null || !patchFile.exists()) {
            return false;
        }
        
        try {
            PatchInfo patchInfo = createPatchInfo(patchFile);
            return applier.apply(patchInfo);
        } catch (Exception e) {
            Log.e(TAG, "应用补丁失败", e);
            return false;
        }
    }
    
    /**
     * 加载已应用的补丁
     * 
     * 此方法应在 Application.attachBaseContext() 中调用
     */
    public void loadAppliedPatch() {
        applier.loadAppliedPatch();
    }
    
    /**
     * 清除补丁（回滚）
     * 
     * @return 是否清除成功
     */
    public boolean clearPatch() {
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
        return storage.getAppliedPatchId() != null;
    }
    
    /**
     * 获取已应用的补丁信息
     * 
     * @return 补丁信息，如果没有返回 null
     */
    public PatchInfo getAppliedPatchInfo() {
        return storage.getAppliedPatchInfo();
    }
    
    /**
     * 获取已应用的补丁 ID
     * 
     * @return 补丁 ID，如果没有返回 null
     */
    public String getAppliedPatchId() {
        PatchInfo patchInfo = storage.getAppliedPatchInfo();
        return patchInfo != null ? patchInfo.getPatchId() : null;
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
    }
    
    /**
     * 补丁结果
     */
    public static class PatchResult {
        /** 是否成功 */
        public boolean success;
        
        /** 补丁 ID */
        public String patchId;
        
        /** 补丁版本 */
        public String patchVersion;
        
        /** 补丁大小（字节） */
        public long patchSize;
        
        /** 是否需要重启才能看到资源更新 */
        public boolean needsRestart;
        
        @Override
        public String toString() {
            return "PatchResult{" +
                    "success=" + success +
                    ", patchId='" + patchId + '\'' +
                    ", patchVersion='" + patchVersion + '\'' +
                    ", patchSize=" + patchSize +
                    ", needsRestart=" + needsRestart +
                    '}';
        }
    }
}
