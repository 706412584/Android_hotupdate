package com.orange.update;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 安全管理器，负责补丁的加密、解密和签名验证。
 * 
 * 功能：
 * - Android KeyStore 密钥管理
 * - AES-256-GCM 加密/解密
 * - RSA-2048 签名验证
 * - 安全删除临时文件
 */
public class SecurityManager {
    
    private static final String TAG = "SecurityManager";
    
    // KeyStore 相关常量
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "patch_encryption_key";
    
    // 加密算法常量
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM_AES = "AES";
    private static final String KEY_ALGORITHM_RSA = "RSA";
    
    // GCM 参数
    private static final int GCM_IV_LENGTH = 12;  // 12 bytes for GCM IV
    private static final int GCM_TAG_LENGTH = 128; // 128 bits for GCM auth tag
    private static final int AES_KEY_SIZE = 256;   // 256 bits for AES key
    
    // 安全删除覆写次数
    private static final int SECURE_DELETE_PASSES = 3;
    
    private final Context context;
    private KeyStore keyStore;
    private PublicKey signaturePublicKey;
    private boolean debugMode;

    
    /**
     * 创建 SecurityManager 实例
     * @param context 应用上下文
     * @throws SecurityException 如果 KeyStore 初始化失败
     */
    public SecurityManager(Context context) {
        this(context, false);
    }
    
    /**
     * 创建 SecurityManager 实例
     * @param context 应用上下文
     * @param debugMode 是否开启调试模式
     * @throws SecurityException 如果 KeyStore 初始化失败
     */
    public SecurityManager(Context context, boolean debugMode) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        this.debugMode = debugMode;
        initKeyStore();
        loadEmbeddedPublicKey();
    }
    
    /**
     * 初始化 Android KeyStore
     */
    private void initKeyStore() {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
        } catch (KeyStoreException | CertificateException | 
                 NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Failed to initialize KeyStore", e);
            throw new SecurityException("Failed to initialize KeyStore: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载编译时嵌入的 RSA 公钥
     * 用于验证补丁签名
     */
    private void loadEmbeddedPublicKey() {
        try {
            // 编译时嵌入的 RSA-2048 公钥（Base64 编码）
            // 实际使用时应替换为真实的公钥
            String embeddedPublicKeyBase64 = getEmbeddedPublicKey();
            
            if (embeddedPublicKeyBase64 != null && !embeddedPublicKeyBase64.isEmpty()) {
                byte[] publicKeyBytes = Base64.decode(embeddedPublicKeyBase64, Base64.DEFAULT);
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM_RSA);
                signaturePublicKey = keyFactory.generatePublic(keySpec);
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e(TAG, "Failed to load embedded public key", e);
            // 不抛出异常，允许在没有公钥的情况下运行（调试模式）
        }
    }
    
    /**
     * 获取嵌入的公钥
     * 子类可以覆盖此方法提供自定义公钥
     * @return Base64 编码的公钥字符串
     */
    protected String getEmbeddedPublicKey() {
        // 默认返回空，实际使用时应在编译时嵌入真实公钥
        // 示例公钥格式（RSA-2048）：
        // MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
        return null;
    }
    
    /**
     * 设置签名验证公钥
     * @param publicKeyBase64 Base64 编码的公钥
     * @throws SecurityException 如果公钥格式无效
     */
    public void setSignaturePublicKey(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isEmpty()) {
            throw new IllegalArgumentException("Public key cannot be null or empty");
        }
        
        try {
            byte[] publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM_RSA);
            signaturePublicKey = keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new SecurityException("Invalid public key format: " + e.getMessage(), e);
        }
    }

    
    // ==================== 密钥管理 ====================
    
    /**
     * 获取或创建 AES 加密密钥
     * 密钥存储在 Android KeyStore 中，受硬件安全模块保护
     * 注意：此方法需要 API 23+ (Android 6.0+)
     * @return AES 密钥
     * @throws SecurityException 如果密钥生成或获取失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public SecretKey getOrCreateEncryptionKey() {
        try {
            // 检查密钥是否已存在
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
            
            // 生成新密钥
            return generateEncryptionKey();
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            Log.e(TAG, "Failed to get or create encryption key", e);
            throw new SecurityException("Failed to get or create encryption key: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成新的 AES-256 加密密钥并存储到 KeyStore
     * @return 生成的密钥
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey generateEncryptionKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_SIZE)
                    .setUserAuthenticationRequired(false);
            
            // Android 7.0+ 支持设置密钥失效
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(false);
            }
            
            keyGenerator.init(builder.build());
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException | 
                 InvalidAlgorithmParameterException e) {
            Log.e(TAG, "Failed to generate encryption key", e);
            throw new SecurityException("Failed to generate encryption key: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除存储的加密密钥
     * @return 是否删除成功
     */
    public boolean deleteEncryptionKey() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
                return true;
            }
            return false;
        } catch (KeyStoreException e) {
            Log.e(TAG, "Failed to delete encryption key", e);
            return false;
        }
    }
    
    /**
     * 检查加密密钥是否存在
     * @return 密钥是否存在
     */
    public boolean hasEncryptionKey() {
        try {
            return keyStore.containsAlias(KEY_ALIAS);
        } catch (KeyStoreException e) {
            Log.e(TAG, "Failed to check encryption key", e);
            return false;
        }
    }

    
    // ==================== AES-256-GCM 加密/解密 ====================
    
    /**
     * 加密补丁文件
     * 使用 AES-256-GCM 加密，输出格式：[IV(12 bytes)] + [encrypted_data] + [auth_tag(16 bytes)]
     * 
     * @param patchFile 原始补丁文件
     * @return 加密后的文件（.enc 扩展名）
     * @throws SecurityException 如果加密失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public File encryptPatch(File patchFile) {
        if (patchFile == null || !patchFile.exists()) {
            throw new IllegalArgumentException("Patch file does not exist");
        }
        
        File encryptedFile = new File(patchFile.getPath() + ".enc");
        
        try {
            // 获取加密密钥
            SecretKey key = getOrCreateEncryptionKey();
            
            // 初始化加密器（不提供 IV，让 Cipher 自动生成）
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            
            // 读取原始文件
            byte[] plainData = readFileBytes(patchFile);
            
            // 加密数据
            byte[] encryptedData = cipher.doFinal(plainData);
            
            // 获取 Cipher 自动生成的 IV
            byte[] iv = cipher.getIV();
            
            // 写入加密文件：IV + 加密数据（包含 auth tag）
            try (FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                fos.write(iv);
                fos.write(encryptedData);
            }
            
            return encryptedFile;
            
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException | IOException e) {
            Log.e(TAG, "Failed to encrypt patch file", e);
            // 清理失败的加密文件
            if (encryptedFile.exists()) {
                encryptedFile.delete();
            }
            throw new SecurityException("Failed to encrypt patch file: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加密字节数组
     * @param data 原始数据
     * @return 加密后的数据（包含 IV）
     * @throws SecurityException 如果加密失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public byte[] encrypt(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        
        try {
            SecretKey key = getOrCreateEncryptionKey();
            
            // 初始化加密器（不提供 IV，让 Cipher 自动生成）
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            
            // 加密数据
            byte[] encryptedData = cipher.doFinal(data);
            
            // 获取 Cipher 自动生成的 IV
            byte[] iv = cipher.getIV();
            
            // 组合 IV + 加密数据
            byte[] result = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);
            
            return result;
            
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException e) {
            Log.e(TAG, "Failed to encrypt data", e);
            throw new SecurityException("Failed to encrypt data: " + e.getMessage(), e);
        }
    }

    
    /**
     * 解密补丁文件
     * 输入格式：[IV(12 bytes)] + [encrypted_data] + [auth_tag(16 bytes)]
     * 
     * @param encryptedFile 加密的补丁文件
     * @return 解密后的文件
     * @throws SecurityException 如果解密失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public File decryptPatch(File encryptedFile) {
        if (encryptedFile == null || !encryptedFile.exists()) {
            throw new IllegalArgumentException("Encrypted file does not exist");
        }
        
        // 生成解密后的文件路径（移除 .enc 扩展名）
        String decryptedPath = encryptedFile.getPath();
        if (decryptedPath.endsWith(".enc")) {
            decryptedPath = decryptedPath.substring(0, decryptedPath.length() - 4);
        } else {
            decryptedPath = decryptedPath + ".dec";
        }
        File decryptedFile = new File(decryptedPath);
        
        try {
            // 获取解密密钥
            SecretKey key = getOrCreateEncryptionKey();
            
            // 读取加密文件
            byte[] encryptedContent = readFileBytes(encryptedFile);
            
            if (encryptedContent.length < GCM_IV_LENGTH) {
                throw new SecurityException("Invalid encrypted file: too short");
            }
            
            // 提取 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedContent, 0, iv, 0, GCM_IV_LENGTH);
            
            // 提取加密数据
            byte[] encryptedData = new byte[encryptedContent.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedContent, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            // 初始化解密器
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            
            // 解密数据
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            // 写入解密文件
            try (FileOutputStream fos = new FileOutputStream(decryptedFile)) {
                fos.write(decryptedData);
            }
            
            return decryptedFile;
            
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | 
                 BadPaddingException | IOException e) {
            Log.e(TAG, "Failed to decrypt patch file", e);
            // 清理失败的解密文件
            if (decryptedFile.exists()) {
                decryptedFile.delete();
            }
            throw new SecurityException("Failed to decrypt patch file: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解密字节数组
     * @param encryptedData 加密的数据（包含 IV）
     * @return 解密后的数据
     * @throws SecurityException 如果解密失败
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public byte[] decrypt(byte[] encryptedData) {
        if (encryptedData == null) {
            throw new IllegalArgumentException("Encrypted data cannot be null");
        }
        
        if (encryptedData.length < GCM_IV_LENGTH) {
            throw new SecurityException("Invalid encrypted data: too short");
        }
        
        try {
            SecretKey key = getOrCreateEncryptionKey();
            
            // 提取 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);
            
            // 提取加密数据
            byte[] cipherData = new byte[encryptedData.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherData, 0, cipherData.length);
            
            // 初始化解密器
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            
            // 解密数据
            return cipher.doFinal(cipherData);
            
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | 
                 BadPaddingException e) {
            Log.e(TAG, "Failed to decrypt data", e);
            throw new SecurityException("Failed to decrypt data: " + e.getMessage(), e);
        }
    }

    
    // ==================== RSA 签名验证 ====================
    
    /**
     * 验证补丁文件的 RSA 签名
     * 
     * @param patchFile 补丁文件
     * @param base64Signature Base64 编码的签名
     * @return 签名是否有效
     */
    public boolean verifySignature(File patchFile, String base64Signature) {
        if (patchFile == null || !patchFile.exists()) {
            Log.e(TAG, "Patch file does not exist");
            return false;
        }
        
        if (base64Signature == null || base64Signature.isEmpty()) {
            Log.e(TAG, "Signature is null or empty");
            return false;
        }
        
        // 调试模式下跳过签名验证
        if (debugMode && signaturePublicKey == null) {
            Log.w(TAG, "Signature verification skipped in debug mode (no public key)");
            return true;
        }
        
        if (signaturePublicKey == null) {
            Log.e(TAG, "No public key available for signature verification");
            return false;
        }
        
        try {
            // 初始化签名验证器
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(signaturePublicKey);
            
            // 读取补丁文件内容并更新签名
            try (FileInputStream fis = new FileInputStream(patchFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    signature.update(buffer, 0, bytesRead);
                }
            }
            
            // 解码并验证签名
            byte[] signatureBytes = Base64.decode(base64Signature, Base64.DEFAULT);
            return signature.verify(signatureBytes);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | 
                 IOException | IllegalArgumentException e) {
            Log.e(TAG, "Signature verification failed", e);
            return false;
        }
    }
    
    /**
     * 验证字节数组的 RSA 签名
     * 
     * @param data 原始数据
     * @param base64Signature Base64 编码的签名
     * @return 签名是否有效
     */
    public boolean verifySignature(byte[] data, String base64Signature) {
        if (data == null) {
            Log.e(TAG, "Data is null");
            return false;
        }
        
        if (base64Signature == null || base64Signature.isEmpty()) {
            Log.e(TAG, "Signature is null or empty");
            return false;
        }
        
        // 调试模式下跳过签名验证
        if (debugMode && signaturePublicKey == null) {
            Log.w(TAG, "Signature verification skipped in debug mode (no public key)");
            return true;
        }
        
        if (signaturePublicKey == null) {
            Log.e(TAG, "No public key available for signature verification");
            return false;
        }
        
        try {
            // 初始化签名验证器
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(signaturePublicKey);
            
            // 更新签名数据
            signature.update(data);
            
            // 解码并验证签名
            byte[] signatureBytes = Base64.decode(base64Signature, Base64.DEFAULT);
            return signature.verify(signatureBytes);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | 
                 IllegalArgumentException e) {
            Log.e(TAG, "Signature verification failed", e);
            return false;
        }
    }
    
    /**
     * 检查是否有可用的签名公钥
     * @return 是否有公钥
     */
    public boolean hasSignaturePublicKey() {
        return signaturePublicKey != null;
    }
    
    /**
     * 检查是否处于调试模式
     * @return 是否为调试模式
     */
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * 设置调试模式
     * @param debugMode 是否开启调试模式
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    
    // ==================== 安全删除 ====================
    
    /**
     * 安全删除文件
     * 通过多次覆写文件内容来防止数据恢复
     * 
     * @param file 要删除的文件
     * @return 是否删除成功
     */
    public boolean secureDelete(File file) {
        if (file == null || !file.exists()) {
            return true; // 文件不存在视为删除成功
        }
        
        if (file.isDirectory()) {
            return secureDeleteDirectory(file);
        }
        
        return secureDeleteFile(file);
    }
    
    /**
     * 安全删除单个文件
     */
    private boolean secureDeleteFile(File file) {
        if (!file.exists() || !file.isFile()) {
            return true;
        }
        
        long fileLength = file.length();
        
        // 如果文件为空，直接删除
        if (fileLength == 0) {
            return file.delete();
        }
        
        try {
            SecureRandom random = new SecureRandom();
            
            // 多次覆写文件内容
            for (int pass = 0; pass < SECURE_DELETE_PASSES; pass++) {
                try (RandomAccessFile raf = new RandomAccessFile(file, "rws")) {
                    byte[] buffer = new byte[4096];
                    long remaining = fileLength;
                    
                    while (remaining > 0) {
                        int toWrite = (int) Math.min(buffer.length, remaining);
                        
                        // 根据覆写轮次使用不同的数据
                        if (pass == 0) {
                            // 第一轮：全 0
                            java.util.Arrays.fill(buffer, 0, toWrite, (byte) 0x00);
                        } else if (pass == 1) {
                            // 第二轮：全 1
                            java.util.Arrays.fill(buffer, 0, toWrite, (byte) 0xFF);
                        } else {
                            // 第三轮：随机数据
                            random.nextBytes(buffer);
                        }
                        
                        raf.write(buffer, 0, toWrite);
                        remaining -= toWrite;
                    }
                    
                    // 强制同步到磁盘
                    raf.getFD().sync();
                }
            }
            
            // 最后删除文件
            return file.delete();
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to securely delete file: " + file.getPath(), e);
            // 尝试普通删除
            return file.delete();
        }
    }
    
    /**
     * 安全删除目录及其内容
     */
    private boolean secureDeleteDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return true;
        }
        
        boolean success = true;
        File[] files = directory.listFiles();
        
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= secureDeleteDirectory(file);
                } else {
                    success &= secureDeleteFile(file);
                }
            }
        }
        
        // 删除空目录
        success &= directory.delete();
        return success;
    }
    
    /**
     * 清理临时目录
     * @param tempDir 临时目录
     * @return 是否清理成功
     */
    public boolean cleanTempDirectory(File tempDir) {
        return secureDelete(tempDir);
    }

    
    // ==================== 工具方法 ====================
    
    /**
     * 读取文件内容为字节数组
     */
    private byte[] readFileBytes(File file) throws IOException {
        long fileLength = file.length();
        if (fileLength > Integer.MAX_VALUE) {
            throw new IOException("File too large: " + fileLength);
        }
        
        byte[] data = new byte[(int) fileLength];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            int remaining = data.length;
            while (remaining > 0) {
                int bytesRead = fis.read(data, offset, remaining);
                if (bytesRead == -1) {
                    throw new IOException("Unexpected end of file");
                }
                offset += bytesRead;
                remaining -= bytesRead;
            }
        }
        return data;
    }
    
    /**
     * 获取临时目录
     * @return 临时目录
     */
    public File getTempDirectory() {
        File tempDir = new File(context.getFilesDir(), "update/temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        // 创建 .nomedia 文件防止媒体扫描
        File noMedia = new File(tempDir, ".nomedia");
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile();
            } catch (IOException e) {
                Log.w(TAG, "Failed to create .nomedia file", e);
            }
        }
        
        return tempDir;
    }
    
    /**
     * 创建临时文件
     * @param prefix 文件名前缀
     * @param suffix 文件名后缀
     * @return 临时文件
     * @throws IOException 如果创建失败
     */
    public File createTempFile(String prefix, String suffix) throws IOException {
        File tempDir = getTempDirectory();
        return File.createTempFile(prefix, suffix, tempDir);
    }
}
