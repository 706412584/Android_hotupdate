package com.orange.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 补丁存储类，管理补丁文件和元数据的本地存储。
 * 
 * 存储结构：
 * /data/data/{package}/files/update/
 * ├── patches/                          # 已下载的加密补丁
 * │   ├── patch_001.enc                 # AES-256 加密的补丁文件
 * │   └── patch_002.enc
 * ├── applied/                          # 当前应用的补丁（解密后）
 * │   └── current_patch.dex
 * ├── temp/                             # 临时解密目录（使用后立即清理）
 * │   └── .nomedia
 * ├── config/
 * │   └── patch_info.json               # 补丁元数据
 * └── backup/                           # 回滚备份
 *     └── previous_patch.enc
 * 
 * 功能：
 * - 补丁目录管理
 * - 加密补丁文件读写
 * - SharedPreferences 存储补丁元数据
 * - 集成 SecurityManager 进行加密存储
 */
public class PatchStorage {
    
    private static final String TAG = "PatchStorage";
    
    // 目录名常量
    private static final String DIR_UPDATE = "update";
    private static final String DIR_PATCHES = "patches";
    private static final String DIR_APPLIED = "applied";
    private static final String DIR_TEMP = "temp";
    private static final String DIR_CONFIG = "config";
    private static final String DIR_BACKUP = "backup";

    
    // 文件名常量
    private static final String FILE_CURRENT_PATCH = "current_patch.zip";
    private static final String FILE_NOMEDIA = ".nomedia";
    private static final String FILE_EXTENSION_ENC = ".enc";
    private static final String FILE_EXTENSION_DEX = ".dex";
    
    // SharedPreferences 常量
    private static final String PREFS_NAME = "patch_storage_prefs";
    private static final String KEY_APPLIED_PATCH_ID = "applied_patch_id";
    private static final String KEY_PATCH_INFO_PREFIX = "patch_info_";
    private static final String KEY_DOWNLOADED_PATCH_IDS = "downloaded_patch_ids";
    private static final String KEY_PREVIOUS_PATCH_ID = "previous_patch_id";
    private static final String KEY_APPLIED_PATCH_HASH = "applied_patch_hash";
    private static final String KEY_TAMPER_COUNT = "tamper_count";
    private static final int MAX_TAMPER_COUNT = 3;
    
    private final Context context;
    private final SharedPreferences prefs;
    private final SecurityManager securityManager;
    private final ZipPasswordManager zipPasswordManager;
    
    // 目录缓存
    private File updateDir;
    private File patchesDir;
    private File appliedDir;
    private File tempDir;
    private File configDir;
    private File backupDir;
    
    /**
     * 创建 PatchStorage 实例
     * @param context 应用上下文
     * @throws IllegalArgumentException 如果 context 为 null
     */
    public PatchStorage(Context context) {
        this(context, null);
    }
    
    /**
     * 创建 PatchStorage 实例
     * @param context 应用上下文
     * @param securityManager 安全管理器，如果为 null 则创建新实例
     * @throws IllegalArgumentException 如果 context 为 null
     */
    public PatchStorage(Context context, SecurityManager securityManager) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.securityManager = securityManager != null ? securityManager : new SecurityManager(this.context);
        this.zipPasswordManager = new ZipPasswordManager(this.context);
        
        // 初始化目录结构
        initDirectories();
    }
    
    /**
     * 初始化目录结构
     */
    private void initDirectories() {
        // 根目录: /data/data/{package}/files/update/
        updateDir = new File(context.getFilesDir(), DIR_UPDATE);
        ensureDirectoryExists(updateDir);
        
        // 补丁目录: patches/
        patchesDir = new File(updateDir, DIR_PATCHES);
        ensureDirectoryExists(patchesDir);
        
        // 已应用补丁目录: applied/
        appliedDir = new File(updateDir, DIR_APPLIED);
        ensureDirectoryExists(appliedDir);
        
        // 临时目录: temp/
        tempDir = new File(updateDir, DIR_TEMP);
        ensureDirectoryExists(tempDir);
        createNoMediaFile(tempDir);
        
        // 配置目录: config/
        configDir = new File(updateDir, DIR_CONFIG);
        ensureDirectoryExists(configDir);
        
        // 备份目录: backup/
        backupDir = new File(updateDir, DIR_BACKUP);
        ensureDirectoryExists(backupDir);
    }
    
    /**
     * 确保目录存在
     */
    private void ensureDirectoryExists(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.w(TAG, "Failed to create directory: " + dir.getAbsolutePath());
            }
        }
    }
    
    /**
     * 创建 .nomedia 文件防止媒体扫描
     */
    private void createNoMediaFile(File dir) {
        File noMedia = new File(dir, FILE_NOMEDIA);
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile();
            } catch (IOException e) {
                Log.w(TAG, "Failed to create .nomedia file", e);
            }
        }
    }

    
    // ==================== 目录管理 ====================
    
    /**
     * 获取更新根目录
     * @return 更新根目录 /data/data/{package}/files/update/
     */
    public File getUpdateDir() {
        return updateDir;
    }
    
    /**
     * 获取补丁存储目录
     * @return 补丁目录 /data/data/{package}/files/update/patches/
     */
    public File getPatchDir() {
        return patchesDir;
    }
    
    /**
     * 获取已应用补丁目录
     * @return 已应用补丁目录 /data/data/{package}/files/update/applied/
     */
    public File getAppliedDir() {
        return appliedDir;
    }
    
    /**
     * 获取临时目录
     * @return 临时目录 /data/data/{package}/files/update/temp/
     */
    public File getTempDir() {
        return tempDir;
    }
    
    /**
     * 获取配置目录
     * @return 配置目录 /data/data/{package}/files/update/config/
     */
    public File getConfigDir() {
        return configDir;
    }
    
    /**
     * 获取备份目录
     * @return 备份目录 /data/data/{package}/files/update/backup/
     */
    public File getBackupDir() {
        return backupDir;
    }
    
    // ==================== 补丁文件管理 ====================
    
    /**
     * 获取补丁文件（加密后的）
     * @param patchId 补丁ID
     * @return 加密的补丁文件
     */
    public File getPatchFile(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            throw new IllegalArgumentException("Patch ID cannot be null or empty");
        }
        return new File(patchesDir, patchId + FILE_EXTENSION_ENC);
    }
    
    /**
     * 获取当前应用的补丁文件（解密后的）
     * @return 当前应用的补丁文件
     */
    public File getAppliedPatchFile() {
        return new File(appliedDir, FILE_CURRENT_PATCH);
    }
    
    /**
     * 获取备份的补丁文件
     * @param patchId 补丁ID
     * @return 备份的补丁文件
     */
    public File getBackupPatchFile(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            throw new IllegalArgumentException("Patch ID cannot be null or empty");
        }
        return new File(backupDir, patchId + FILE_EXTENSION_ENC);
    }
    
    /**
     * 检查补丁文件是否存在
     * @param patchId 补丁ID
     * @return 补丁文件是否存在
     */
    public boolean hasPatchFile(String patchId) {
        File patchFile = getPatchFile(patchId);
        return patchFile.exists() && patchFile.isFile();
    }

    
    // ==================== 加密补丁文件读写 ====================
    
    /**
     * 保存补丁文件（加密存储）
     * 注意：加密功能需要 API 23+ (Android 6.0+)，低版本将直接存储未加密数据
     * @param patchId 补丁ID
     * @param patchData 补丁数据（原始未加密）
     * @return 保存是否成功
     */
    public boolean savePatchFile(String patchId, byte[] patchData) {
        if (patchId == null || patchId.isEmpty()) {
            throw new IllegalArgumentException("Patch ID cannot be null or empty");
        }
        if (patchData == null) {
            throw new IllegalArgumentException("Patch data cannot be null");
        }
        
        File patchFile = getPatchFile(patchId);
        
        // 确保父目录存在
        File parentDir = patchFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(TAG, "Failed to create parent directory: " + parentDir.getAbsolutePath());
                return false;
            }
            Log.d(TAG, "Created parent directory: " + parentDir.getAbsolutePath());
        }
        
        try {
            // 加密数据 (API 23+) 或直接存储 (API 21-22)
            byte[] dataToWrite;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dataToWrite = securityManager.encrypt(patchData);
            } else {
                // API 21-22 不支持 KeyStore 加密，直接存储
                Log.w(TAG, "API level < 23, storing patch without encryption");
                dataToWrite = patchData;
            }
            
            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(patchFile)) {
                fos.write(dataToWrite);
                fos.flush();
            }
            
            // 添加到已下载列表
            addToDownloadedPatchIds(patchId);
            
            Log.d(TAG, "Saved encrypted patch file: " + patchFile.getAbsolutePath());
            return true;
            
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Failed to save patch file: " + patchId, e);
            // 清理失败的文件
            if (patchFile.exists()) {
                patchFile.delete();
            }
            return false;
        }
    }
    
    /**
     * 读取补丁文件（解密后返回）
     * 注意：解密功能需要 API 23+ (Android 6.0+)，低版本将直接读取未加密数据
     * @param patchId 补丁ID
     * @return 解密后的补丁数据，如果读取失败返回 null
     */
    public byte[] readPatchFile(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            throw new IllegalArgumentException("Patch ID cannot be null or empty");
        }
        
        File patchFile = getPatchFile(patchId);
        
        if (!patchFile.exists()) {
            Log.w(TAG, "Patch file not found: " + patchId);
            return null;
        }
        
        try {
            // 读取数据
            byte[] fileData = readFileBytes(patchFile);
            
            // 解密数据 (API 23+) 或直接返回 (API 21-22)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return securityManager.decrypt(fileData);
            } else {
                // API 21-22 不支持 KeyStore 加密，直接返回
                Log.w(TAG, "API level < 23, reading patch without decryption");
                return fileData;
            }
            
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Failed to read patch file: " + patchId, e);
            return null;
        }
    }
    
    /**
     * 从原始文件保存补丁（加密存储）
     * 注意：加密功能需要 API 23+ (Android 6.0+)，低版本将直接存储未加密数据
     * @param patchId 补丁ID
     * @param sourceFile 原始补丁文件
     * @return 保存是否成功
     */
    public boolean savePatchFromFile(String patchId, File sourceFile) {
        if (patchId == null || patchId.isEmpty()) {
            throw new IllegalArgumentException("Patch ID cannot be null or empty");
        }
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Source file does not exist");
        }
        
        try {
            File targetFile = getPatchFile(patchId);
            
            // 加密文件 (API 23+) 或直接复制 (API 21-22)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 使用 SecurityManager 加密文件
                File encryptedFile = securityManager.encryptPatch(sourceFile);
                
                // 移动到补丁目录
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                
                if (!encryptedFile.renameTo(targetFile)) {
                    // 如果重命名失败，尝试复制
                    copyFile(encryptedFile, targetFile);
                    encryptedFile.delete();
                }
            } else {
                // API 21-22 不支持 KeyStore 加密，直接复制
                Log.w(TAG, "API level < 23, storing patch without encryption");
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                copyFile(sourceFile, targetFile);
            }
            
            // 添加到已下载列表
            addToDownloadedPatchIds(patchId);
            
            Log.d(TAG, "Saved patch from file: " + patchId);
            return true;
            
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Failed to save patch from file: " + patchId, e);
            return false;
        }
    }
    
    /**
     * 解密补丁到应用目录
     * 注意：解密功能需要 API 23+ (Android 6.0+)，低版本将直接复制未加密数据
     * @param patchId 补丁ID
     * @return 解密后的补丁文件，如果失败返回 null
     */
    public File decryptPatchToApplied(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            throw new IllegalArgumentException("Patch ID cannot be null or empty");
        }
        
        File patchFile = getPatchFile(patchId);
        if (!patchFile.exists()) {
            Log.w(TAG, "Patch file not found: " + patchId);
            return null;
        }
        
        try {
            File appliedFile = getAppliedPatchFile();
            
            // 确保父目录存在
            File parentDir = appliedFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.e(TAG, "Failed to create applied directory");
                    return null;
                }
            }
            
            if (appliedFile.exists()) {
                appliedFile.delete();
            }
            
            // 解密 (API 23+) 或直接复制 (API 21-22)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 解密到临时文件
                File decryptedFile = securityManager.decryptPatch(patchFile);
                
                // 移动到应用目录
                if (!decryptedFile.renameTo(appliedFile)) {
                    // 如果重命名失败，尝试复制
                    copyFile(decryptedFile, appliedFile);
                    securityManager.secureDelete(decryptedFile);
                }
            } else {
                // API 21-22 不支持 KeyStore 加密，直接复制
                Log.w(TAG, "API level < 23, copying patch without decryption");
                copyFile(patchFile, appliedFile);
            }
            
            // 计算并保存文件哈希值（用于完整性验证）
            String hash = calculateSHA256(appliedFile);
            if (hash != null) {
                prefs.edit().putString(KEY_APPLIED_PATCH_HASH, hash).apply();
                Log.d(TAG, "Saved patch hash: " + hash.substring(0, 16) + "...");
            } else {
                Log.w(TAG, "Failed to calculate patch hash");
            }
            
            Log.d(TAG, "Prepared patch to applied directory: " + patchId);
            return appliedFile;
            
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Failed to prepare patch: " + patchId, e);
            return null;
        }
    }

    
    // ==================== 补丁信息管理（SharedPreferences） ====================
    
    /**
     * 保存补丁信息
     * @param info 补丁信息
     */
    public void savePatchInfo(PatchInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("PatchInfo cannot be null");
        }
        if (info.getPatchId() == null || info.getPatchId().isEmpty()) {
            throw new IllegalArgumentException("Patch ID cannot be null or empty");
        }
        
        String key = KEY_PATCH_INFO_PREFIX + info.getPatchId();
        String json = info.toJson();
        
        prefs.edit().putString(key, json).apply();
        Log.d(TAG, "Saved patch info: " + info.getPatchId());
    }
    
    /**
     * 获取补丁信息
     * @param patchId 补丁ID
     * @return 补丁信息，如果不存在返回 null
     */
    public PatchInfo getPatchInfo(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            return null;
        }
        
        String key = KEY_PATCH_INFO_PREFIX + patchId;
        String json = prefs.getString(key, null);
        
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        try {
            return PatchInfo.fromJson(json);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to parse patch info: " + patchId, e);
            return null;
        }
    }
    
    /**
     * 删除补丁信息
     * @param patchId 补丁ID
     */
    public void deletePatchInfo(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            return;
        }
        
        String key = KEY_PATCH_INFO_PREFIX + patchId;
        prefs.edit().remove(key).apply();
        Log.d(TAG, "Deleted patch info: " + patchId);
    }
    
    /**
     * 保存当前应用的补丁ID
     * @param patchId 补丁ID，传 null 表示清除
     */
    public void saveAppliedPatchId(String patchId) {
        SharedPreferences.Editor editor = prefs.edit();
        if (patchId == null || patchId.isEmpty()) {
            editor.remove(KEY_APPLIED_PATCH_ID);
        } else {
            editor.putString(KEY_APPLIED_PATCH_ID, patchId);
        }
        editor.apply();
        Log.d(TAG, "Saved applied patch ID: " + patchId);
    }
    
    /**
     * 获取当前应用的补丁ID
     * @return 当前应用的补丁ID，如果没有返回 null
     */
    public String getAppliedPatchId() {
        return prefs.getString(KEY_APPLIED_PATCH_ID, null);
    }
    
    /**
     * 保存上一个补丁ID（用于回滚）
     * @param patchId 补丁ID
     */
    public void savePreviousPatchId(String patchId) {
        SharedPreferences.Editor editor = prefs.edit();
        if (patchId == null || patchId.isEmpty()) {
            editor.remove(KEY_PREVIOUS_PATCH_ID);
        } else {
            editor.putString(KEY_PREVIOUS_PATCH_ID, patchId);
        }
        editor.apply();
        Log.d(TAG, "Saved previous patch ID: " + patchId);
    }
    
    /**
     * 获取上一个补丁ID
     * @return 上一个补丁ID，如果没有返回 null
     */
    public String getPreviousPatchId() {
        return prefs.getString(KEY_PREVIOUS_PATCH_ID, null);
    }
    
    /**
     * 获取当前应用的补丁信息
     * @return 当前应用的补丁信息，如果没有返回 null
     */
    public PatchInfo getAppliedPatchInfo() {
        String patchId = getAppliedPatchId();
        if (patchId == null) {
            return null;
        }
        return getPatchInfo(patchId);
    }

    
    // ==================== 已下载补丁列表管理 ====================
    
    /**
     * 添加补丁ID到已下载列表
     * @param patchId 补丁ID
     */
    private void addToDownloadedPatchIds(String patchId) {
        Set<String> patchIds = getDownloadedPatchIds();
        patchIds.add(patchId);
        saveDownloadedPatchIds(patchIds);
    }
    
    /**
     * 从已下载列表移除补丁ID
     * @param patchId 补丁ID
     */
    private void removeFromDownloadedPatchIds(String patchId) {
        Set<String> patchIds = getDownloadedPatchIds();
        patchIds.remove(patchId);
        saveDownloadedPatchIds(patchIds);
    }
    
    /**
     * 获取已下载的补丁ID列表
     * @return 补丁ID集合
     */
    private Set<String> getDownloadedPatchIds() {
        String json = prefs.getString(KEY_DOWNLOADED_PATCH_IDS, null);
        Set<String> patchIds = new HashSet<>();
        
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    patchIds.add(array.getString(i));
                }
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse downloaded patch IDs", e);
            }
        }
        
        return patchIds;
    }
    
    /**
     * 保存已下载的补丁ID列表
     * @param patchIds 补丁ID集合
     */
    private void saveDownloadedPatchIds(Set<String> patchIds) {
        JSONArray array = new JSONArray();
        for (String patchId : patchIds) {
            array.put(patchId);
        }
        prefs.edit().putString(KEY_DOWNLOADED_PATCH_IDS, array.toString()).apply();
    }
    
    /**
     * 获取已下载的补丁列表
     * @return 补丁信息列表
     */
    public List<PatchInfo> getDownloadedPatches() {
        Set<String> patchIds = getDownloadedPatchIds();
        List<PatchInfo> patches = new ArrayList<>();
        
        for (String patchId : patchIds) {
            // 验证文件是否存在
            if (hasPatchFile(patchId)) {
                PatchInfo info = getPatchInfo(patchId);
                if (info != null) {
                    patches.add(info);
                }
            } else {
                // 文件不存在，从列表中移除
                removeFromDownloadedPatchIds(patchId);
            }
        }
        
        // 按创建时间排序（最新的在前）
        Collections.sort(patches, new Comparator<PatchInfo>() {
            @Override
            public int compare(PatchInfo p1, PatchInfo p2) {
                return Long.compare(p2.getCreateTime(), p1.getCreateTime());
            }
        });
        
        return patches;
    }
    
    /**
     * 获取未应用的已下载补丁列表
     * @return 未应用的补丁信息列表
     */
    public List<PatchInfo> getPendingPatches() {
        String appliedPatchId = getAppliedPatchId();
        List<PatchInfo> allPatches = getDownloadedPatches();
        List<PatchInfo> pendingPatches = new ArrayList<>();
        
        for (PatchInfo patch : allPatches) {
            if (!patch.getPatchId().equals(appliedPatchId)) {
                pendingPatches.add(patch);
            }
        }
        
        return pendingPatches;
    }

    
    // ==================== 补丁删除和清理 ====================
    
    /**
     * 删除补丁
     * @param patchId 补丁ID
     * @return 是否删除成功
     */
    public boolean deletePatch(String patchId) {
        if (patchId == null || patchId.isEmpty()) {
            return false;
        }
        
        boolean success = true;
        
        // 删除补丁文件
        File patchFile = getPatchFile(patchId);
        if (patchFile.exists()) {
            success = securityManager.secureDelete(patchFile);
        }
        
        // 删除补丁信息
        deletePatchInfo(patchId);
        
        // 从已下载列表移除
        removeFromDownloadedPatchIds(patchId);
        
        // 如果是当前应用的补丁，清除应用状态
        String appliedPatchId = getAppliedPatchId();
        if (patchId.equals(appliedPatchId)) {
            saveAppliedPatchId(null);
            // 删除已应用的补丁文件
            File appliedFile = getAppliedPatchFile();
            if (appliedFile.exists()) {
                securityManager.secureDelete(appliedFile);
            }
        }
        
        Log.d(TAG, "Deleted patch: " + patchId + ", success: " + success);
        return success;
    }
    
    /**
     * 清理过期补丁，保留最新的 N 个
     * @param keepCount 保留的补丁数量
     * @return 清理的补丁数量
     */
    public int cleanOldPatches(int keepCount) {
        if (keepCount < 0) {
            keepCount = 0;
        }
        
        List<PatchInfo> patches = getDownloadedPatches();
        String appliedPatchId = getAppliedPatchId();
        
        int cleanedCount = 0;
        
        // 跳过前 keepCount 个（最新的）
        for (int i = keepCount; i < patches.size(); i++) {
            PatchInfo patch = patches.get(i);
            
            // 不删除当前应用的补丁
            if (patch.getPatchId().equals(appliedPatchId)) {
                continue;
            }
            
            if (deletePatch(patch.getPatchId())) {
                cleanedCount++;
            }
        }
        
        Log.d(TAG, "Cleaned " + cleanedCount + " old patches, kept " + keepCount);
        return cleanedCount;
    }
    
    /**
     * 清理临时目录
     * @return 是否清理成功
     */
    public boolean cleanTempDirectory() {
        return securityManager.cleanTempDirectory(tempDir);
    }
    
    /**
     * 清理所有数据（补丁文件、元数据、配置）
     * @return 是否清理成功
     */
    public boolean clearAll() {
        boolean success = true;
        
        // 清理所有补丁文件
        success &= securityManager.secureDelete(patchesDir);
        success &= securityManager.secureDelete(appliedDir);
        success &= securityManager.secureDelete(tempDir);
        success &= securityManager.secureDelete(backupDir);
        
        // 清理 SharedPreferences
        prefs.edit().clear().apply();
        
        // 重新初始化目录
        initDirectories();
        
        Log.d(TAG, "Cleared all data, success: " + success);
        return success;
    }

    
    // ==================== 备份和回滚支持 ====================
    
    /**
     * 备份当前应用的补丁
     * @return 是否备份成功
     */
    public boolean backupCurrentPatch() {
        String appliedPatchId = getAppliedPatchId();
        if (appliedPatchId == null) {
            Log.d(TAG, "No applied patch to backup");
            return true;
        }
        
        File sourcePatchFile = getPatchFile(appliedPatchId);
        if (!sourcePatchFile.exists()) {
            Log.w(TAG, "Applied patch file not found: " + appliedPatchId);
            return false;
        }
        
        try {
            File backupFile = getBackupPatchFile(appliedPatchId);
            copyFile(sourcePatchFile, backupFile);
            savePreviousPatchId(appliedPatchId);
            Log.d(TAG, "Backed up patch: " + appliedPatchId);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to backup patch: " + appliedPatchId, e);
            return false;
        }
    }
    
    /**
     * 恢复备份的补丁
     * @return 恢复的补丁ID，如果失败返回 null
     */
    public String restoreBackupPatch() {
        String previousPatchId = getPreviousPatchId();
        if (previousPatchId == null) {
            Log.d(TAG, "No backup patch to restore");
            return null;
        }
        
        File backupFile = getBackupPatchFile(previousPatchId);
        if (!backupFile.exists()) {
            Log.w(TAG, "Backup patch file not found: " + previousPatchId);
            savePreviousPatchId(null);
            return null;
        }
        
        try {
            // 恢复到补丁目录
            File targetFile = getPatchFile(previousPatchId);
            if (!targetFile.exists()) {
                copyFile(backupFile, targetFile);
                addToDownloadedPatchIds(previousPatchId);
            }
            
            // 解密到应用目录
            File appliedFile = decryptPatchToApplied(previousPatchId);
            if (appliedFile != null) {
                saveAppliedPatchId(previousPatchId);
                Log.d(TAG, "Restored backup patch: " + previousPatchId);
                return previousPatchId;
            }
            
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Failed to restore backup patch: " + previousPatchId, e);
            return null;
        }
    }
    
    /**
     * 清理备份
     */
    public void clearBackup() {
        securityManager.secureDelete(backupDir);
        ensureDirectoryExists(backupDir);
        savePreviousPatchId(null);
        Log.d(TAG, "Cleared backup");
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
     * 复制文件
     */
    private void copyFile(File source, File target) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();
        }
    }
    
    /**
     * 获取存储空间使用情况
     * @return 已使用的存储空间（字节）
     */
    public long getStorageUsage() {
        return calculateDirectorySize(updateDir);
    }
    
    /**
     * 计算目录大小
     */
    private long calculateDirectorySize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }
    
    // ==================== 补丁完整性验证 ====================
    
    /**
     * 计算文件的 SHA-256 哈希值
     * @param file 文件
     * @return SHA-256 哈希值（十六进制字符串），如果失败返回 null
     */
    private String calculateSHA256(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            
            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to calculate SHA-256", e);
            return null;
        }
    }
    
    /**
     * 验证已应用补丁的完整性
     * 
     * 验证流程：
     * 1. ZIP 密码验证（如果是加密 ZIP）
     * 2. SHA-256 哈希验证
     * 
     * @return 验证是否通过
     */
    public boolean verifyAppliedPatchIntegrity() {
        File appliedFile = getAppliedPatchFile();
        if (!appliedFile.exists()) {
            Log.d(TAG, "No applied patch file to verify");
            return false;
        }
        
        // 第 1 层验证：ZIP 密码验证（如果是加密 ZIP）
        if (!verifyZipPassword(appliedFile)) {
            Log.e(TAG, "⚠️ ZIP password verification failed!");
            return false;
        }
        
        // 第 2 层验证：SHA-256 哈希验证
        String savedHash = prefs.getString(KEY_APPLIED_PATCH_HASH, null);
        if (savedHash == null || savedHash.isEmpty()) {
            Log.w(TAG, "No saved hash found, patch may be from old version (backward compatible)");
            return true; // 向后兼容：旧版本没有哈希值
        }
        
        String currentHash = calculateSHA256(appliedFile);
        if (currentHash == null) {
            Log.e(TAG, "Failed to calculate current hash");
            return false;
        }
        
        boolean valid = savedHash.equals(currentHash);
        
        if (valid) {
            Log.d(TAG, "✅ Patch integrity verified (ZIP + SHA-256): " + currentHash.substring(0, 16) + "...");
        } else {
            Log.e(TAG, "⚠️ PATCH INTEGRITY CHECK FAILED!");
            Log.e(TAG, "Expected: " + savedHash);
            Log.e(TAG, "Actual:   " + currentHash);
            Log.e(TAG, "File may have been tampered with!");
        }
        
        return valid;
    }
    
    /**
     * 验证 ZIP 密码
     * 
     * @param zipFile ZIP 文件
     * @return 验证是否通过
     */
    // 已移动到文件末尾，避免重复定义
    
    /**
     * 验证并恢复补丁（如果检测到篡改）
     * @return 验证是否通过或恢复是否成功
     */
    public boolean verifyAndRecoverPatch() {
        // 1. 验证完整性
        if (verifyAppliedPatchIntegrity()) {
            // 完整性正常，重置篡改计数
            prefs.edit().putInt(KEY_TAMPER_COUNT, 0).apply();
            return true;
        }
        
        // 2. 检测到篡改
        int tamperCount = prefs.getInt(KEY_TAMPER_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_TAMPER_COUNT, tamperCount).apply();
        
        Log.e(TAG, "⚠️ Patch tampered! Attempt count: " + tamperCount + "/" + MAX_TAMPER_COUNT);
        
        // 3. 超过阈值，清除补丁
        if (tamperCount >= MAX_TAMPER_COUNT) {
            Log.e(TAG, "⚠️ Too many tamper attempts (" + tamperCount + "), clearing patch for security");
            
            String appliedPatchId = getAppliedPatchId();
            saveAppliedPatchId(null);
            prefs.edit().remove(KEY_APPLIED_PATCH_HASH).apply();
            
            File appliedFile = getAppliedPatchFile();
            if (appliedFile.exists()) {
                securityManager.secureDelete(appliedFile);
            }
            
            // 上报篡改尝试（可选）
            reportTamperAttempt(appliedPatchId, tamperCount);
            
            return false;
        }
        
        // 4. 尝试从加密存储中恢复
        String appliedPatchId = getAppliedPatchId();
        if (appliedPatchId != null && !appliedPatchId.isEmpty()) {
            Log.i(TAG, "Attempting to recover patch from encrypted storage...");
            
            File recoveredFile = decryptPatchToApplied(appliedPatchId);
            
            if (recoveredFile != null && verifyAppliedPatchIntegrity()) {
                Log.i(TAG, "✅ Patch recovered successfully from encrypted storage");
                // 重置篡改计数
                prefs.edit().putInt(KEY_TAMPER_COUNT, 0).apply();
                return true;
            } else {
                Log.e(TAG, "❌ Failed to recover patch from encrypted storage");
            }
        }
        
        return false;
    }
    
    /**
     * 上报篡改尝试（可选实现）
     * @param patchId 补丁ID
     * @param attemptCount 尝试次数
     */
    private void reportTamperAttempt(String patchId, int attemptCount) {
        // TODO: 实现上报逻辑，发送到服务器进行安全分析
        Log.e(TAG, "Reporting tamper attempt to server: patchId=" + patchId + ", attempts=" + attemptCount);
        
        // 示例：可以通过 UpdateManager 或其他方式上报
        // UpdateManager.getInstance().reportSecurityEvent("patch_tampered", patchId, attemptCount);
    }
    
    /**
     * 获取篡改尝试次数
     * @return 篡改尝试次数
     */
    public int getTamperAttemptCount() {
        return prefs.getInt(KEY_TAMPER_COUNT, 0);
    }
    
    /**
     * 重置篡改计数
     */
    public void resetTamperCount() {
        prefs.edit().putInt(KEY_TAMPER_COUNT, 0).apply();
        Log.d(TAG, "Reset tamper count");
    }
    
    /**
     * 验证 ZIP 密码
     * 
     * @param zipFile ZIP 文件
     * @return 验证是否通过
     */
    private boolean verifyZipPassword(File zipFile) {
        // 检查是否是加密的 ZIP
        if (!zipPasswordManager.isEncrypted(zipFile)) {
            Log.d(TAG, "ZIP is not encrypted (backward compatible)");
            return true; // 向后兼容：未加密的 ZIP
        }
        
        Log.d(TAG, "Verifying ZIP password...");
        String zipPassword = zipPasswordManager.getZipPassword();
        
        boolean valid = zipPasswordManager.verifyPassword(zipFile, zipPassword);
        
        if (valid) {
            Log.d(TAG, "✅ ZIP password verified");
        } else {
            Log.e(TAG, "⚠️ ZIP PASSWORD VERIFICATION FAILED!");
            Log.e(TAG, "ZIP file may have been tampered with or password is incorrect!");
        }
        
        return valid;
    }
    
    /**
     * 获取 ZipPasswordManager 实例
     * @return ZipPasswordManager 实例
     */
    public ZipPasswordManager getZipPasswordManager() {
        return zipPasswordManager;
    }
    
    /**
     * 获取 SecurityManager 实例
     * @return SecurityManager 实例
     */
    public SecurityManager getSecurityManager() {
        return securityManager;
    }
}
