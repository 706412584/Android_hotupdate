package com.orange.update;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 资源合并器
 * 
 * 将原始 APK 的资源和补丁资源合并成一个完整的资源包。
 * 这是 Tinker 等热更新框架的核心思路：
 * - 补丁包只包含差异资源
 * - 客户端将原始资源和补丁资源合并成完整资源包
 * - 加载完整资源包，而不是增量补丁
 */
public class ResourceMerger {
    
    private static final String TAG = "ResourceMerger";
    
    /**
     * 合并原始 APK 和补丁资源，生成完整的资源包
     * 
     * @param context 应用上下文
     * @param patchFile 补丁文件（包含差异资源）
     * @param outputFile 输出文件（完整资源包）
     * @return 是否成功
     */
    public static boolean mergeResources(Context context, File patchFile, File outputFile) {
        // 始终使用真正的原始 APK，而不是之前生成的 merged_resources.apk
        // 这样可以避免在第二次应用补丁时，同时读写同一个文件导致的 SIGBUS 崩溃
        String originalApkPath = context.getApplicationInfo().sourceDir;
        
        Log.d(TAG, "Merging resources:");
        Log.d(TAG, "  Original APK: " + originalApkPath);
        Log.d(TAG, "  Patch file: " + patchFile.getAbsolutePath());
        Log.d(TAG, "  Output file: " + outputFile.getAbsolutePath());
        
        try {
            // 1. 收集补丁中的资源文件名
            Set<String> patchEntries = collectPatchEntries(patchFile);
            Log.d(TAG, "Patch contains " + patchEntries.size() + " entries");
            
            // 2. 创建输出 ZIP
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {
                
                // 3. 先写入补丁中的资源（优先级更高）
                writePatchEntries(patchFile, zos, patchEntries);
                
                // 4. 再写入原始 APK 中的资源（补丁中没有的）
                writeOriginalEntries(originalApkPath, zos, patchEntries);
            }
            
            Log.i(TAG, "Resources merged successfully, size: " + outputFile.length());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to merge resources", e);
            if (outputFile.exists()) {
                outputFile.delete();
            }
            return false;
        }
    }
    
    /**
     * 收集补丁中的资源条目名称
     */
    private static Set<String> collectPatchEntries(File patchFile) throws IOException {
        Set<String> entries = new HashSet<>();
        
        // 使用 ZipFile 而不是 ZipInputStream，避免 STORED 模式的兼容性问题
        try (ZipFile zipFile = new ZipFile(patchFile)) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                String name = entry.getName();
                Log.d(TAG, "  Checking entry: " + name + " -> " + (isResourceEntry(name) ? "YES" : "NO"));
                // 只收集资源相关的条目
                if (isResourceEntry(name)) {
                    entries.add(name);
                }
            }
        }
        
        return entries;
    }
    
    /**
     * 写入补丁中的资源条目
     */
    private static void writePatchEntries(File patchFile, ZipOutputStream zos, 
            Set<String> patchEntries) throws IOException {
        
        try (ZipFile zipFile = new ZipFile(patchFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 只写入资源相关的条目
                if (isResourceEntry(name)) {
                    writeEntry(zipFile, entry, zos);
                    Log.d(TAG, "  [PATCH] " + name);
                }
            }
        }
    }
    
    /**
     * 写入原始 APK 中的资源条目（排除补丁中已有的）
     */
    private static void writeOriginalEntries(String apkPath, ZipOutputStream zos, 
            Set<String> patchEntries) throws IOException {
        
        try (ZipFile zipFile = new ZipFile(apkPath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 只写入资源相关的条目，且补丁中没有的
                if (isResourceEntry(name) && !patchEntries.contains(name)) {
                    writeEntry(zipFile, entry, zos);
                }
            }
        }
    }
    
    /**
     * 写入单个条目
     * 注意：resources.arsc 必须使用 STORED 方式存储，否则 Android 无法加载
     */
    private static void writeEntry(ZipFile zipFile, ZipEntry entry, ZipOutputStream zos) 
            throws IOException {
        
        String name = entry.getName();
        ZipEntry newEntry = new ZipEntry(name);
        
        // resources.arsc 必须使用 STORED 方式（不压缩）
        // 否则 Android 会报错：resources.arsc in APK is compressed
        if (name.equals("resources.arsc")) {
            // 读取完整内容以计算 CRC
            byte[] content;
            try (InputStream is = zipFile.getInputStream(entry)) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                content = baos.toByteArray();
            }
            
            // 设置 STORED 方式
            newEntry.setMethod(ZipEntry.STORED);
            newEntry.setSize(content.length);
            newEntry.setCompressedSize(content.length);
            
            // 计算 CRC32
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(content);
            newEntry.setCrc(crc.getValue());
            
            zos.putNextEntry(newEntry);
            zos.write(content);
            zos.closeEntry();
            
            Log.d(TAG, "  Written resources.arsc as STORED, size: " + content.length);
            return;
        }
        
        // 其他文件保持原始的压缩方式
        newEntry.setMethod(entry.getMethod());
        if (entry.getMethod() == ZipEntry.STORED) {
            newEntry.setSize(entry.getSize());
            newEntry.setCrc(entry.getCrc());
        }
        
        zos.putNextEntry(newEntry);
        
        try (InputStream is = zipFile.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }
        
        zos.closeEntry();
    }
    
    /**
     * 判断是否是资源相关的条目
     * 
     * 注意：需要排除以下内容：
     * - META-INF/ 签名文件（会导致签名验证失败）
     * - classes.dex 代码文件（由 DexPatcher 单独处理）
     * - lib/ 原生库（不需要热更新）
     */
    private static boolean isResourceEntry(String name) {
        // 排除签名文件
        if (name.startsWith("META-INF/")) {
            return false;
        }
        // 排除 dex 文件
        if (name.endsWith(".dex")) {
            return false;
        }
        // 排除原生库
        if (name.startsWith("lib/")) {
            return false;
        }
        // 排除 Kotlin 元数据（可选）
        if (name.startsWith("kotlin/")) {
            return false;
        }
        
        // 其他所有文件都视为资源相关
        // 包括：resources.arsc, res/, assets/, AndroidManifest.xml 等
        return true;
    }
}
