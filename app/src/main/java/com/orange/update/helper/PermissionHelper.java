package com.orange.update.helper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 权限管理助手类
 * 统一处理权限请求逻辑
 */
public class PermissionHelper {
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private final Activity activity;
    private PermissionCallback callback;
    
    /**
     * 权限回调接口
     */
    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }
    
    /**
     * 构造函数
     * @param activity Activity 实例
     */
    public PermissionHelper(Activity activity) {
        this.activity = activity;
    }
    
    /**
     * 检查并请求存储权限
     * @param callback 回调接口
     */
    public void checkAndRequestStoragePermission(PermissionCallback callback) {
        this.callback = callback;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionDialog();
            } else {
                if (callback != null) {
                    callback.onPermissionGranted();
                }
            }
        } else {
            // Android 10 及以下使用传统权限
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        PERMISSION_REQUEST_CODE);
            } else {
                if (callback != null) {
                    callback.onPermissionGranted();
                }
            }
        }
    }
    
    /**
     * 显示存储权限请求对话框
     */
    private void showStoragePermissionDialog() {
        new AlertDialog.Builder(activity)
            .setTitle("需要存储权限")
            .setMessage("请授予所有文件访问权限，以便应用正常工作")
            .setPositiveButton("去设置", (d, w) -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            })
            .setNegativeButton("取消", (d, w) -> {
                if (callback != null) {
                    callback.onPermissionDenied();
                }
            })
            .show();
    }
    
    /**
     * 处理权限请求结果
     * @param requestCode 请求码
     * @param grantResults 授权结果
     */
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (callback != null) {
                    callback.onPermissionGranted();
                }
            } else {
                if (callback != null) {
                    callback.onPermissionDenied();
                }
            }
        }
    }
    
    /**
     * 检查是否有存储权限
     * @return 是否有权限
     */
    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * 检查是否有特定权限
     * @param permission 权限名称
     * @return 是否有权限
     */
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 请求特定权限
     * @param permissions 权限数组
     * @param requestCode 请求码
     */
    public void requestPermissions(String[] permissions, int requestCode) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }
}
