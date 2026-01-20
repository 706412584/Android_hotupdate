package com.orange.update;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 补丁签名和验证工具
 * 
 * 用于验证补丁 ZIP 文件的签名
 * 补丁 ZIP 文件使用 JAR 签名（v1签名方案），可以：
 * 1. 验证补丁的完整性
 * 2. 验证补丁的来源
 * 3. 防止补丁被篡改
 * 
 * 使用场景：
 * - 补丁应用前：验证补丁签名
 * 
 * 注意：补丁签名功能在 patch-generator-android 模块中实现
 */
public class PatchSigner {
    
    private static final String TAG = "PatchSigner";
    
    private final Context context;
    private String lastError;
    
    public PatchSigner(Context context) {
        this.context = context;
    }
    
    // 注意：补丁签名功能已移至 patch-generator-android 模块
    // update 模块只负责验证签名，不负责生成签名
    
    /**
     * 验证补丁签名（使用 JAR 签名验证）
     * 
     * 注意：补丁是 ZIP 文件，不是 APK，所以不能使用 ApkVerifier
     * 我们使用 JAR 签名验证（v1 签名方案）
     * 
     * @param patchFile 补丁文件
     * @return 签名有效返回 true，否则返回 false
     */
    public boolean verifyPatchSignature(File patchFile) {
        try {
            Log.i(TAG, "验证补丁签名: " + patchFile.getName());
            
            // 使用 JarFile 验证 JAR 签名（v1 签名方案）
            java.util.jar.JarFile jarFile = new java.util.jar.JarFile(patchFile, true);
            
            // 读取所有条目以触发签名验证
            java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
            byte[] buffer = new byte[8192];
            boolean hasSigned = false;
            
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                
                // 跳过目录和签名文件
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                
                // 读取条目内容以触发签名验证
                java.io.InputStream is = jarFile.getInputStream(entry);
                while (is.read(buffer) != -1) {
                    // 只是读取，触发验证
                }
                is.close();
                
                // 检查签名
                java.security.cert.Certificate[] certs = entry.getCertificates();
                if (certs != null && certs.length > 0) {
                    hasSigned = true;
                    Log.d(TAG, "  条目已签名: " + entry.getName());
                } else {
                    Log.w(TAG, "  条目未签名: " + entry.getName());
                    jarFile.close();
                    lastError = "补丁包含未签名的条目: " + entry.getName();
                    return false;
                }
            }
            
            jarFile.close();
            
            if (!hasSigned) {
                lastError = "补丁未签名";
                Log.e(TAG, lastError);
                return false;
            }
            
            Log.i(TAG, "✓ 补丁签名验证成功（JAR 签名）");
            return true;
            
        } catch (Exception e) {
            lastError = "验证失败: " + e.getMessage();
            Log.e(TAG, lastError, e);
            return false;
        }
    }
    

    
    /**
     * 验证补丁签名是否与应用签名匹配
     * 
     * @param patchFile 补丁文件
     * @return 签名匹配返回 true，否则返回 false
     */
    public boolean verifyPatchSignatureMatchesApp(File patchFile) {
        try {
            Log.i(TAG, "验证补丁签名是否与应用签名匹配");
            
            // 1. 验证补丁签名
            if (!verifyPatchSignature(patchFile)) {
                return false;
            }
            
            // 2. 从 JAR 签名中获取补丁的签名证书
            X509Certificate patchCert = extractCertificateFromJar(patchFile);
            if (patchCert == null) {
                lastError = "无法从补丁中提取签名证书";
                Log.e(TAG, lastError);
                return false;
            }
            
            Log.d(TAG, "补丁签名证书:");
            Log.d(TAG, "  签名者: " + patchCert.getSubjectDN().getName());
            Log.d(TAG, "  算法: " + patchCert.getPublicKey().getAlgorithm());
            
            // 3. 获取应用的签名证书
            android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 
                    android.content.pm.PackageManager.GET_SIGNATURES);
            
            if (packageInfo.signatures == null || packageInfo.signatures.length == 0) {
                lastError = "无法获取应用签名";
                Log.e(TAG, lastError);
                return false;
            }
            
            // 4. 比较签名
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(
                packageInfo.signatures[0].toByteArray());
            java.security.cert.CertificateFactory cf = 
                java.security.cert.CertificateFactory.getInstance("X.509");
            X509Certificate appCert = (X509Certificate) cf.generateCertificate(bis);
            
            Log.d(TAG, "应用签名证书:");
            Log.d(TAG, "  签名者: " + appCert.getSubjectDN().getName());
            Log.d(TAG, "  算法: " + appCert.getPublicKey().getAlgorithm());
            
            // 比较证书的公钥
            boolean matches = java.util.Arrays.equals(
                patchCert.getPublicKey().getEncoded(),
                appCert.getPublicKey().getEncoded()
            );
            
            if (matches) {
                Log.i(TAG, "✓ 补丁签名与应用签名匹配");
                return true;
            } else {
                lastError = "补丁签名与应用签名不匹配";
                Log.e(TAG, lastError);
                Log.e(TAG, "  补丁签名者: " + patchCert.getSubjectDN().getName());
                Log.e(TAG, "  应用签名者: " + appCert.getSubjectDN().getName());
                return false;
            }
            
        } catch (Exception e) {
            lastError = "签名匹配验证失败: " + e.getMessage();
            Log.e(TAG, lastError, e);
            return false;
        }
    }
    
    /**
     * 从 JAR 文件中提取签名证书
     * 
     * @param jarFile JAR 文件
     * @return X509 证书，失败返回 null
     */
    private X509Certificate extractCertificateFromJar(File jarFile) {
        try {
            java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile, true);
            
            // 读取所有条目以触发签名验证
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            byte[] buffer = new byte[8192];
            X509Certificate cert = null;
            
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                
                // 跳过目录和签名文件
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                
                // 读取条目内容以触发签名验证
                java.io.InputStream is = jar.getInputStream(entry);
                while (is.read(buffer) != -1) {
                    // 只是读取，触发验证
                }
                is.close();
                
                // 获取证书
                java.security.cert.Certificate[] certs = entry.getCertificates();
                if (certs != null && certs.length > 0) {
                    if (certs[0] instanceof X509Certificate) {
                        cert = (X509Certificate) certs[0];
                        break; // 找到第一个证书就够了
                    }
                }
            }
            
            jar.close();
            return cert;
            
        } catch (Exception e) {
            Log.e(TAG, "提取证书失败: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 获取错误信息
     * 
     * @return 错误信息
     */
    public String getError() {
        return lastError;
    }
    
    /**
     * 格式化文件大小
     */
    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }
}
