package com.orange.update.helper;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 格式化助手类
 * 提供各种格式化工具方法
 */
public class FormatHelper {
    
    /**
     * 格式化文件大小
     * @param bytes 字节数
     * @return 格式化后的大小字符串
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * 格式化时间戳
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的时间字符串
     */
    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * 格式化日期
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的日期字符串
     */
    public static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * 格式化日期时间
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的日期时间字符串
     */
    public static String formatDateTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * 获取 APK 信息
     * @param context Context
     * @param apkFile APK 文件
     * @return APK 版本信息
     */
    public static String getApkInfo(Context context, File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) {
                return "v" + info.versionName;
            }
        } catch (Exception e) {
            // 忽略异常，返回文件大小
        }
        return formatSize(apkFile.length());
    }
    
    /**
     * 格式化密钥显示（每64个字符换行）
     * @param key 密钥字符串
     * @return 格式化后的密钥字符串
     */
    public static String formatKey(String key) {
        StringBuilder formatted = new StringBuilder();
        int lineLength = 64;
        for (int i = 0; i < key.length(); i += lineLength) {
            int end = Math.min(i + lineLength, key.length());
            formatted.append(key.substring(i, end)).append("\n");
        }
        return formatted.toString().trim();
    }
    
    /**
     * 格式化百分比
     * @param value 数值
     * @param total 总数
     * @return 格式化后的百分比字符串
     */
    public static String formatPercentage(long value, long total) {
        if (total == 0) {
            return "0%";
        }
        float percentage = (float) value / total * 100;
        return String.format(Locale.getDefault(), "%.1f%%", percentage);
    }
    
    /**
     * 格式化压缩比
     * @param compressedSize 压缩后大小
     * @param originalSize 原始大小
     * @return 格式化后的压缩比字符串
     */
    public static String formatCompressionRatio(long compressedSize, long originalSize) {
        if (originalSize == 0) {
            return "0%";
        }
        float ratio = (float) compressedSize / originalSize * 100;
        return String.format(Locale.getDefault(), "%.1f%%", ratio);
    }
}
