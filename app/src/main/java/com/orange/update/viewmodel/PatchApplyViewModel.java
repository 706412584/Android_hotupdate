package com.orange.update.viewmodel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.orange.update.HotUpdateHelper;
import com.orange.update.ZipPasswordManager;

import java.io.File;

/**
 * 补丁应用 ViewModel
 * 管理补丁应用的业务逻辑和状态
 */
public class PatchApplyViewModel extends ViewModel {
    
    private static final String TAG = "PatchApplyViewModel";
    
    // 安全策略配置
    private static final String PREFS_SECURITY = "security_policy";
    private static final String KEY_REQUIRE_SIGNATURE = "require_signature";
    private static final String KEY_REQUIRE_ENCRYPTION = "require_encryption";
    
    private final MutableLiveData<Integer> applyProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> applyStatus = new MutableLiveData<>("");
    private final MutableLiveData<HotUpdateHelper.PatchResult> applyResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isApplying = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isPatchApplied = new MutableLiveData<>(false);
    private final MutableLiveData<SecurityPolicyError> securityPolicyError = new MutableLiveData<>();
    
    private File patchFile;
    private HotUpdateHelper hotUpdateHelper;
    private Context context;
    
    public void setHotUpdateHelper(HotUpdateHelper helper) {
        this.hotUpdateHelper = helper;
        updatePatchStatus();
    }
    
    public void setContext(Context context) {
        this.context = context;
    }
    
    public void setPatchFile(File file) {
        this.patchFile = file;
    }
    
    public File getPatchFile() {
        return patchFile;
    }
    
    public boolean canApply() {
        return patchFile != null && !Boolean.TRUE.equals(isApplying.getValue());
    }
    
    public void updatePatchStatus() {
        if (hotUpdateHelper != null) {
            isPatchApplied.setValue(hotUpdateHelper.isPatchApplied());
        }
    }
    
    /**
     * 应用补丁（带安全策略检查）
     */
    public void applyPatch() {
        if (!canApply() || hotUpdateHelper == null) {
            HotUpdateHelper.PatchResult failureResult = new HotUpdateHelper.PatchResult();
            failureResult.success = false;
            applyResult.setValue(failureResult);
            applyStatus.setValue("请先选择补丁文件");
            return;
        }
        
        // 1. 执行安全策略检查
        SecurityPolicyError error = checkSecurityPolicy(patchFile);
        if (error != null) {
            // 通知 Fragment 显示安全策略错误对话框
            securityPolicyError.setValue(error);
            return;
        }
        
        // 2. 安全策略检查通过，继续应用补丁
        isApplying.setValue(true);
        applyProgress.setValue(0);
        applyStatus.setValue("开始应用补丁...");
        
        hotUpdateHelper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
            @Override
            public void onProgress(int percent, String message) {
                applyProgress.postValue(percent);
                applyStatus.postValue(message);
            }
            
            @Override
            public void onSuccess(HotUpdateHelper.PatchResult result) {
                isApplying.postValue(false);
                isPatchApplied.postValue(true);
                applyResult.postValue(result);
            }
            
            @Override
            public void onError(String message) {
                isApplying.postValue(false);
                HotUpdateHelper.PatchResult failureResult = new HotUpdateHelper.PatchResult();
                failureResult.success = false;
                applyResult.postValue(failureResult);
                applyStatus.postValue(message);
            }
            
            @Override
            public void onZipPasswordRequired(File patchFileToDecrypt) {
                isApplying.postValue(false);
                // 通过 LiveData 通知 Fragment 显示密码输入对话框
                applyStatus.postValue("ZIP_PASSWORD_REQUIRED");
            }
            
            @Override
            public void onAesPasswordRequired(File patchFileToDecrypt) {
                isApplying.postValue(false);
                // 通过 LiveData 通知 Fragment 显示密码输入对话框
                applyStatus.postValue("AES_PASSWORD_REQUIRED");
            }
        });
    }
    
    /**
     * 检查安全策略
     * 
     * @param patchFile 补丁文件
     * @return 如果违反安全策略，返回错误信息；否则返回 null
     */
    private SecurityPolicyError checkSecurityPolicy(File patchFile) {
        if (context == null) {
            return null; // 无法检查，允许继续
        }
        
        try {
            // 获取安全策略配置
            SharedPreferences securityPrefs = context.getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE);
            boolean requireSignature = securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
            boolean requireEncryption = securityPrefs.getBoolean(KEY_REQUIRE_ENCRYPTION, false);
            
            Log.d(TAG, "安全策略 - 要求签名: " + requireSignature + ", 要求加密: " + requireEncryption);
            
            // 检查两种加密方式：AES 加密（.enc）或 ZIP 密码加密
            boolean isAesEncrypted = patchFile.getName().endsWith(".enc");
            boolean isZipPasswordEncrypted = false;
            
            // 检查 ZIP 密码加密
            if (!isAesEncrypted) {
                try {
                    ZipPasswordManager zipPasswordManager = new ZipPasswordManager(context);
                    isZipPasswordEncrypted = zipPasswordManager.isEncrypted(patchFile);
                } catch (Exception e) {
                    Log.d(TAG, "检查 ZIP 密码加密失败: " + e.getMessage());
                }
            }
            
            boolean isEncrypted = isAesEncrypted || isZipPasswordEncrypted;
            
            Log.d(TAG, "补丁状态 - AES加密: " + isAesEncrypted + ", ZIP密码加密: " + isZipPasswordEncrypted + ", 已加密: " + isEncrypted);
            
            // 对于加密文件，签名检查将在 HotUpdateHelper 解密后进行
            if (!isEncrypted) {
                // 只对未加密文件检查签名
                boolean hasSignature = checkHasApkSignature(patchFile);
                Log.d(TAG, "补丁状态 - 有签名: " + hasSignature);
                
                // 检查安全策略：签名要求
                if (requireSignature && !hasSignature) {
                    return new SecurityPolicyError(
                        SecurityPolicyError.Type.SIGNATURE_REQUIRED,
                        "当前安全策略要求补丁必须包含 APK 签名！\n\n" +
                        "此补丁未包含 APK 签名，拒绝应用。\n\n" +
                        "补丁文件: " + patchFile.getName() + "\n\n" +
                        "解决方法：\n" +
                        "1. 重新生成补丁，并选择「APK 签名验证」选项\n" +
                        "2. 或在安全设置中关闭签名验证要求"
                    );
                }
            } else {
                // 对于加密文件，签名验证将在 HotUpdateHelper 中解密后进行
                Log.d(TAG, "补丁已加密（" + (isAesEncrypted ? "AES" : "ZIP密码") + "），签名验证将在解密后进行");
            }
            
            // 检查安全策略：加密要求
            if (requireEncryption && !isEncrypted) {
                return new SecurityPolicyError(
                    SecurityPolicyError.Type.ENCRYPTION_REQUIRED,
                    "当前安全策略要求补丁必须加密！\n\n" +
                    "此补丁未加密，拒绝应用。\n\n" +
                    "支持的加密方式：\n" +
                    "1. AES 加密（.enc 文件）\n" +
                    "2. ZIP 密码加密\n\n" +
                    "请使用已加密的补丁，或在设置中关闭加密验证要求。"
                );
            }
            
            // 安全策略检查通过
            Log.d(TAG, "✅ 安全策略检查通过");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "安全策略检查失败", e);
            // 检查失败，允许继续（向后兼容）
            return null;
        }
    }
    
    /**
     * 检查补丁是否有 APK 签名
     */
    private boolean checkHasApkSignature(File patchFile) {
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
    
    public void clearPatch() {
        if (hotUpdateHelper != null) {
            hotUpdateHelper.clearPatch();
            isPatchApplied.setValue(false);
            applyStatus.setValue("补丁已清除");
        }
    }
    
    // Getters for LiveData
    public LiveData<Integer> getApplyProgress() {
        return applyProgress;
    }
    
    public LiveData<String> getApplyStatus() {
        return applyStatus;
    }
    
    public LiveData<HotUpdateHelper.PatchResult> getApplyResult() {
        return applyResult;
    }
    
    public LiveData<Boolean> getIsApplying() {
        return isApplying;
    }
    
    public LiveData<Boolean> getIsPatchApplied() {
        return isPatchApplied;
    }
    
    public LiveData<SecurityPolicyError> getSecurityPolicyError() {
        return securityPolicyError;
    }
    
    /**
     * 安全策略错误信息
     */
    public static class SecurityPolicyError {
        public enum Type {
            SIGNATURE_REQUIRED,
            ENCRYPTION_REQUIRED
        }
        
        public final Type type;
        public final String message;
        
        public SecurityPolicyError(Type type, String message) {
            this.type = type;
            this.message = message;
        }
    }
}
