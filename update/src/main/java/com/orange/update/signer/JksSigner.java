package com.orange.update.signer;

import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;

/**
 * JKS 签名工具
 * 
 * 基于 XiaoMozi 签名工具的包装类，提供统一的 JKS 签名接口。
 * 支持在 Android 上使用 JKS keystore 进行签名。
 * 
 * @author Orange Update Team
 */
public class JksSigner {
    
    private static final String TAG = "JksSigner";
    
    /**
     * 对 ZIP/APK 文件进行 JKS 签名
     * 
     * @param inputFile 输入文件
     * @param outputFile 输出文件
     * @param keystoreFile keystore 文件（JKS 格式）
     * @param keystorePassword keystore 密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @return 签名是否成功
     */
    public static boolean sign(File inputFile, File outputFile,
                               File keystoreFile, String keystorePassword,
                               String keyAlias, String keyPassword) {
        try {
            Log.d(TAG, "JKS 签名: " + inputFile.getName());
            Log.d(TAG, "  Keystore: " + keystoreFile.getName());
            Log.d(TAG, "  别名: " + keyAlias);
            
            // 使用反射调用底层签名实现
            Class<?> signerClass = Class.forName("XiaoMozi.签名");
            
            // 获取签名方法
            Method signMethod = signerClass.getMethod("签名", 
                String.class, String.class, String.class, 
                String.class, String.class, String.class);
            
            // 调用签名方法
            // 参数顺序：密钥路径, 密钥密码, 别名, 别名密码, APK路径, 输出APK路径
            Log.d(TAG, "执行签名...");
            
            Boolean result = (Boolean) signMethod.invoke(null,
                keystoreFile.getAbsolutePath(),  // 密钥路径
                keystorePassword,                 // 密钥密码
                keyAlias,                         // 别名
                keyPassword,                      // 别名密码
                inputFile.getAbsolutePath(),     // APK路径
                outputFile.getAbsolutePath()     // 输出APK路径
            );
            
            if (result != null && result) {
                Log.i(TAG, "✓ JKS 签名成功");
                Log.i(TAG, "  输出文件: " + outputFile.getName());
                Log.i(TAG, "  输出大小: " + formatSize(outputFile.length()));
                return true;
            } else {
                // 获取错误日志
                try {
                    Method getErrorMethod = signerClass.getMethod("取错误日志");
                    String errorLog = (String) getErrorMethod.invoke(null);
                    Log.e(TAG, "JKS 签名失败: " + errorLog);
                } catch (Exception e) {
                    Log.e(TAG, "JKS 签名失败（无法获取错误日志）");
                }
                return false;
            }
            
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "JKS 签名工具不可用: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "JKS 签名失败: " + e.getMessage(), e);
            
            // 打印更详细的错误信息
            if (e.getCause() != null) {
                Log.e(TAG, "  原因: " + e.getCause().getMessage());
            }
            
            return false;
        }
    }
    
    /**
     * 检查 JKS 签名工具是否可用
     * 
     * @return 是否可用
     */
    public static boolean isAvailable() {
        try {
            Class.forName("XiaoMozi.签名");
            Log.d(TAG, "✓ JKS 签名工具可用");
            return true;
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "✗ JKS 签名工具不可用");
            return false;
        }
    }
    
    /**
     * 获取签名工具版本信息
     * 
     * @return 版本信息
     */
    public static String getVersion() {
        return "1.0.0 (based on XiaoMozi Signer)";
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
