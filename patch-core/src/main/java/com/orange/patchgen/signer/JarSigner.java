package com.orange.patchgen.signer;

import com.orange.patchgen.config.SigningConfig;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * JAR 签名器
 * 
 * 生成完整的 JAR 签名结构（MANIFEST.MF + .SF + .RSA），
 * 使补丁文件可以通过 JarFile.getCertificates() 验证。
 * 
 * 
 *
 * 实现说明：
 * 1. 生成 MANIFEST.MF - 包含所有文件的 SHA-256 摘要
 * 2. 生成 .SF 文件 - 包含 MANIFEST.MF 各部分的摘要
 * 3. 生成 .RSA/.DSA/.EC 文件 - 包含 .SF 的签名和证书链
 */
public class JarSigner {
    
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String DIGEST_MANIFEST_ATTR = "SHA-256-Digest-Manifest";
    private static final String DIGEST_ATTR = "SHA-256-Digest";
    
    // 静态初始化 BouncyCastle Provider，支持 JKS 等格式
    static {
        try {
            Security.addProvider(new BouncyCastleProvider());
            System.out.println("[JarSigner] ✓ BouncyCastle Provider 已注册");
        } catch (Exception e) {
            System.err.println("[JarSigner] ✗ 注册 BouncyCastle Provider 失败: " + e.getMessage());
        }
    }
    
    private final SigningConfig config;
    
    public JarSigner(SigningConfig config) {
        this.config = config;
    }
    
    /**
     * 对 JAR/ZIP 文件签名
     * 
     * @param jarFile 要签名的文件
     * @throws SigningException 签名失败时抛出
     */
    public void sign(File jarFile) throws SigningException {
        if (jarFile == null || !jarFile.exists()) {
            throw new SigningException("JAR file does not exist");
        }
        
        // 优先尝试使用 ZipSigner（支持 JKS 原生签名）
        if (tryZipSigner(jarFile)) {
            return;  // ZipSigner 签名成功，直接返回
        }
        
        // ZipSigner 失败或不可用，使用标准 JAR 签名流程
        try {
            System.out.println("[JarSigner] 开始签名: " + jarFile.getAbsolutePath());
            
            // 1. 加载密钥和证书
            System.out.println("[JarSigner] 步骤 1: 加载密钥库");
            if (config.getKeystoreFile() == null) {
                throw new SigningException("Keystore file is null");
            }
            System.out.println("[JarSigner] 密钥库文件: " + config.getKeystoreFile().getAbsolutePath());
            System.out.println("[JarSigner] 密钥库文件存在: " + config.getKeystoreFile().exists());
            
            if (!config.getKeystoreFile().exists()) {
                throw new SigningException("Keystore file does not exist: " + config.getKeystoreFile().getAbsolutePath());
            }
            
            KeyStore keyStore = loadKeyStore();
            System.out.println("[JarSigner] ✓ 密钥库加载成功");
            
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(
                config.getKeyAlias(),
                config.getKeyPassword().toCharArray()
            );
            Certificate[] certChain = keyStore.getCertificateChain(config.getKeyAlias());
            
            if (privateKey == null) {
                throw new SigningException("Private key not found: " + config.getKeyAlias());
            }
            if (certChain == null || certChain.length == 0) {
                throw new SigningException("Certificate chain not found: " + config.getKeyAlias());
            }
            System.out.println("[JarSigner] ✓ 私钥和证书链加载成功");
            
            // 2. 读取现有 JAR 文件
            System.out.println("[JarSigner] 步骤 2: 读取 JAR 文件内容");
            Map<String, byte[]> entries = readJarEntries(jarFile);
            System.out.println("[JarSigner] ✓ 读取了 " + entries.size() + " 个文件");
            
            // 3. 生成 MANIFEST.MF
            System.out.println("[JarSigner] 步骤 3: 生成 MANIFEST.MF");
            Manifest manifest = generateManifest(entries);
            System.out.println("[JarSigner] ✓ MANIFEST.MF 生成成功");
            
            // 4. 生成 .SF 文件
            System.out.println("[JarSigner] 步骤 4: 生成 .SF 文件");
            byte[] sfBytes = generateSignatureFile(manifest);
            System.out.println("[JarSigner] ✓ .SF 文件生成成功，大小: " + sfBytes.length + " 字节");
            
            // 5. 生成签名块文件（.RSA/.DSA/.EC）
            System.out.println("[JarSigner] 步骤 5: 生成签名块文件");
            byte[] signatureBlock = generateSignatureBlock(sfBytes, privateKey, certChain);
            System.out.println("[JarSigner] ✓ 签名块生成成功，大小: " + signatureBlock.length + " 字节");
            
            // 6. 确定签名文件扩展名
            String signatureExt = getSignatureExtension(privateKey.getAlgorithm());
            System.out.println("[JarSigner] 签名文件扩展名: " + signatureExt);
            
            // 7. 重新打包 JAR 文件
            System.out.println("[JarSigner] 步骤 6: 重新打包 JAR 文件");
            repackJar(jarFile, entries, manifest, sfBytes, signatureBlock, signatureExt);
            System.out.println("[JarSigner] ✓ JAR 签名完成");
            
        } catch (Exception e) {
            System.err.println("[JarSigner] 签名失败: " + e.getMessage());
            e.printStackTrace();
            throw new SigningException("Failed to sign JAR: " + e.getMessage(), e);
        }
    }
    
    /**
     * 尝试使用 ZipSigner 签名（支持 JKS）
     * 
     * @param jarFile 要签名的文件
     * @return 是否成功
     */
    private boolean tryZipSigner(File jarFile) {
        try {
            // 检查是否是 JKS 文件
            if (config.getKeystoreFile() == null) {
                return false;
            }
            
            String keystoreName = config.getKeystoreFile().getName().toLowerCase();
            if (!keystoreName.endsWith(".jks")) {
                System.out.println("[JarSigner] 不是 JKS 文件，跳过 ZipSigner");
                return false;
            }
            
            System.out.println("[JarSigner] 检测到 JKS 文件，尝试使用 ZipSigner 签名");
            
            // 方案：使用 XiaoMozi.签名 类的方式
            // 1. 使用 KeyStoreFileManager.loadKeyStore() 加载 keystore
            // 2. 使用 ZipSigner.setKeys() 设置密钥
            // 3. 调用 ZipSigner.signZip() 签名
            try {
                // 加载 KeyStoreFileManager 类
                Class<?> keyStoreManagerClass = Class.forName("kellinwood.security.zipsigner.optional.KeyStoreFileManager");
                
                // 调用 loadKeyStore(String keystorePath, char[] password)
                java.lang.reflect.Method loadKeyStoreMethod = keyStoreManagerClass.getMethod("loadKeyStore",
                    String.class, char[].class);
                
                java.security.KeyStore keyStore = (java.security.KeyStore) loadKeyStoreMethod.invoke(null,
                    config.getKeystoreFile().getAbsolutePath(),
                    config.getKeystorePassword().toCharArray());
                
                System.out.println("[JarSigner] ✓ KeyStore 加载成功（通过 KeyStoreFileManager）");
                
                // 获取私钥和证书
                java.security.PrivateKey privateKey = (java.security.PrivateKey) keyStore.getKey(
                    config.getKeyAlias(),
                    config.getKeyPassword().toCharArray());
                
                java.security.cert.Certificate certificate = keyStore.getCertificate(config.getKeyAlias());
                
                System.out.println("[JarSigner] ✓ 私钥和证书获取成功");
                
                // 创建 ZipSigner 实例
                Class<?> zipSignerClass = Class.forName("kellinwood.security.zipsigner.ZipSigner");
                Object zipSigner = zipSignerClass.newInstance();
                
                // 调用 setKeys(String keyName, X509Certificate cert, PrivateKey privateKey, String signatureAlgorithm, byte[] publicKeyBytes)
                java.lang.reflect.Method setKeysMethod = zipSignerClass.getMethod("setKeys",
                    String.class,
                    java.security.cert.X509Certificate.class,
                    java.security.PrivateKey.class,
                    String.class,
                    byte[].class);
                
                setKeysMethod.invoke(zipSigner,
                    "XiaoMozi",  // keyName
                    certificate,  // certificate
                    privateKey,  // privateKey
                    "SHA1withRSA",  // signatureAlgorithm
                    null);  // publicKeyBytes
                
                System.out.println("[JarSigner] ✓ ZipSigner.setKeys() 调用成功");
                
                // 创建临时输出文件
                File tempOutput = new File(jarFile.getParent(), jarFile.getName() + ".signed.tmp");
                
                // 调用 signZip(String input, String output)
                java.lang.reflect.Method signZipMethod = zipSignerClass.getMethod("signZip",
                    String.class, String.class);
                
                signZipMethod.invoke(zipSigner,
                    jarFile.getAbsolutePath(),
                    tempOutput.getAbsolutePath());
                
                System.out.println("[JarSigner] ✓ ZipSigner.signZip() 调用成功");
                
                if (tempOutput.exists()) {
                    // 替换原文件
                    jarFile.delete();
                    tempOutput.renameTo(jarFile);
                    System.out.println("[JarSigner] ✓ ZipSigner 签名成功");
                    return true;
                } else {
                    System.out.println("[JarSigner] ZipSigner 签名失败：输出文件不存在");
                    return false;
                }
                
            } catch (ClassNotFoundException e) {
                System.out.println("[JarSigner] ZipSigner 类未找到，跳过");
                return false;
            } catch (java.lang.reflect.InvocationTargetException e) {
                System.err.println("[JarSigner] ZipSigner 签名异常（InvocationTargetException）");
                System.err.println("[JarSigner] 目标异常: " + e.getTargetException());
                if (e.getTargetException() != null) {
                    e.getTargetException().printStackTrace();
                }
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("[JarSigner] ZipSigner 签名异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 查找当前应用的 APK 文件
     */
    private File findApkFile() {
        // 1. 优先使用配置中指定的源 APK
        if (config.getSourceApk() != null && config.getSourceApk().exists()) {
            return config.getSourceApk();
        }
        
        // 2. 尝试从配置中获取 APK 路径
        if (config.getKeystoreFile() != null) {
            // 如果配置了密钥库，尝试在同一目录查找 APK
            File dir = config.getKeystoreFile().getParentFile();
            if (dir != null && dir.exists()) {
                File[] apks = dir.listFiles((d, name) -> name.endsWith(".apk"));
                if (apks != null && apks.length > 0) {
                    return apks[0];
                }
            }
        }
        
        // 3. 尝试从系统属性获取（Android 环境）
        try {
            String apkPath = System.getProperty("android.app.apk");
            if (apkPath != null) {
                File apk = new File(apkPath);
                if (apk.exists()) {
                    return apk;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return null;
    }
    
    /**
     * 从 APK 中提取签名文件
     */
    private Map<String, byte[]> extractApkSignatures(File apkFile) throws IOException {
        Map<String, byte[]> signatures = new LinkedHashMap<>();
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                
                // 只提取 META-INF 目录下的签名相关文件
                if (name.startsWith("META-INF/") && 
                    (name.endsWith(".SF") || name.endsWith(".RSA") || 
                     name.endsWith(".DSA") || name.endsWith(".EC") ||
                     name.equals("META-INF/MANIFEST.MF"))) {
                    
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    
                    signatures.put(name, baos.toByteArray());
                    System.out.println("[JarSigner]   - " + name + " (" + baos.size() + " 字节)");
                }
            }
        }
        
        return signatures;
    }
    
    /**
     * 重新打包 JAR 文件，使用 APK 的签名
     */
    private void repackJarWithApkSignature(File jarFile, Map<String, byte[]> entries, 
                                            Manifest manifest, Map<String, byte[]> apkSignatures) throws IOException {
        File tempFile = new File(jarFile.getParentFile(), jarFile.getName() + ".tmp");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
            // 1. 写入新生成的 MANIFEST.MF（包含补丁文件的摘要）
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            manifest.write(zos);
            zos.closeEntry();
            
            // 2. 写入从 APK 复制的签名文件（.SF, .RSA 等）
            for (Map.Entry<String, byte[]> sigEntry : apkSignatures.entrySet()) {
                String name = sigEntry.getKey();
                // 跳过 APK 的 MANIFEST.MF，使用我们新生成的
                if (name.equals("META-INF/MANIFEST.MF")) {
                    continue;
                }
                
                zos.putNextEntry(new ZipEntry(name));
                zos.write(sigEntry.getValue());
                zos.closeEntry();
                System.out.println("[JarSigner]   写入: " + name);
            }
            
            // 3. 写入所有补丁文件
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        
        // 替换原文件
        if (!jarFile.delete()) {
            throw new IOException("Failed to delete original JAR file");
        }
        if (!tempFile.renameTo(jarFile)) {
            throw new IOException("Failed to rename temporary file");
        }
    }
    
    /**
     * 加载密钥库
     * 使用 Native JKS 解析器（如果可用），否则回退到 BouncyCastle
     */
    private KeyStore loadKeyStore() throws Exception {
        System.out.println("[JarSigner] 尝试加载密钥库");
        
        // 优先尝试使用 Native JKS 解析器
        if (JKSNative.isAvailable()) {
            System.out.println("[JarSigner] 尝试使用 Native JKS 解析器");
            try {
                return loadKeyStoreNative();
            } catch (Exception e) {
                System.out.println("[JarSigner] Native JKS 解析失败，回退到 BouncyCastle: " + e.getMessage());
                // 继续尝试 BouncyCastle
            }
        } else {
            System.out.println("[JarSigner] Native JKS 解析器不可用，使用 BouncyCastle");
        }
        
        // 回退到 BouncyCastle
        return loadKeyStoreBouncyCastle();
    }
    
    /**
     * 使用 Native 解析器加载 JKS
     */
    private KeyStore loadKeyStoreNative() throws Exception {
        System.out.println("[JarSigner] 使用 Native JKS 解析器");
        
        // 加载 JKS 文件
        boolean loaded = JKSNative.loadKeyStore(
            config.getKeystoreFile().getAbsolutePath(),
            config.getKeystorePassword()
        );
        
        if (!loaded) {
            throw new Exception("Native JKS 加载失败: " + JKSNative.getError());
        }
        
        // 获取私钥数据
        byte[] privateKeyData = JKSNative.getPrivateKey(
            config.getKeyAlias(),
            config.getKeyPassword()
        );
        
        if (privateKeyData == null) {
            throw new Exception("无法获取私钥: " + JKSNative.getError());
        }
        
        System.out.println("[JarSigner] 私钥数据大小: " + privateKeyData.length + " 字节");
        System.out.println("[JarSigner] 私钥数据前16字节: " + bytesToHex(privateKeyData, 0, Math.min(16, privateKeyData.length)));
        
        // 获取证书链
        byte[][] certChainData = JKSNative.getCertificateChain(config.getKeyAlias());
        if (certChainData == null || certChainData.length == 0) {
            throw new Exception("无法获取证书链");
        }
        
        // 创建一个临时的 PKCS12 KeyStore 来存储解析的数据
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        
        // 解析私钥（PKCS#8 格式）
        java.security.spec.PKCS8EncodedKeySpec keySpec = 
            new java.security.spec.PKCS8EncodedKeySpec(privateKeyData);
        
        // 尝试不同的密钥算法
        PrivateKey privateKey = null;
        String[] algorithms = {"RSA", "DSA", "EC"};
        Exception lastException = null;
        for (String algorithm : algorithms) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
                privateKey = keyFactory.generatePrivate(keySpec);
                System.out.println("[JarSigner] ✓ 私钥算法: " + algorithm);
                break;
            } catch (Exception e) {
                System.out.println("[JarSigner] ✗ 尝试算法 " + algorithm + " 失败: " + e.getMessage());
                lastException = e;
                // 尝试下一个算法
            }
        }
        
        if (privateKey == null) {
            if (lastException != null) {
                lastException.printStackTrace();
            }
            throw new Exception("无法解析私钥");
        }
        
        // 解析证书链
        java.security.cert.CertificateFactory certFactory = 
            java.security.cert.CertificateFactory.getInstance("X.509");
        
        Certificate[] certChain = new Certificate[certChainData.length];
        for (int i = 0; i < certChainData.length; i++) {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(certChainData[i]);
            certChain[i] = certFactory.generateCertificate(bis);
        }
        
        // 将私钥和证书链存入 KeyStore
        keyStore.setKeyEntry(
            config.getKeyAlias(),
            privateKey,
            config.getKeyPassword().toCharArray(),
            certChain
        );
        
        System.out.println("[JarSigner] ✓ Native JKS 加载成功");
        
        // 释放 Native 资源
        JKSNative.release();
        
        return keyStore;
    }
    
    /**
     * 字节数组转十六进制字符串（用于调试）
     */
    private String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + length && i < bytes.length; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString();
    }
    
    /**
     * 使用 BouncyCastle 加载密钥库
     */
    private KeyStore loadKeyStoreBouncyCastle() throws Exception {
        System.out.println("[JarSigner] 尝试加载密钥库，支持的格式: JKS, PKCS12, BKS");
        
        // 尝试的密钥库类型列表（按优先级）
        String[] keyStoreTypes = {"JKS", "PKCS12", "BKS"};
        
        Exception lastException = null;
        for (String type : keyStoreTypes) {
            try {
                System.out.println("[JarSigner] 尝试格式: " + type);
                
                // 尝试使用 BouncyCastle Provider
                KeyStore keyStore;
                try {
                    // 先尝试使用 BouncyCastle Provider
                    keyStore = KeyStore.getInstance(type, "BC");
                    System.out.println("[JarSigner] 使用 BouncyCastle Provider");
                } catch (Exception e) {
                    // 如果 BC 失败，尝试默认 Provider
                    System.out.println("[JarSigner] BouncyCastle Provider 失败，尝试默认 Provider");
                    keyStore = KeyStore.getInstance(type);
                }
                
                try (FileInputStream fis = new FileInputStream(config.getKeystoreFile())) {
                    keyStore.load(fis, config.getKeystorePassword().toCharArray());
                }
                System.out.println("[JarSigner] ✓ 成功使用格式: " + type);
                return keyStore;
            } catch (Exception e) {
                System.out.println("[JarSigner] ✗ 格式 " + type + " 失败: " + e.getMessage());
                e.printStackTrace();
                lastException = e;
            }
        }
        
        // 所有格式都失败
        System.err.println("[JarSigner] 所有密钥库格式都失败");
        throw new Exception("无法加载密钥库，尝试了所有支持的格式", lastException);
    }
    
    /**
     * 读取 JAR 文件中的所有条目
     * 使用 ZipFile API 而不是 ZipInputStream 以支持所有 ZIP 格式
     */
    private Map<String, byte[]> readJarEntries(File jarFile) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(jarFile)) {
            java.util.Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                
                // 跳过目录和旧的签名文件
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                
                // 读取文件内容
                try (java.io.InputStream is = zipFile.getInputStream(entry)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    entries.put(entry.getName(), baos.toByteArray());
                }
            }
        }
        
        return entries;
    }
    
    /**
     * 生成 MANIFEST.MF
     */
    private Manifest generateManifest(Map<String, byte[]> entries) throws NoSuchAlgorithmException {
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttrs.putValue("Created-By", "HotUpdate JarSigner");
        
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue();
            
            // 计算文件的 SHA-256 摘要
            digest.reset();
            byte[] hash = digest.digest(data);
            String hashBase64 = Base64.getEncoder().encodeToString(hash);
            
            // 添加到 manifest
            Attributes attrs = new Attributes();
            attrs.putValue(DIGEST_ATTR, hashBase64);
            manifest.getEntries().put(name, attrs);
        }
        
        return manifest;
    }
    
    /**
     * 生成 .SF 签名文件
     */
    private byte[] generateSignatureFile(Manifest manifest) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // 写入 .SF 文件头
        baos.write("Signature-Version: 1.0\r\n".getBytes("UTF-8"));
        baos.write(("Created-By: HotUpdate JarSigner\r\n").getBytes("UTF-8"));
        
        // 计算整个 MANIFEST.MF 的摘要
        ByteArrayOutputStream manifestBytes = new ByteArrayOutputStream();
        manifest.write(manifestBytes);
        byte[] manifestData = manifestBytes.toByteArray();
        
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        byte[] manifestHash = digest.digest(manifestData);
        String manifestHashBase64 = Base64.getEncoder().encodeToString(manifestHash);
        baos.write((DIGEST_MANIFEST_ATTR + ": " + manifestHashBase64 + "\r\n").getBytes("UTF-8"));
        baos.write("\r\n".getBytes("UTF-8"));
        
        // 为每个条目生成摘要
        for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
            String name = entry.getKey();
            Attributes attrs = entry.getValue();
            
            // 计算该条目在 MANIFEST.MF 中的摘要
            ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
            entryBytes.write(("Name: " + name + "\r\n").getBytes("UTF-8"));
            for (Object key : attrs.keySet()) {
                String attrName = key.toString();
                String attrValue = attrs.getValue(attrName);
                entryBytes.write((attrName + ": " + attrValue + "\r\n").getBytes("UTF-8"));
            }
            entryBytes.write("\r\n".getBytes("UTF-8"));
            
            digest.reset();
            byte[] entryHash = digest.digest(entryBytes.toByteArray());
            String entryHashBase64 = Base64.getEncoder().encodeToString(entryHash);
            
            baos.write(("Name: " + name + "\r\n").getBytes("UTF-8"));
            baos.write((DIGEST_ATTR + ": " + entryHashBase64 + "\r\n").getBytes("UTF-8"));
            baos.write("\r\n".getBytes("UTF-8"));
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 生成签名块文件（.RSA/.DSA/.EC）
     */
    private byte[] generateSignatureBlock(byte[] sfBytes, PrivateKey privateKey, Certificate[] certChain) throws Exception {
        // 对 .SF 文件进行签名
        String signatureAlgorithm = getSignatureAlgorithm(privateKey.getAlgorithm());
        Signature signature = Signature.getInstance(signatureAlgorithm);
        signature.initSign(privateKey);
        signature.update(sfBytes);
        byte[] signatureBytes = signature.sign();
        
        // 生成 PKCS#7 签名块
        return generatePKCS7Block(sfBytes, signatureBytes, certChain, signatureAlgorithm, privateKey);
    }
    
    /**
     * 生成 PKCS#7 签名块
     * 
     * 使用 BouncyCastle 生成标准的 PKCS#7 格式，兼容 JarFile 验证
     */
    private byte[] generatePKCS7Block(byte[] data, byte[] signature, Certificate[] certChain, String algorithm, PrivateKey privateKey) throws Exception {
        try {
            // 尝试使用 BouncyCastle
            return generateBouncyCastlePKCS7(data, certChain, algorithm, privateKey);
        } catch (ClassNotFoundException e) {
            // BouncyCastle 不可用，尝试使用 JDK 内部 API
            try {
                return generateJdkPKCS7(data, signature, certChain, algorithm);
            } catch (ClassNotFoundException e2) {
                // 两种方法都不可用
                throw new Exception("Neither BouncyCastle nor JDK PKCS7 API is available. Please add BouncyCastle dependency.");
            }
        }
    }
    
    /**
     * 使用 BouncyCastle 生成 PKCS#7 签名块
     * 
     * 这个方法会重新对数据签名，因为 BouncyCastle 需要完整的签名过程
     */
    private byte[] generateBouncyCastlePKCS7(byte[] data, Certificate[] certChain, String algorithm, PrivateKey privateKey) throws Exception {
        // 动态加载 BouncyCastle 类
        Class<?> securityClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
        Class<?> cmsSignedDataGeneratorClass = Class.forName("org.bouncycastle.cms.CMSSignedDataGenerator");
        Class<?> cmsProcessableByteArrayClass = Class.forName("org.bouncycastle.cms.CMSProcessableByteArray");
        Class<?> jcaSignerInfoGeneratorBuilderClass = Class.forName("org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder");
        Class<?> jcaDigestCalculatorProviderBuilderClass = Class.forName("org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder");
        Class<?> jcaContentSignerBuilderClass = Class.forName("org.bouncycastle.operator.jcajce.JcaContentSignerBuilder");
        Class<?> jcaCertStoreClass = Class.forName("org.bouncycastle.cert.jcajce.JcaCertStore");
        
        // 注册 BouncyCastle Provider
        Object bcProvider = securityClass.getDeclaredConstructor().newInstance();
        java.security.Security.addProvider((java.security.Provider) bcProvider);
        
        // 创建 CMSSignedDataGenerator
        Object generator = cmsSignedDataGeneratorClass.getDeclaredConstructor().newInstance();
        
        // 创建 DigestCalculatorProvider
        Object digestCalcProviderBuilder = jcaDigestCalculatorProviderBuilderClass.getDeclaredConstructor().newInstance();
        java.lang.reflect.Method setProviderMethod = jcaDigestCalculatorProviderBuilderClass.getMethod("setProvider", String.class);
        setProviderMethod.invoke(digestCalcProviderBuilder, "BC");
        java.lang.reflect.Method buildDigestMethod = jcaDigestCalculatorProviderBuilderClass.getMethod("build");
        Object digestCalcProvider = buildDigestMethod.invoke(digestCalcProviderBuilder);
        
        // 创建 ContentSigner
        Object contentSignerBuilder = jcaContentSignerBuilderClass.getDeclaredConstructor(String.class).newInstance(algorithm);
        java.lang.reflect.Method setProviderMethod2 = jcaContentSignerBuilderClass.getMethod("setProvider", String.class);
        setProviderMethod2.invoke(contentSignerBuilder, "BC");
        java.lang.reflect.Method buildSignerMethod = jcaContentSignerBuilderClass.getMethod("build", PrivateKey.class);
        Object contentSigner = buildSignerMethod.invoke(contentSignerBuilder, privateKey);
        
        // 创建 SignerInfoGenerator
        Object signerInfoGeneratorBuilder = jcaSignerInfoGeneratorBuilderClass.getDeclaredConstructor(
            Class.forName("org.bouncycastle.operator.DigestCalculatorProvider")
        ).newInstance(digestCalcProvider);
        
        java.lang.reflect.Method buildSignerInfoMethod = jcaSignerInfoGeneratorBuilderClass.getMethod(
            "build",
            Class.forName("org.bouncycastle.operator.ContentSigner"),
            X509Certificate.class
        );
        Object signerInfoGenerator = buildSignerInfoMethod.invoke(
            signerInfoGeneratorBuilder,
            contentSigner,
            (X509Certificate) certChain[0]
        );
        
        // 添加 SignerInfoGenerator 到 CMSSignedDataGenerator
        java.lang.reflect.Method addSignerInfoGeneratorMethod = cmsSignedDataGeneratorClass.getMethod(
            "addSignerInfoGenerator",
            Class.forName("org.bouncycastle.cms.SignerInfoGenerator")
        );
        addSignerInfoGeneratorMethod.invoke(generator, signerInfoGenerator);
        
        // 添加证书
        java.util.List<Certificate> certList = java.util.Arrays.asList(certChain);
        Object certStore = jcaCertStoreClass.getDeclaredConstructor(java.util.Collection.class).newInstance(certList);
        java.lang.reflect.Method addCertificatesMethod = cmsSignedDataGeneratorClass.getMethod(
            "addCertificates",
            Class.forName("org.bouncycastle.util.Store")
        );
        addCertificatesMethod.invoke(generator, certStore);
        
        // 创建 CMSProcessableByteArray
        Object processableData = cmsProcessableByteArrayClass.getDeclaredConstructor(byte[].class).newInstance(data);
        
        // 生成 CMSSignedData
        java.lang.reflect.Method generateMethod = cmsSignedDataGeneratorClass.getMethod(
            "generate",
            Class.forName("org.bouncycastle.cms.CMSTypedData"),
            boolean.class
        );
        Object cmsSignedData = generateMethod.invoke(generator, processableData, false);
        
        // 获取编码后的字节
        java.lang.reflect.Method getEncodedMethod = cmsSignedData.getClass().getMethod("getEncoded");
        return (byte[]) getEncodedMethod.invoke(cmsSignedData);
    }
    
    /**
     * 使用 JDK 内部 API 生成 PKCS#7 签名块
     */
    private byte[] generateJdkPKCS7(byte[] data, byte[] signature, Certificate[] certChain, String algorithm) throws Exception {
        Class<?> pkcs7Class = Class.forName("sun.security.pkcs.PKCS7");
        Class<?> signerInfoClass = Class.forName("sun.security.pkcs.SignerInfo");
        Class<?> algorithmIdClass = Class.forName("sun.security.x509.AlgorithmId");
        
        // 创建 AlgorithmId
        java.lang.reflect.Method getMethod = algorithmIdClass.getMethod("get", String.class);
        Object digestAlgorithmId = getMethod.invoke(null, DIGEST_ALGORITHM);
        Object signatureAlgorithmId = getMethod.invoke(null, algorithm);
        
        // 创建 SignerInfo
        java.lang.reflect.Constructor<?> signerInfoConstructor = signerInfoClass.getConstructor(
            java.security.cert.X509Certificate.class,
            algorithmIdClass,
            algorithmIdClass,
            byte[].class
        );
        Object signerInfo = signerInfoConstructor.newInstance(
            (X509Certificate) certChain[0],
            digestAlgorithmId,
            signatureAlgorithmId,
            signature
        );
        
        // 创建 PKCS7
        java.lang.reflect.Constructor<?> pkcs7Constructor = pkcs7Class.getConstructor(
            new Class[]{algorithmIdClass.arrayType(), java.security.cert.X509Certificate[].class, byte[].class, signerInfoClass.arrayType()}
        );
        
        Object[] digestAlgorithmIds = (Object[]) java.lang.reflect.Array.newInstance(algorithmIdClass, 1);
        java.lang.reflect.Array.set(digestAlgorithmIds, 0, digestAlgorithmId);
        
        X509Certificate[] x509Certs = new X509Certificate[certChain.length];
        for (int i = 0; i < certChain.length; i++) {
            x509Certs[i] = (X509Certificate) certChain[i];
        }
        
        Object[] signerInfos = (Object[]) java.lang.reflect.Array.newInstance(signerInfoClass, 1);
        java.lang.reflect.Array.set(signerInfos, 0, signerInfo);
        
        Object pkcs7 = pkcs7Constructor.newInstance(digestAlgorithmIds, x509Certs, data, signerInfos);
        
        // 编码为 DER 格式
        java.lang.reflect.Method encodeSignedDataMethod = pkcs7Class.getMethod("encodeSignedData");
        return (byte[]) encodeSignedDataMethod.invoke(pkcs7);
    }
    
    /**
     * 根据密钥算法确定签名算法
     */
    private String getSignatureAlgorithm(String keyAlgorithm) {
        switch (keyAlgorithm.toUpperCase()) {
            case "RSA":
                return "SHA256withRSA";
            case "DSA":
                return "SHA256withDSA";
            case "EC":
                return "SHA256withECDSA";
            default:
                return "SHA256withRSA";
        }
    }
    
    /**
     * 确定签名文件扩展名
     */
    private String getSignatureExtension(String keyAlgorithm) {
        switch (keyAlgorithm.toUpperCase()) {
            case "RSA":
                return "RSA";
            case "DSA":
                return "DSA";
            case "EC":
                return "EC";
            default:
                return "RSA";
        }
    }
    
    /**
     * 重新打包 JAR 文件，包含签名
     */
    private void repackJar(File jarFile, Map<String, byte[]> entries, Manifest manifest, 
                          byte[] sfBytes, byte[] signatureBlock, String signatureExt) throws IOException {
        File tempFile = new File(jarFile.getParentFile(), jarFile.getName() + ".tmp");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
            // 1. 写入 MANIFEST.MF
            zos.putNextEntry(new ZipEntry(MANIFEST_NAME));
            manifest.write(zos);
            zos.closeEntry();
            
            // 2. 写入 .SF 文件
            String sfName = "META-INF/CERT.SF";
            zos.putNextEntry(new ZipEntry(sfName));
            zos.write(sfBytes);
            zos.closeEntry();
            
            // 3. 写入签名块文件
            String sigName = "META-INF/CERT." + signatureExt;
            zos.putNextEntry(new ZipEntry(sigName));
            zos.write(signatureBlock);
            zos.closeEntry();
            
            // 4. 写入所有原始文件
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String name = entry.getKey();
                byte[] data = entry.getValue();
                
                ZipEntry zipEntry = new ZipEntry(name);
                
                // resources.arsc 必须使用 STORE 模式（不压缩）
                if ("resources.arsc".equals(name)) {
                    zipEntry.setMethod(ZipEntry.STORED);
                    zipEntry.setSize(data.length);
                    zipEntry.setCompressedSize(data.length);
                    zipEntry.setCrc(calculateCrc32(data));
                }
                
                zos.putNextEntry(zipEntry);
                zos.write(data);
                zos.closeEntry();
            }
        }
        
        // 替换原文件
        if (!jarFile.delete()) {
            throw new IOException("Failed to delete original JAR file");
        }
        if (!tempFile.renameTo(jarFile)) {
            throw new IOException("Failed to rename temporary file");
        }
    }
    
    /**
     * 计算 CRC32 校验和
     */
    private long calculateCrc32(byte[] data) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        return crc.getValue();
    }
    
    /**
     * 签名异常
     */
    public static class SigningException extends Exception {
        public SigningException(String message) {
            super(message);
        }
        
        public SigningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
