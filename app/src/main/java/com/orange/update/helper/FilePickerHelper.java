package com.orange.update.helper;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 文件选择助手类
 * 统一处理文件选择逻辑，减少 MainActivity 代码重复
 */
public class FilePickerHelper {
    
    private static final String TAG = "FilePickerHelper";
    
    private final Activity activity;
    private final ActivityResultLauncher<Intent> launcher;
    private FilePickerCallback callback;
    
    /**
     * 文件选择回调接口
     */
    public interface FilePickerCallback {
        /**
         * 文件选择成功
         * @param uri 文件 URI
         * @param destFile 复制后的目标文件
         */
        void onFileSelected(Uri uri, File destFile);
        
        /**
         * 文件选择失败
         * @param message 错误信息
         */
        void onError(String message);
    }
    
    /**
     * 构造函数
     * @param activity Activity 实例
     * @param launcher ActivityResultLauncher 实例
     */
    public FilePickerHelper(Activity activity, ActivityResultLauncher<Intent> launcher) {
        this.activity = activity;
        this.launcher = launcher;
    }
    
    /**
     * 选择 APK 文件
     * @param callback 回调接口
     */
    public void pickApkFile(FilePickerCallback callback) {
        pickFile("application/vnd.android.package-archive", "选择 APK 文件", callback);
    }
    
    /**
     * 选择 ZIP 文件（补丁文件）
     * @param callback 回调接口
     */
    public void pickZipFile(FilePickerCallback callback) {
        pickFile("application/zip", "选择补丁文件", callback);
    }
    
    /**
     * 选择 JKS 文件（密钥库文件）
     * @param callback 回调接口
     */
    public void pickJksFile(FilePickerCallback callback) {
        pickFile("*/*", "选择 JKS 文件", callback);
    }
    
    /**
     * 选择文件
     * @param mimeType MIME 类型
     * @param title 选择器标题
     * @param callback 回调接口
     */
    public void pickFile(String mimeType, String title, FilePickerCallback callback) {
        this.callback = callback;
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        try {
            launcher.launch(Intent.createChooser(intent, title));
        } catch (Exception e) {
            Log.e(TAG, "无法打开文件选择器", e);
            if (callback != null) {
                callback.onError("无法打开文件选择器: " + e.getMessage());
            }
        }
    }
    
    /**
     * 处理文件选择结果
     * @param result ActivityResult
     */
    public void handleResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null && callback != null) {
                handleSelectedFile(uri);
            }
        }
    }
    
    /**
     * 处理选中的文件
     * @param uri 文件 URI
     */
    private void handleSelectedFile(Uri uri) {
        try {
            // 获取原始文件名
            String originalFileName = getFileNameFromUri(uri);
            if (originalFileName == null || originalFileName.isEmpty()) {
                originalFileName = "selected_file";
            }
            
            // 创建目标文件
            File destFile = new File(activity.getExternalFilesDir(null), originalFileName);
            
            // 复制文件
            copyFile(uri, destFile);
            
            // 回调成功
            if (callback != null) {
                callback.onFileSelected(uri, destFile);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "处理文件失败", e);
            if (callback != null) {
                callback.onError("处理文件失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 从 URI 获取文件名
     * @param uri 文件 URI
     * @return 文件名
     */
    public String getFileNameFromUri(Uri uri) {
        String fileName = null;
        
        // 尝试从 content provider 获取文件名
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            ContentResolver resolver = activity.getContentResolver();
            Cursor cursor = resolver.query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        
        // 如果从 content provider 获取失败，尝试从路径获取
        if (fileName == null || fileName.isEmpty()) {
            String path = uri.getPath();
            if (path != null) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    fileName = path.substring(lastSlash + 1);
                }
            }
        }
        
        return fileName;
    }
    
    /**
     * 复制文件
     * @param sourceUri 源文件 URI
     * @param destFile 目标文件
     * @throws Exception 复制失败
     */
    private void copyFile(Uri sourceUri, File destFile) throws Exception {
        ContentResolver resolver = activity.getContentResolver();
        InputStream inputStream = resolver.openInputStream(sourceUri);
        
        if (inputStream == null) {
            throw new Exception("无法打开输入流");
        }
        
        FileOutputStream outputStream = new FileOutputStream(destFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        outputStream.close();
        inputStream.close();
        
        Log.d(TAG, "文件复制成功: " + destFile.getAbsolutePath());
    }
    
    /**
     * 格式化文件大小
     * @param bytes 字节数
     * @return 格式化后的大小字符串
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
