package com.orange.update;

import android.content.Context;
import android.util.Log;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 补丁签名和验证工具
 * 
 * 使用 apksig 库对生成的补丁 ZIP 文件进行签名和验证
 * 补丁 ZIP 文件会被当作 APK 文件进行签名，这样可以：
 * 1. 验证补丁的完整性
 * 2. 验证补丁的来源
 * 3. 防止补丁被篡改
 * 
 * 使用场景：
 * - 补丁生成时：对补丁进行签名
 * - 补丁应用前：验证补丁签名
 */
public class PatchSigner {
    
    private static final String TAG = "PatchSigner";
    
    private final Context context;
    private String lastError;
    
    public PatchSigner(Context context) {
        this.context = context;
    }
    
    /**
     * 对补丁文件进行签名（使用应用自己的签名）
     * 
     * @param patchFile 补丁文件（ZIP 格式）
     * @return 签名后的文件，失败返回 null
     */
    public File signPatchWithAppSignature(File patchFile) {
        try {
            Log.i(TAG, "使用应用签名对补丁进行签名");
            Log.i(TAG, "补丁文件: " + patchFile.getName());
            Log.i(TAG, "补丁大小: " + formatSize(patchFile.length()));
            
            // 获取应用的签名信息
            android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 
                    android.content.pm.PackageManager.GET_SIGNATURES);
            
            if (packageInfo.signatures == null || packageInfo.signatures.length == 0) {
                lastError = "无法获取应用签名";
                Log.e(TAG, lastError);
                return null;
            }
            
            // 获取应用的签名证书
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(
                packageInfo.signatures[0].toByteArray());
            java.security.cert.CertificateFactory cf = 
                java.security.cert.CertificateFactory.getInstance("X.509");
            X509Certificate appCert = (X509Certificate) cf.generateCertificate(bis);
            
            Log.i(TAG, "✓ 应用签名证书:");
            Log.i(TAG, "  签名者: " + appCert.getSubjectDN().getName());
            Log.i(TAG, "  算法: " + appCert.getPublicKey().getAlgorithm());
            
            // 注意：这里我们只能验证，不能签名，因为我们没有私钥
            // 所以这个方法实际上是告诉用户需要使用相同的签名
            
            lastError = "应用签名验证功能需要使用与应用相同的签名密钥。\n" +
                       "请使用应用的签名密钥库（keystore）对补丁进行签名。";
            Log.w(TAG, lastError);
            
            return null;
            
        } catch (Exception e) {
            lastError = "获取应用签名失败: " + e.getMessage();
            Log.e(TAG, lastError, e);
            return null;
        }
    }
    
    /**
     * 对补丁文件进行签名
     * 
     * @param patchFile 补丁文件（ZIP 格式）
     * @param keystoreFile Keystore 文件
     * @param keystorePassword Keystore 密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @return 签名后的文件，失败返回 null
     */
    public File signPatch(File patchFile, File keystoreFile, 
                         String keystorePassword, String keyAlias, String keyPassword) {
        try {
            Log.i(TAG, "开始签名补丁: " + patchFile.getName());
            Log.i(TAG, "补丁大小: " + formatSize(patchFile.length()));
            
            // 使用 loadKeyStore 方法加载密钥库
            KeyStore keyStore = loadKeyStore(keystoreFile, keystorePassword);
            
            // 检查是否需要使用 ZipSigner（JKS 文件且标准加载失败）
            if (keyStore == null && "JKS_NEEDS_ZIPSIGNER".equals(lastError)) {
                Log.i(TAG, "使用 ZipSigner 对 JKS 进行签名");
                
                // 创建签名后的文件
                File signedPatchFile = new File(patchFile.getParent(), 
                    patchFile.getName().replace(".zip", "_signed.zip"));
                
                // 如果已存在，先删除
                if (signedPatchFile.exists()) {
                    signedPatchFile.delete();
                }
                
                // 使用 ZipSigner 签名
                boolean success = ZipSignerHelper.signZipWithJks(
                    patchFile, signedPatchFile,
                    keystoreFile, keystorePassword,
                    keyAlias, keyPassword
                );
                
                if (!success) {
                    lastError = "ZipSigner 签名失败";
                    Log.e(TAG, lastError);
                    return null;
                }
                
                Log.i(TAG, "✓ 补丁签名成功 (via ZipSigner)");
                Log.i(TAG, "  签名后大小: " + formatSize(signedPatchFile.length()));
                Log.i(TAG, "  输出文件: " + signedPatchFile.getName());
                
                // 删除原始文件，重命名签名后的文件
                patchFile.delete();
                File finalPatchFile = new File(patchFile.getParent(), patchFile.getName());
                signedPatchFile.renameTo(finalPatchFile);
                
                return finalPatchFile;
            }
            
            if (keyStore == null) {
                return null;
            }
            
            // 从 KeyStore 获取私钥和证书链
            PrivateKey privateKey = null;
            List<X509Certificate> certs = null;
            
            try {
                // 获取私钥
                privateKey = (PrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
                if (privateKey == null) {
                    lastError = "无法获取私钥，别名: " + keyAlias;
                    Log.e(TAG, lastError);
                    return null;
                }
                
                Log.i(TAG, "✓ 私钥获取成功");
                Log.i(TAG, "  算法: " + privateKey.getAlgorithm());
                Log.i(TAG, "  格式: " + privateKey.getFormat());
                
                // 获取证书链
                java.security.cert.Certificate[] certChain = keyStore.getCertificateChain(keyAlias);
                if (certChain == null || certChain.length == 0) {
                    lastError = "无法获取证书链，别名: " + keyAlias;
                    Log.e(TAG, lastError);
                    return null;
                }
                
                // 转换为 X509Certificate 列表
                certs = new ArrayList<>();
                for (java.security.cert.Certificate cert : certChain) {
                    if (cert instanceof X509Certificate) {
                        certs.add((X509Certificate) cert);
                    }
                }
                
                Log.i(TAG, "✓ 证书链获取成功");
                Log.i(TAG, "  证书数量: " + certs.size());
                
            } catch (Exception e) {
                Log.e(TAG, "加载密钥失败: " + e.getMessage(), e);
                lastError = "加载密钥失败: " + e.getMessage();
                return null;
            }
            
            if (privateKey == null || certs == null || certs.isEmpty()) {
                lastError = "无法加载密钥和证书";
                Log.e(TAG, lastError);
                return null;
            }
            
            // 创建签名后的文件
            File signedPatchFile = new File(patchFile.getParent(), 
                patchFile.getName().replace(".zip", "_signed.zip"));
            
            // 如果已存在，先删除
            if (signedPatchFile.exists()) {
                signedPatchFile.delete();
            }
            
            // 创建签名配置
            ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                keyAlias, privateKey, certs).build();
            
            // 配置签名器
            ApkSigner.Builder signerBuilder = new ApkSigner.Builder(
                Collections.singletonList(signerConfig));
            
            signerBuilder.setInputApk(patchFile);
            signerBuilder.setOutputApk(signedPatchFile);
            signerBuilder.setMinSdkVersion(21);  // Android 5.0+
            
            // 启用签名方案
            signerBuilder.setV1SigningEnabled(true);   // JAR signing (必须)
            signerBuilder.setV2SigningEnabled(true);   // v2 (Android 7.0+)
            signerBuilder.setV3SigningEnabled(false);  // v3 对 ZIP 文件可能不适用
            signerBuilder.setV4SigningEnabled(false);  // v4 不需要
            
            // 执行签名
            Log.i(TAG, "正在签名...");
            ApkSigner signer = signerBuilder.build();
            signer.sign();
            
            Log.i(TAG, "✓ 补丁签名成功");
            Log.i(TAG, "  签名后大小: " + formatSize(signedPatchFile.length()));
            Log.i(TAG, "  输出文件: " + signedPatchFile.getName());
            
            // 删除原始文件，重命名签名后的文件
            patchFile.delete();
            File finalPatchFile = new File(patchFile.getParent(), patchFile.getName());
            signedPatchFile.renameTo(finalPatchFile);
            
            return finalPatchFile;
            
        } catch (Exception e) {
            lastError = "签名失败: " + e.getMessage();
            Log.e(TAG, lastError, e);
            return null;
        }
    }
    
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
     * 验证补丁签名（使用 ApkVerifier - 已废弃，仅用于 APK）
     * 
     * @param patchFile 补丁文件
     * @return 签名有效返回 true，否则返回 false
     */
    @Deprecated
    public boolean verifyPatchSignatureWithApkVerifier(File patchFile) {
        try {
            Log.i(TAG, "验证补丁签名: " + patchFile.getName());
            
            // 使用 ApkVerifier 验证签名
            ApkVerifier.Builder verifierBuilder = new ApkVerifier.Builder(patchFile);
            verifierBuilder.setMinCheckedPlatformVersion(21);  // Android 5.0+
            
            ApkVerifier verifier = verifierBuilder.build();
            ApkVerifier.Result result = verifier.verify();
            
            // 检查验证结果
            if (result.isVerified()) {
                Log.i(TAG, "✓ 补丁签名验证成功");
                Log.i(TAG, "  V1 签名: " + result.isVerifiedUsingV1Scheme());
                Log.i(TAG, "  V2 签名: " + result.isVerifiedUsingV2Scheme());
                Log.i(TAG, "  V3 签名: " + result.isVerifiedUsingV3Scheme());
                
                // 打印签名者信息
                if (!result.getSignerCertificates().isEmpty()) {
                    X509Certificate cert = result.getSignerCertificates().get(0);
                    Log.i(TAG, "  签名者: " + cert.getSubjectDN().getName());
                }
                
                return true;
            } else {
                // 收集错误信息
                StringBuilder errors = new StringBuilder();
                
                if (result.containsErrors()) {
                    for (ApkVerifier.IssueWithParams error : result.getErrors()) {
                        errors.append(error.toString()).append("; ");
                    }
                }
                
                if (result.getV1SchemeSigners() != null) {
                    for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeSigners()) {
                        if (signer.containsErrors()) {
                            for (ApkVerifier.IssueWithParams error : signer.getErrors()) {
                                errors.append("V1: ").append(error.toString()).append("; ");
                            }
                        }
                    }
                }
                
                if (result.getV2SchemeSigners() != null) {
                    for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
                        if (signer.containsErrors()) {
                            for (ApkVerifier.IssueWithParams error : signer.getErrors()) {
                                errors.append("V2: ").append(error.toString()).append("; ");
                            }
                        }
                    }
                }
                
                lastError = "签名验证失败: " + errors.toString();
                Log.e(TAG, lastError);
                return false;
            }
            
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
     * 加载 keystore
     * 
     * 支持多种格式：
     * 1. JKS - 使用 ZipSigner 原生支持（优先）
     * 2. BKS - 使用 BouncyCastle
     * 3. PKCS12 - 使用标准 Java 或 BouncyCastle
     * 
     * @param keystoreFile Keystore 文件
     * @param password 密码
     * @return KeyStore 对象，失败返回 null
     */
    private KeyStore loadKeyStore(File keystoreFile, String password) {
        try {
            KeyStore keyStore = null;
            
            Log.i(TAG, "加载 Keystore: " + keystoreFile.getName());
            Log.i(TAG, "文件大小: " + formatSize(keystoreFile.length()));
            
            String fileName = keystoreFile.getName().toLowerCase(java.util.Locale.ROOT);
            
            // 方法1: 如果是 JKS 文件，优先尝试使用 ZipSigner（原生 JKS 支持）
            if (fileName.endsWith(".jks") && ZipSignerHelper.isAvailable()) {
                try {
                    Log.d(TAG, "检测到 JKS 文件，尝试使用 ZipSigner...");
                    
                    // 注意：ZipSigner 需要 keyAlias 和 keyPassword
                    // 这里我们先尝试加载 KeyStore，稍后在 signPatch 中使用完整参数
                    
                    // 尝试使用标准 JKS 加载（可能失败）
                    try {
                        keyStore = KeyStore.getInstance("JKS");
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(keystoreFile)) {
                            keyStore.load(fis, password.toCharArray());
                        }
                        Log.i(TAG, "✓ Keystore 加载成功 (标准 JKS)");
                        return keyStore;
                    } catch (Exception e) {
                        Log.d(TAG, "标准 JKS 加载失败，将在签名时使用 ZipSigner: " + e.getMessage());
                        // 返回 null，让 signPatch 方法使用 ZipSigner
                        lastError = "JKS_NEEDS_ZIPSIGNER";
                        return null;
                    }
                    
                } catch (Exception e) {
                    Log.d(TAG, "ZipSigner 检查失败: " + e.getMessage());
                }
            }
            
            // 方法2: 尝试使用 BouncyCastle 的 JKS/PKCS12 提供者（最可靠）
            try {
                Log.d(TAG, "尝试使用 BouncyCastle 提供者...");
                
                // 注册 BouncyCastle 提供者（插入到第一位，优先级最高）
                java.security.Provider bcProvider = new org.bouncycastle.jce.provider.BouncyCastleProvider();
                java.security.Security.insertProviderAt(bcProvider, 1);
                
                Log.d(TAG, "BouncyCastle 提供者已注册: " + bcProvider.getName());
                
                // 首先尝试 BKS（BouncyCastle KeyStore - 最兼容 Android）
                String[] keystoreTypes = {"BKS", "PKCS12", "BCPKCS12", "PKCS12-DEF"};
                for (String type : keystoreTypes) {
                    try {
                        Log.d(TAG, "尝试 BouncyCastle KeyStore 类型: " + type);
                        keyStore = KeyStore.getInstance(type, "BC");
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(keystoreFile)) {
                            keyStore.load(fis, password.toCharArray());
                        }
                        Log.i(TAG, "✓ Keystore 加载成功 (类型: " + type + " via BouncyCastle)");
                        
                        // 列出所有别名
                        java.util.Enumeration<String> aliases = keyStore.aliases();
                        Log.d(TAG, "Keystore 中的别名:");
                        while (aliases.hasMoreElements()) {
                            String alias = aliases.nextElement();
                            Log.d(TAG, "  - " + alias + " (isKey: " + keyStore.isKeyEntry(alias) + ")");
                        }
                        
                        return keyStore;
                    } catch (Exception e) {
                        Log.d(TAG, "类型 " + type + " 失败: " + e.getMessage());
                        if (e.getCause() != null) {
                            Log.d(TAG, "  原因: " + e.getCause().getMessage());
                        }
                    }
                }
                
                throw new Exception("BouncyCastle 无法加载此 KeyStore");
                
            } catch (Exception e) {
                Log.d(TAG, "BouncyCastle 加载失败: " + e.getMessage());
            }
            
            // 方法3: 尝试标准 PKCS12 格式
            try {
                Log.d(TAG, "尝试 PKCS12...");
                keyStore = KeyStore.getInstance("PKCS12");
                try (java.io.FileInputStream fis = new java.io.FileInputStream(keystoreFile)) {
                    keyStore.load(fis, password.toCharArray());
                }
                Log.i(TAG, "✓ Keystore 加载成功 (PKCS12)");
                return keyStore;
            } catch (Exception e) {
                // 所有方法都失败了
                if (fileName.endsWith(".jks")) {
                    lastError = "JKS 格式需要使用 ZipSigner 或转换为 BKS 格式：\n\n" +
                               "方法1 - 转换为 BKS（推荐）：\n" +
                               "keytool -importkeystore \\\n" +
                               "  -srckeystore " + keystoreFile.getName() + " \\\n" +
                               "  -destkeystore " + keystoreFile.getName().replace(".jks", ".bks") + " \\\n" +
                               "  -srcstoretype JKS \\\n" +
                               "  -deststoretype BKS \\\n" +
                               "  -provider org.bouncycastle.jce.provider.BouncyCastleProvider\n\n" +
                               "方法2 - 使用 ZipSigner 库（已集成）";
                } else {
                    lastError = "Keystore 加载失败 (尝试了所有方法): " + e.getMessage();
                }
                Log.e(TAG, lastError);
                return null;
            }
            
        } catch (Exception e) {
            lastError = "Keystore 加载失败: " + e.getMessage();
            Log.e(TAG, lastError, e);
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
