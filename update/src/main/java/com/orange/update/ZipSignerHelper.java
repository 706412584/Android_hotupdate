package com.orange.update;

import android.util.Log;

import com.orange.update.signer.JksSigner;

import java.io.File;

import kellinwood.security.zipsigner.ZipSigner;

/**
 * ZipSigner 库包装类
 * 
 * 提供对签名库的封装，用于支持 JKS keystore 的原生加载和签名。
 * 
 * 功能：
 * - 使用 JKS 对 ZIP 文件进行签名（原生支持）
 * - 作为 BKS 方案的备选方案
 */
public class ZipSignerHelper {
    
    private static final String TAG = "ZipSignerHelper";
    
    /**
     * 使用签名工具对 ZIP 文件进行签名（支持 JKS）
     * 
     * 优先使用 JksSigner（支持 Android JKS），
     * 如果不可用则回退到 ZipSigner
     * 
     * @param inputFile 输入 ZIP 文件
     * @param outputFile 输出签名后的 ZIP 文件
     * @param keystoreFile keystore 文件（支持 JKS/BKS）
     * @param keystorePassword keystore 密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @return 签名是否成功
     */
    public static boolean signZipWithJks(File inputFile, File outputFile,
                                         File keystoreFile, String keystorePassword,
                                         String keyAlias, String keyPassword) {
        
        // 优先尝试使用 JksSigner（支持 Android JKS）
        if (JksSigner.isAvailable()) {
            Log.d(TAG, "使用 JksSigner");
            return JksSigner.sign(inputFile, outputFile, 
                keystoreFile, keystorePassword, keyAlias, keyPassword);
        }
        
        // 回退到 ZipSigner（可能不支持 Android JKS）
        try {
            Log.d(TAG, "使用 ZipSigner 签名: " + inputFile.getName());
            Log.d(TAG, "  Keystore: " + keystoreFile.getName());
            Log.d(TAG, "  别名: " + keyAlias);
            
            // 创建 ZipSigner 实例
            ZipSigner zipSigner = new ZipSigner();
            
            // 转换为 URL
            java.net.URL keystoreUrl = keystoreFile.toURI().toURL();
            
            Log.d(TAG, "  Keystore URL: " + keystoreUrl);
            
            // 执行签名
            // signZip(URL keystoreUrl, String keystoreType, char[] keystorePass, 
            //         String keyAlias, char[] keyPass, String signatureAlgorithm, 
            //         String inputFilename, String outputFilename)
            zipSigner.signZip(
                keystoreUrl,
                "JKS",  // keystore 类型
                keystorePassword.toCharArray(),
                keyAlias,
                keyPassword.toCharArray(),
                "SHA1withRSA",  // 签名算法
                inputFile.getAbsolutePath(),
                outputFile.getAbsolutePath()
            );
            
            Log.i(TAG, "✓ ZipSigner 签名成功");
            Log.i(TAG, "  输出文件: " + outputFile.getName());
            Log.i(TAG, "  输出大小: " + formatSize(outputFile.length()));
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "ZipSigner 签名失败: " + e.getMessage(), e);
            
            // 打印更详细的错误信息
            if (e.getCause() != null) {
                Log.e(TAG, "  原因: " + e.getCause().getMessage());
            }
            
            return false;
        }
    }
    
    /**
     * 检查签名工具是否可用
     * 
     * @return 是否可用
     */
    public static boolean isAvailable() {
        // 优先检查 JksSigner
        if (JksSigner.isAvailable()) {
            return true;
        }
        
        // 检查 ZipSigner
        try {
            Class.forName("kellinwood.security.zipsigner.ZipSigner");
            Log.d(TAG, "✓ ZipSigner 库可用");
            return true;
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "✗ 签名工具不可用");
            return false;
        }
    }
    
    /**
     * 格式化文件大小
     */
    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }
}
