package com.orange.update.viewmodel;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.orange.patchgen.android.AndroidPatchGenerator;
import com.orange.patchgen.android.SimpleAndroidGeneratorCallback;
import com.orange.patchgen.model.PatchResult;

import java.io.File;

/**
 * 补丁生成 ViewModel
 * 管理补丁生成的业务逻辑和状态
 */
public class PatchGenerateViewModel extends ViewModel {
    
    private final MutableLiveData<Integer> generationProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> generationStatus = new MutableLiveData<>("");
    private final MutableLiveData<PatchResult> generationResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGenerating = new MutableLiveData<>(false);
    
    private File baseApk;
    private File newApk;
    private AndroidPatchGenerator generator;
    
    public void setBaseApk(File apk) {
        this.baseApk = apk;
    }
    
    public void setNewApk(File apk) {
        this.newApk = apk;
    }
    
    public File getBaseApk() {
        return baseApk;
    }
    
    public File getNewApk() {
        return newApk;
    }
    
    public boolean canGenerate() {
        return baseApk != null && newApk != null && !Boolean.TRUE.equals(isGenerating.getValue());
    }
    
    public void generatePatch(Context context, File outputFile) {
        generatePatchWithOptions(context, outputFile, false, false, false, null, null);
    }
    
    /**
     * 生成补丁（带安全选项）
     * @param context 上下文
     * @param outputFile 输出文件
     * @param withApkSignature 是否添加 APK 签名
     * @param withZipPassword 是否添加 ZIP 密码
     * @param withEncryption 是否加密
     * @param zipPassword ZIP 密码（可选）
     * @param aesPassword AES 加密密码（可选）
     */
    public void generatePatchWithOptions(Context context, File outputFile, 
                                        boolean withApkSignature, boolean withZipPassword, 
                                        boolean withEncryption, String zipPassword, String aesPassword) {
        if (!canGenerate()) {
            generationResult.setValue(PatchResult.failure(-1, "请先选择两个 APK 文件"));
            return;
        }
        
        isGenerating.setValue(true);
        generationProgress.setValue(0);
        
        // 构建状态消息
        String status = "正在生成补丁...";
        if (withApkSignature && withZipPassword && withEncryption) {
            status = "正在生成、APK签名、ZIP密码并加密补丁...";
        } else if (withApkSignature && withZipPassword) {
            status = "正在生成、APK签名并添加ZIP密码...";
        } else if (withApkSignature && withEncryption) {
            status = "正在生成、APK签名并加密补丁...";
        } else if (withZipPassword && withEncryption) {
            status = "正在生成、ZIP密码并加密补丁...";
        } else if (withApkSignature) {
            status = "正在生成并添加APK签名...";
        } else if (withZipPassword) {
            status = "正在生成并添加ZIP密码...";
        } else if (withEncryption) {
            status = "正在生成并加密补丁...";
        }
        generationStatus.setValue(status);
        
        // 配置签名（如果需要）
        com.orange.patchgen.config.SigningConfig.Builder signingBuilder = null;
        if (withApkSignature) {
            // 从 SharedPreferences 加载 JKS 配置
            android.content.SharedPreferences jksPrefs = context.getSharedPreferences("jks_config", android.content.Context.MODE_PRIVATE);
            String jksFilePath = jksPrefs.getString("jks_file_path", null);
            String keystorePassword = jksPrefs.getString("keystore_password", null);
            String keyAlias = jksPrefs.getString("key_alias", null);
            String keyPassword = jksPrefs.getString("key_password", null);
            
            // 验证配置完整性
            if (jksFilePath != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
                File keystoreFile = new File(jksFilePath);
                
                if (keystoreFile.exists()) {
                    signingBuilder = new com.orange.patchgen.config.SigningConfig.Builder()
                            .keystoreFile(keystoreFile)
                            .keystorePassword(keystorePassword)
                            .keyAlias(keyAlias)
                            .keyPassword(keyPassword);
                    android.util.Log.d("PatchGenerateVM", "✓ 配置签名: " + keystoreFile.getAbsolutePath());
                    android.util.Log.d("PatchGenerateVM", "  密钥别名: " + keyAlias);
                } else {
                    android.util.Log.w("PatchGenerateVM", "⚠️ Keystore文件不存在: " + jksFilePath);
                    generationResult.setValue(com.orange.patchgen.model.PatchResult.failure(-1, 
                        "签名文件不存在，请在「系统信息」中配置 JKS 签名"));
                    isGenerating.setValue(false);
                    return;
                }
            } else {
                android.util.Log.w("PatchGenerateVM", "⚠️ JKS 配置不完整，请先在「系统信息」中配置签名");
                generationResult.setValue(com.orange.patchgen.model.PatchResult.failure(-1, 
                    "未配置 JKS 签名，请在「系统信息」→「安全策略设置」中配置"));
                isGenerating.setValue(false);
                return;
            }
        }
        
        generator = new AndroidPatchGenerator.Builder(context)
            .baseApk(baseApk)
            .newApk(newApk)
            .output(outputFile)
            .signingConfig(signingBuilder != null ? signingBuilder.build() : null)
            .callback(new SimpleAndroidGeneratorCallback() {
                @Override
                public void onProgress(int percent, String stage) {
                    generationProgress.postValue(percent);
                    generationStatus.postValue(stage + " (" + percent + "%)");
                }
                
                @Override
                public void onComplete(PatchResult result) {
                    if (result.isSuccess() && result.getPatchFile() != null && result.getPatchFile().exists()) {
                        // 检查补丁文件大小，如果为 0KB 或非常小（< 1KB），说明两个 APK 无差异
                        long patchSize = result.getPatchFile().length();
                        if (patchSize < 1024) {  // 小于 1KB
                            android.util.Log.w("PatchGenerateVM", "⚠️ 补丁文件过小 (" + patchSize + " bytes)，两个 APK 可能无差异");
                            isGenerating.postValue(false);
                            generationResult.postValue(PatchResult.failure(-1, 
                                "⚠️ 两个 APK 无差异，无需生成补丁\n\n" +
                                "补丁大小: " + patchSize + " bytes\n" +
                                "请确保选择了不同版本的 APK 文件"));
                            // 删除无效的补丁文件
                            if (result.getPatchFile().exists()) {
                                result.getPatchFile().delete();
                            }
                            return;
                        }
                        
                        // 处理 ZIP 密码和 AES 加密
                        if (withZipPassword || withEncryption) {
                            processSecurityOptions(context, result, withZipPassword, withEncryption, 
                                                 zipPassword, aesPassword);
                        } else {
                            isGenerating.postValue(false);
                            generationResult.postValue(result);
                        }
                    } else {
                        isGenerating.postValue(false);
                        generationResult.postValue(result);
                    }
                }
                
                @Override
                public void onError(int errorCode, String message) {
                    isGenerating.postValue(false);
                    generationResult.postValue(PatchResult.failure(errorCode, message));
                }
            })
            .build();
        
        generator.generateInBackground();
    }
    
    /**
     * 处理安全选项（ZIP密码和AES加密）
     */
    private void processSecurityOptions(Context context, PatchResult result, 
                                       boolean withZipPassword, boolean withEncryption,
                                       String zipPassword, String aesPassword) {
        new Thread(() -> {
            try {
                File patchFile = result.getPatchFile();
                File currentFile = patchFile;
                
                android.util.Log.d("PatchGenerateVM", "开始处理安全选项");
                android.util.Log.d("PatchGenerateVM", "补丁文件: " + patchFile.getAbsolutePath());
                
                // 1. ZIP 密码保护
                if (withZipPassword) {
                    generationStatus.postValue("正在添加 ZIP 密码保护...");
                    
                    com.orange.update.ZipPasswordManager zipPasswordManager = 
                        new com.orange.update.ZipPasswordManager(context);
                    
                    // 获取 ZIP 密码
                    String finalZipPassword;
                    boolean isCustomPassword = false;
                    if (zipPassword != null && !zipPassword.isEmpty()) {
                        finalZipPassword = zipPassword;
                        isCustomPassword = true;
                        android.util.Log.d("PatchGenerateVM", "使用用户自定义 ZIP 密码");
                    } else {
                        finalZipPassword = zipPasswordManager.getZipPassword();
                        android.util.Log.d("PatchGenerateVM", "使用从应用签名派生的 ZIP 密码");
                    }
                    
                    // 创建加密后的 ZIP 文件
                    File encryptedZipFile = new File(currentFile.getParent(), 
                        currentFile.getName().replace(".zip", "_zippwd.zip"));
                    
                    boolean success = zipPasswordManager.encryptZip(currentFile, encryptedZipFile, finalZipPassword);
                    
                    if (success) {
                        currentFile.delete();
                        File finalZipFile = new File(currentFile.getParent(), patchFile.getName());
                        encryptedZipFile.renameTo(finalZipFile);
                        currentFile = finalZipFile;
                        
                        // 如果使用自定义密码，保存密码提示文件
                        if (isCustomPassword) {
                            File zipPasswordFile = new File(currentFile.getPath() + ".zippwd");
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(zipPasswordFile);
                            fos.write(("ZIP 密码提示: 使用自定义密码\n" + 
                                      "注意: 应用补丁时需要输入相同密码\n" +
                                      "密码长度: " + finalZipPassword.length() + " 字符").getBytes("UTF-8"));
                            fos.close();
                            android.util.Log.d("PatchGenerateVM", "✓ 已保存 ZIP 密码提示文件");
                        }
                        
                        android.util.Log.d("PatchGenerateVM", "✓ ZIP 密码保护已添加");
                    } else {
                        throw new Exception("ZIP 密码加密失败");
                    }
                }
                
                // 2. AES 加密
                if (withEncryption) {
                    generationStatus.postValue("正在加密补丁...");
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        com.orange.update.SecurityManager securityManager = 
                            new com.orange.update.SecurityManager(context);
                        
                        File encryptedFile;
                        File previousFile = currentFile;
                        
                        if (aesPassword != null && !aesPassword.isEmpty()) {
                            android.util.Log.d("PatchGenerateVM", "使用自定义密码加密补丁");
                            encryptedFile = securityManager.encryptPatchWithPassword(currentFile, aesPassword);
                            
                            // 保存密码提示信息
                            File passwordFile = new File(encryptedFile.getPath() + ".pwd");
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(passwordFile);
                            fos.write(("密码提示: 使用自定义密码\n" + 
                                      "注意: 客户端需要相同密码才能解密\n" +
                                      "密码长度: " + aesPassword.length() + " 字符").getBytes("UTF-8"));
                            fos.close();
                        } else {
                            android.util.Log.d("PatchGenerateVM", "使用默认密钥加密补丁");
                            encryptedFile = securityManager.encryptPatch(currentFile);
                        }
                        
                        // 删除上一步的文件
                        if (previousFile != null && previousFile.exists()) {
                            String previousPath = previousFile.getAbsolutePath();
                            String encryptedPath = encryptedFile.getAbsolutePath();
                            
                            if (!previousPath.equals(encryptedPath)) {
                                previousFile.delete();
                                android.util.Log.d("PatchGenerateVM", "删除上一步文件: " + previousFile.getName());
                            }
                        }
                        
                        currentFile = encryptedFile;
                        android.util.Log.d("PatchGenerateVM", "✓ AES 加密完成");
                    }
                }
                
                // 3. 更新结果 - 使用 setter 方法更新 patchFile
                File finalFile = currentFile;
                result.setPatchFile(finalFile);
                result.setPatchSize(finalFile.length());
                
                isGenerating.postValue(false);
                generationResult.postValue(result);
                
                android.util.Log.i("PatchGenerateVM", "✓ 安全选项处理完成");
                android.util.Log.i("PatchGenerateVM", "  最终文件: " + finalFile.getAbsolutePath());
                
            } catch (Exception e) {
                android.util.Log.e("PatchGenerateVM", "处理安全选项失败", e);
                isGenerating.postValue(false);
                generationResult.postValue(PatchResult.failure(-1, "处理安全选项失败: " + e.getMessage()));
            }
        }).start();
    }
    
    public void cancel() {
        if (generator != null) {
            generator.cancel();
            isGenerating.setValue(false);
        }
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        cancel();
    }
    
    // Getters for LiveData
    public LiveData<Integer> getGenerationProgress() {
        return generationProgress;
    }
    
    public LiveData<String> getGenerationStatus() {
        return generationStatus;
    }
    
    public LiveData<PatchResult> getGenerationResult() {
        return generationResult;
    }
    
    public LiveData<Boolean> getIsGenerating() {
        return isGenerating;
    }
}
