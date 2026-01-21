package com.orange.update.helper;

import android.app.ProgressDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * 对话框助手类
 * 统一处理对话框显示逻辑，减少代码重复
 */
public class DialogHelper {
    
    /**
     * 密码输入回调接口
     */
    public interface PasswordCallback {
        void onPasswordEntered(String password);
    }
    
    /**
     * 确认回调接口
     */
    public interface ConfirmCallback {
        void onConfirm();
        void onCancel();
    }
    
    /**
     * 显示密码输入对话框
     * @param context Context
     * @param title 标题
     * @param hint 提示文本
     * @param callback 回调接口
     */
    public static void showPasswordDialog(Context context, String title, String hint, 
                                         PasswordCallback callback) {
        EditText editText = new EditText(context);
        editText.setHint(hint);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("确定", (dialog, which) -> {
                String password = editText.getText().toString().trim();
                if (!password.isEmpty()) {
                    callback.onPasswordEntered(password);
                } else {
                    Toast.makeText(context, "密码不能为空", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 显示进度对话框
     * @param context Context
     * @param message 消息
     * @return ProgressDialog 实例
     */
    public static ProgressDialog showProgressDialog(Context context, String message) {
        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.show();
        return dialog;
    }
    
    /**
     * 显示确认对话框
     * @param context Context
     * @param title 标题
     * @param message 消息
     * @param callback 回调接口
     */
    public static void showConfirmDialog(Context context, String title, String message,
                                        ConfirmCallback callback) {
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", (dialog, which) -> {
                if (callback != null) {
                    callback.onConfirm();
                }
            })
            .setNegativeButton("取消", (dialog, which) -> {
                if (callback != null) {
                    callback.onCancel();
                }
            })
            .show();
    }
    
    /**
     * 显示信息对话框
     * @param context Context
     * @param title 标题
     * @param message 消息
     */
    public static void showInfoDialog(Context context, String title, String message) {
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }
    
    /**
     * 显示错误对话框
     * @param context Context
     * @param title 标题
     * @param message 错误消息
     */
    public static void showErrorDialog(Context context, String title, String message) {
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    /**
     * 显示警告对话框
     * @param context Context
     * @param title 标题
     * @param message 警告消息
     * @param callback 回调接口
     */
    public static void showWarningDialog(Context context, String title, String message,
                                        ConfirmCallback callback) {
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("继续", (dialog, which) -> {
                if (callback != null) {
                    callback.onConfirm();
                }
            })
            .setNegativeButton("取消", (dialog, which) -> {
                if (callback != null) {
                    callback.onCancel();
                }
            })
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    /**
     * 显示 Toast 消息
     * @param context Context
     * @param message 消息
     */
    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 显示长 Toast 消息
     * @param context Context
     * @param message 消息
     */
    public static void showLongToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
