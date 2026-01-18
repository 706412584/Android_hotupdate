package com.orange.update;

import android.util.Log;

import java.io.File;
import java.net.URL;
import java.security.KeyStore;

import kellinwood.security.zipsigner.ZipSigner;

/**
 * ZipSigner 库包装类
 * 
 * 提供对 kellinwood ZipSigner 库的封装，用于支持 JKS keystore 的原生加载和签名。
 * 
 * 功能：
 * - 使用 JKS 对 ZIP 文件进行签名（原生支持）
 * - 作为 BKS 方案的备选方案
 */
public class ZipSignerHelper {
    
    private static final String TAG = "ZipSignerHelper";
    
    /**
     * 使用 ZipSigner 对 ZIP 文件进行签名（支持 JKS）
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
        try {
            Log.d(TAG, "使用 ZipSigner 签名: " + inputFile.getName());
            Log.d(TAG, "  Keystore: " + keystoreFile.getName());
            Log.d(TAG, "  别名: " + keyAlias);
            
            // 创建 ZipSigner 实例
            ZipSigner zipSigner = new ZipSigner();
            
            // 设置为自定义密钥模式
            zipSigner.setKeymode("custom");
            
            // 转换为 URL
            URL keystoreUrl = keystoreFile.toURI().toURL();
            
            // 执行签名
            // signZip(URL keystoreUrl, String keystoreType, char[] keystorePass, 
            //         String keyAlias, char[] keyPass, String signatureAlgorithm, 
            //         String inputFilename, String outputFilename)
            zipSigner.signZip(
                keystoreUrl,
                "auto",  // 自动检测 keystore 类型（JKS/BKS）
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
            return false;
        }
    }
    
    /**
     * 检查 ZipSigner 库是否可用
     * 
     * @return 是否可用
     */
    public static boolean isAvailable() {
        try {
            // 尝试加载 ZipSigner 类
            Class.forName("kellinwood.security.zipsigner.ZipSigner");
            Log.d(TAG, "✓ ZipSigner 库可用");
            return true;
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "✗ ZipSigner 库不可用");
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
