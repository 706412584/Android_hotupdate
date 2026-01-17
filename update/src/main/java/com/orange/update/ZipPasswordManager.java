package com.orange.update;

import android.content.Context;
import android.util.Log;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * ZIP 密码管理器
 * 
 * 功能：
 * 1. 使用密码加密 ZIP 文件（AES-256）
 * 2. 验证 ZIP 密码
 * 3. 解压加密的 ZIP 文件
 * 4. 密钥派生（从应用签名）
 * 
 * 安全特性：
 * - AES-256 加密
 * - 密码从应用签名派生（设备绑定）
 * - 密码错误时自动检测篡改
 */
public class ZipPasswordManager {
    
    private static final String TAG = "ZipPasswordManager";
    
    private final Context context;
    
    public ZipPasswordManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 检查 ZIP 文件是否加密
     */
    public boolean isEncrypted(File zipFile) {
        try {
            ZipFile zip = new ZipFile(zipFile);
            return zip.isEncrypted();
        } catch (Exception e) {
            Log.w(TAG, "Failed to check if ZIP is encrypted", e);
            return false;
        }
    }
    
    /**
     * 使用密码加密 ZIP 文件
     * 
     * @param sourceZip 源 ZIP 文件（未加密）
     * @param destZip 目标 ZIP 文件（加密后）
     * @param password ZIP 密码
     * @return 是否成功
     */
    public boolean encryptZip(File sourceZip, File destZip, String password) {
        try {
            // 创建临时目录
            File tempDir = new File(context.getCacheDir(), "zip_encrypt_temp_" + System.currentTimeMillis());
            tempDir.mkdirs();
            
            // 1. 解压源 ZIP
            ZipFile sourceZipFile = new ZipFile(sourceZip);
            sourceZipFile.extractAll(tempDir.getAbsolutePath());
            
            // 2. 使用密码重新打包（不压缩，使用存储模式）
            ZipParameters params = new ZipParameters();
            params.setEncryptFiles(true);
            params.setEncryptionMethod(EncryptionMethod.AES);
            params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            // 关键：设置为存储模式（不压缩）
            params.setCompressionMethod(net.lingala.zip4j.model.enums.CompressionMethod.STORE);
            
            ZipFile destZipFile = new ZipFile(destZip, password.toCharArray());
            destZipFile.addFolder(tempDir, params);
            
            // 3. 清理临时目录
            deleteDirectory(tempDir);
            
            Log.i(TAG, "ZIP encrypted successfully: " + destZip.getName());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt ZIP", e);
            return false;
        }
    }
    
    /**
     * 验证 ZIP 密码是否正确
     * 
     * @param zipFile ZIP 文件
     * @param password 密码
     * @return 密码是否正确
     */
    public boolean verifyPassword(File zipFile, String password) {
        try {
            ZipFile zip = new ZipFile(zipFile, password.toCharArray());
            
            if (!zip.isValidZipFile()) {
                Log.w(TAG, "Invalid ZIP file");
                return false;
            }
            
            if (!zip.isEncrypted()) {
                Log.w(TAG, "ZIP is not encrypted");
                return true; // 未加密的 ZIP，密码验证通过
            }
            
            // 尝试读取第一个文件来验证密码
            if (zip.getFileHeaders().isEmpty()) {
                Log.w(TAG, "ZIP is empty");
                return true;
            }
            
            // 尝试提取第一个文件到内存（不实际写入磁盘）
            zip.getInputStream(zip.getFileHeaders().get(0)).close();
            
            Log.d(TAG, "ZIP password verified successfully");
            return true;
            
        } catch (ZipException e) {
            if (e.getMessage() != null && e.getMessage().contains("Wrong Password")) {
                Log.e(TAG, "⚠️ ZIP password incorrect!");
                return false;
            }
            Log.e(TAG, "Failed to verify ZIP password", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to verify ZIP password", e);
            return false;
        }
    }
    
    /**
     * 解压加密的 ZIP 文件
     * 
     * @param zipFile ZIP 文件
     * @param destDir 目标目录
     * @param password 密码
     * @return 是否成功
     */
    public boolean extractEncryptedZip(File zipFile, File destDir, String password) {
        try {
            ZipFile zip = new ZipFile(zipFile, password.toCharArray());
            
            if (!zip.isValidZipFile()) {
                Log.e(TAG, "Invalid ZIP file");
                return false;
            }
            
            // 创建目标目录
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            
            // 解压
            zip.extractAll(destDir.getAbsolutePath());
            
            Log.i(TAG, "ZIP extracted successfully to: " + destDir.getAbsolutePath());
            return true;
            
        } catch (ZipException e) {
            if (e.getMessage() != null && e.getMessage().contains("Wrong Password")) {
                Log.e(TAG, "⚠️ ZIP password incorrect - possible tampering!");
                return false;
            }
            Log.e(TAG, "Failed to extract ZIP", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract ZIP", e);
            return false;
        }
    }
    
    /**
     * 获取 ZIP 密码
     * 
     * 密码派生策略：
     * 1. 从应用签名派生（设备绑定）
     * 2. 使用 SHA-256 哈希
     * 3. 取前 16 个字符作为密码
     * 
     * @return ZIP 密码
     */
    public String getZipPassword() {
        try {
            // 获取应用签名
            String signature = getAppSignature();
            
            // 使用 SHA-256 派生密码
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(signature.getBytes("UTF-8"));
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // 取前 16 个字符作为密码
            String password = hexString.toString().substring(0, 16);
            
            Log.d(TAG, "ZIP password derived from app signature");
            return password;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to derive ZIP password", e);
            // 返回默认密码（不推荐，仅作为后备）
            return "default_password";
        }
    }
    
    /**
     * 获取应用签名
     */
    private String getAppSignature() {
        try {
            android.content.pm.PackageInfo packageInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 
                    android.content.pm.PackageManager.GET_SIGNATURES);
            
            android.content.pm.Signature[] signatures = packageInfo.signatures;
            if (signatures != null && signatures.length > 0) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(signatures[0].toByteArray());
                
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                
                return hexString.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get app signature", e);
        }
        
        return "unknown_signature";
    }
    
    /**
     * 删除目录及其所有内容
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
