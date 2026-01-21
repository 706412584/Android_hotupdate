package com.orange.update.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.orange.patchgen.android.AndroidPatchGenerator;
import com.orange.patchgen.android.StorageChecker;
import com.orange.update.HotUpdateHelper;
import com.orange.update.R;
import com.orange.update.ServerTestActivity;
import com.orange.update.helper.DialogHelper;
import com.orange.update.helper.FilePickerHelper;
import com.orange.update.helper.FormatHelper;

import java.io.File;

/**
 * 系统信息 Fragment
 * 显示系统信息、引擎状态、存储空间等
 */
public class SystemInfoFragment extends Fragment {
    
    private static final String TAG = "SystemInfoFragment";
    
    private HotUpdateHelper hotUpdateHelper;
    
    // UI 组件
    private TextView tvVersion;
    private TextView tvInfo;
    private Button btnCheckEngine;
    private Button btnCheckStorage;
    private Button btnSecurityPolicy;
    private Button btnServerTest;
    
    // JKS 配置
    private File selectedKeystoreFile;
    private String keystorePassword;
    private String keyAlias;
    private String keyPassword;
    private TextView tvJksStatus;
    
    // 文件选择器
    private ActivityResultLauncher<Intent> filePickerLauncher;
    
    // SharedPreferences
    private SharedPreferences jksPrefs;
    private static final String PREFS_JKS_CONFIG = "jks_config";
    private static final String KEY_JKS_FILE_PATH = "jks_file_path";
    private static final String KEY_KEYSTORE_PASSWORD = "keystore_password";
    private static final String KEY_KEY_ALIAS = "key_alias";
    private static final String KEY_KEY_PASSWORD = "key_password";
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hotUpdateHelper = new HotUpdateHelper(requireContext());
        
        // 初始化 SharedPreferences
        jksPrefs = requireContext().getSharedPreferences(PREFS_JKS_CONFIG, Context.MODE_PRIVATE);
        
        // 初始化文件选择器
        initFilePickerLauncher();
        
        // 加载 JKS 配置
        loadJksConfig();
    }
    
    /**
     * 初始化文件选择器
     */
    private void initFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleJksFileSelected(uri);
                    }
                }
            }
        );
    }
    
    /**
     * 处理 JKS 文件选择结果
     */
    private void handleJksFileSelected(Uri uri) {
        try {
            // 获取原始文件名
            String originalFileName = getFileNameFromUri(uri);
            if (originalFileName == null || originalFileName.isEmpty()) {
                originalFileName = "keystore.jks";
            }
            
            // 创建目标文件
            File destFile = new File(requireContext().getExternalFilesDir(null), originalFileName);
            
            // 复制文件
            copyFile(uri, destFile);
            
            // 设置选中的文件
            selectedKeystoreFile = destFile;
            
            // 更新状态显示
            updateJksStatus();
            
            Log.i(TAG, "✓ JKS 文件已选择: " + destFile.getName());
            DialogHelper.showToast(requireContext(), "✓ 已选择: " + destFile.getName());
            
        } catch (Exception e) {
            Log.e(TAG, "处理 JKS 文件失败", e);
            DialogHelper.showToast(requireContext(), "处理文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 从 URI 获取文件名
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        
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
     */
    private void copyFile(Uri sourceUri, File destFile) throws Exception {
        java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(sourceUri);
        if (inputStream == null) {
            throw new Exception("无法打开输入流");
        }
        
        java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        outputStream.close();
        inputStream.close();
        
        Log.d(TAG, "文件复制成功: " + destFile.getAbsolutePath());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_system_info, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupListeners();
        showSystemInfo();
    }
    
    private void initViews(View view) {
        tvVersion = view.findViewById(R.id.tv_version);
        tvInfo = view.findViewById(R.id.tv_info);
        btnCheckEngine = view.findViewById(R.id.btn_check_engine);
        btnCheckStorage = view.findViewById(R.id.btn_check_storage);
        btnSecurityPolicy = view.findViewById(R.id.btn_security_policy);
        btnServerTest = view.findViewById(R.id.btn_server_test);
    }
    
    private void setupListeners() {
        btnCheckEngine.setOnClickListener(v -> checkEngineAvailability());
        btnCheckStorage.setOnClickListener(v -> checkStorageSpace());
        btnSecurityPolicy.setOnClickListener(v -> showSecurityPolicyDialog());
        btnServerTest.setOnClickListener(v -> openServerTest());
    }
    
    private void showSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 系统信息 ===\n\n");
        
        // 显示热更新测试信息（用于验证 DEX 热更新）
        info.append(getHotUpdateTestInfo()).append("\n\n");
        
        info.append("应用包名: ").append(requireContext().getPackageName()).append("\n");
        
        try {
            PackageInfo pInfo = requireContext().getPackageManager()
                .getPackageInfo(requireContext().getPackageName(), 0);
            String displayVersion = hotUpdateHelper.getDisplayVersion(pInfo.versionName);
            tvVersion.setText("v" + displayVersion);
            info.append("版本: ").append(displayVersion).append("\n");
            
            // 显示热更新状态
            if (hotUpdateHelper.isPatchApplied()) {
                info.append("\n🔥 热更新状态: 已应用\n");
                info.append("补丁版本: ").append(hotUpdateHelper.getPatchedVersion()).append("\n");
                info.append("DEX 注入: ").append(hotUpdateHelper.isDexInjected() ? "✓" : "✗").append("\n");
            }
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("版本未知");
        }
        
        info.append("\n").append(hotUpdateHelper.getCompatibilityInfo()).append("\n");
        info.append("\nNative 引擎: ")
            .append(AndroidPatchGenerator.isNativeEngineAvailable() ? "✓ 可用" : "✗ 不可用")
            .append("\n");
        
        info.append("\n=== 使用说明 ===\n");
        info.append("1. 在「补丁生成」选择 APK 并生成补丁\n");
        info.append("2. 在「补丁应用」选择补丁并应用\n");
        info.append("3. 热更新后无需重启即可生效\n");
        
        tvInfo.setText(info.toString());
    }
    
    /**
     * 获取热更新测试信息 - 用于验证 DEX 热更新是否生效
     * v1.5 基准版本
     */
    private String getHotUpdateTestInfo() {
        return "🔥 热更新测试 v1.5 - 这是基准版本！";
    }
    
    private void checkEngineAvailability() {
        boolean nativeAvailable = AndroidPatchGenerator.isNativeEngineAvailable();
        
        StringBuilder info = new StringBuilder();
        info.append("=== 引擎状态 ===\n\n");
        info.append("Native 引擎: ")
            .append(nativeAvailable ? "✓ 可用 (高性能)" : "✗ 不可用")
            .append("\n");
        info.append("Java 引擎: ✓ 始终可用\n\n");
        
        if (nativeAvailable) {
            info.append("当前使用: Native 引擎\n");
            info.append("Native 引擎使用 C/C++ 实现，性能更高");
        } else {
            info.append("当前使用: Java 引擎\n");
            info.append("Java 引擎功能完整，兼容性好");
        }
        
        tvInfo.setText(info.toString());
    }
    
    private void checkStorageSpace() {
        StorageChecker checker = new StorageChecker(requireContext());
        
        long internalAvailable = checker.getInternalStorageAvailable();
        long externalAvailable = checker.getExternalStorageAvailable();
        
        StringBuilder info = new StringBuilder();
        info.append("=== 存储空间 ===\n\n");
        info.append("内部存储: ").append(FormatHelper.formatSize(internalAvailable)).append(" 可用\n");
        info.append("外部存储: ").append(FormatHelper.formatSize(externalAvailable)).append(" 可用\n\n");
        info.append("临时目录:\n").append(checker.getTempDir().getAbsolutePath());
        
        tvInfo.setText(info.toString());
    }
    
    private void openServerTest() {
        Intent intent = new Intent(requireContext(), ServerTestActivity.class);
        startActivity(intent);
    }
    
    /**
     * 加载 JKS 配置
     */
    private void loadJksConfig() {
        String jksFilePath = jksPrefs.getString(KEY_JKS_FILE_PATH, null);
        keystorePassword = jksPrefs.getString(KEY_KEYSTORE_PASSWORD, null);
        keyAlias = jksPrefs.getString(KEY_KEY_ALIAS, null);
        keyPassword = jksPrefs.getString(KEY_KEY_PASSWORD, null);
        
        if (jksFilePath != null && !jksFilePath.isEmpty()) {
            selectedKeystoreFile = new File(jksFilePath);
            if (!selectedKeystoreFile.exists()) {
                Log.w(TAG, "JKS 文件不存在，清除配置: " + jksFilePath);
                selectedKeystoreFile = null;
                // 清除无效配置
                jksPrefs.edit().remove(KEY_JKS_FILE_PATH).apply();
            } else {
                Log.d(TAG, "✓ 加载 JKS 配置: " + selectedKeystoreFile.getName());
            }
        }
    }
    
    /**
     * 保存 JKS 配置
     */
    private void saveJksConfig() {
        SharedPreferences.Editor editor = jksPrefs.edit();
        
        if (selectedKeystoreFile != null && selectedKeystoreFile.exists()) {
            editor.putString(KEY_JKS_FILE_PATH, selectedKeystoreFile.getAbsolutePath());
            Log.d(TAG, "保存 JKS 文件路径: " + selectedKeystoreFile.getAbsolutePath());
        } else {
            editor.remove(KEY_JKS_FILE_PATH);
            Log.d(TAG, "清除 JKS 文件路径");
        }
        
        if (keystorePassword != null && !keystorePassword.isEmpty()) {
            editor.putString(KEY_KEYSTORE_PASSWORD, keystorePassword);
            Log.d(TAG, "保存密钥库密码: ****");
        } else {
            editor.remove(KEY_KEYSTORE_PASSWORD);
            Log.d(TAG, "清除密钥库密码");
        }
        
        if (keyAlias != null && !keyAlias.isEmpty()) {
            editor.putString(KEY_KEY_ALIAS, keyAlias);
            Log.d(TAG, "保存密钥别名: " + keyAlias);
        } else {
            editor.remove(KEY_KEY_ALIAS);
            Log.d(TAG, "清除密钥别名");
        }
        
        if (keyPassword != null && !keyPassword.isEmpty()) {
            editor.putString(KEY_KEY_PASSWORD, keyPassword);
            Log.d(TAG, "保存密钥密码: ****");
        } else {
            editor.remove(KEY_KEY_PASSWORD);
            Log.d(TAG, "清除密钥密码");
        }
        
        editor.apply();
        Log.i(TAG, "✓ JKS 配置已保存到 SharedPreferences");
    }
    
    /**
     * 验证 JKS 配置完整性
     */
    private boolean validateJksConfig() {
        return selectedKeystoreFile != null && 
               selectedKeystoreFile.exists() &&
               keystorePassword != null && !keystorePassword.isEmpty() &&
               keyAlias != null && !keyAlias.isEmpty() &&
               keyPassword != null && !keyPassword.isEmpty();
    }
    
    /**
     * 更新 JKS 状态显示
     */
    private void updateJksStatus() {
        if (tvJksStatus != null) {
            if (selectedKeystoreFile != null && selectedKeystoreFile.exists()) {
                tvJksStatus.setText("✓ 当前签名文件: " + selectedKeystoreFile.getName());
                tvJksStatus.setTextColor(0xFF4CAF50);  // 绿色
            } else {
                tvJksStatus.setText("⚠ 未选择签名文件");
                tvJksStatus.setTextColor(0xFFFF9800);  // 橙色
            }
        }
    }
    
    private void showSecurityPolicyDialog() {
        boolean requireSignature = hotUpdateHelper.isRequireSignature();
        boolean requireEncryption = hotUpdateHelper.isRequireEncryption();
        
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 标题说明
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("配置补丁应用的安全策略：");
        tvTitle.setTextSize(14);
        tvTitle.setPadding(0, 0, 0, 20);
        layout.addView(tvTitle);
        
        // 签名验证开关
        android.widget.CheckBox cbRequireSignature = new android.widget.CheckBox(requireContext());
        cbRequireSignature.setText("🔒 强制要求 APK 签名验证");
        cbRequireSignature.setChecked(requireSignature);
        layout.addView(cbRequireSignature);
        
        TextView tvSignatureHint = new TextView(requireContext());
        tvSignatureHint.setText("  开启后，只能应用包含 APK 签名的补丁\n  验证补丁签名与应用签名是否一致");
        tvSignatureHint.setTextSize(12);
        tvSignatureHint.setTextColor(0xFF666666);
        tvSignatureHint.setPadding(0, 0, 0, 15);
        layout.addView(tvSignatureHint);
        
        // JKS 签名配置区域
        android.widget.LinearLayout jksConfigLayout = new android.widget.LinearLayout(requireContext());
        jksConfigLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        jksConfigLayout.setPadding(20, 10, 0, 15);
        jksConfigLayout.setVisibility(cbRequireSignature.isChecked() ? View.VISIBLE : View.GONE);
        layout.addView(jksConfigLayout);
        
        // 当前 JKS 文件状态显示
        tvJksStatus = new TextView(requireContext());
        updateJksStatus();
        tvJksStatus.setTextSize(13);
        tvJksStatus.setPadding(0, 0, 0, 10);
        jksConfigLayout.addView(tvJksStatus);
        
        // JKS 文件选择按钮
        Button btnSelectJks = new Button(requireContext());
        btnSelectJks.setText("📁 选择签名文件 (推荐.bks)");
        btnSelectJks.setTextSize(13);
        jksConfigLayout.addView(btnSelectJks);
        
        // 格式提示
        TextView tvFormatHint = new TextView(requireContext());
        tvFormatHint.setText("  ✓ 支持格式：PKCS12 (.p12), BKS (.bks)，JKS (.jks)");
        tvFormatHint.setTextSize(11);
        tvFormatHint.setTextColor(0xFFFF9800);  // 橙色警告
        tvFormatHint.setPadding(0, 5, 0, 10);
        jksConfigLayout.addView(tvFormatHint);
        
        // 密钥库密码输入
        TextView tvStorePasswordLabel = new TextView(requireContext());
        tvStorePasswordLabel.setText("密钥库密码 (storePassword):");
        tvStorePasswordLabel.setTextSize(12);
        tvStorePasswordLabel.setPadding(0, 10, 0, 5);
        jksConfigLayout.addView(tvStorePasswordLabel);
        
        EditText etStorePassword = new EditText(requireContext());
        etStorePassword.setHint("输入密钥库密码");
        etStorePassword.setText(keystorePassword != null ? keystorePassword : "123123");
        etStorePassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        jksConfigLayout.addView(etStorePassword);
        
        // 密钥别名输入
        TextView tvKeyAliasLabel = new TextView(requireContext());
        tvKeyAliasLabel.setText("密钥别名 (keyAlias):");
        tvKeyAliasLabel.setTextSize(12);
        tvKeyAliasLabel.setPadding(0, 10, 0, 5);
        jksConfigLayout.addView(tvKeyAliasLabel);
        
        EditText etKeyAlias = new EditText(requireContext());
        etKeyAlias.setHint("输入密钥别名");
        etKeyAlias.setText(keyAlias != null ? keyAlias : "smlieapp");
        jksConfigLayout.addView(etKeyAlias);
        
        // 密钥密码输入
        TextView tvKeyPasswordLabel = new TextView(requireContext());
        tvKeyPasswordLabel.setText("密钥密码 (keyPassword):");
        tvKeyPasswordLabel.setTextSize(12);
        tvKeyPasswordLabel.setPadding(0, 10, 0, 5);
        jksConfigLayout.addView(tvKeyPasswordLabel);
        
        EditText etKeyPassword = new EditText(requireContext());
        etKeyPassword.setHint("输入密钥密码");
        etKeyPassword.setText(keyPassword != null ? keyPassword : "123123");
        etKeyPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        jksConfigLayout.addView(etKeyPassword);
        
        // 签名验证开关变化监听
        cbRequireSignature.setOnCheckedChangeListener((buttonView, isChecked) -> {
            jksConfigLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        
        // 加密验证开关
        android.widget.CheckBox cbRequireEncryption = new android.widget.CheckBox(requireContext());
        cbRequireEncryption.setText("🔐 强制要求补丁加密");
        cbRequireEncryption.setChecked(requireEncryption);
        layout.addView(cbRequireEncryption);
        
        TextView tvEncryptionHint = new TextView(requireContext());
        tvEncryptionHint.setText("  开启后，只能应用已加密的补丁\n  支持 AES 加密和 ZIP 密码加密");
        tvEncryptionHint.setTextSize(12);
        tvEncryptionHint.setTextColor(0xFF666666);
        tvEncryptionHint.setPadding(0, 0, 0, 15);
        layout.addView(tvEncryptionHint);
        
        // 安全说明
        TextView tvNote = new TextView(requireContext());
        tvNote.setText("\n💡 安全建议：\n\n" +
            "• APK 签名验证：防止补丁被篡改，推荐开启\n" +
            "• 补丁加密：保护补丁内容，敏感应用建议开启\n" +
            "• 开发测试时可以关闭验证\n" +
            "• 修改设置后立即生效");
        tvNote.setTextSize(12);
        tvNote.setTextColor(0xFF666666);
        layout.addView(tvNote);
        
        // 创建对话框
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
            .setTitle("🛡️ 安全策略设置")
            .setView(layout)
            .setPositiveButton("保存", (d, w) -> {
                boolean newRequireSignature = cbRequireSignature.isChecked();
                boolean newRequireEncryption = cbRequireEncryption.isChecked();
                
                // 总是保存 JKS 配置（无论是否勾选签名验证）
                // 因为用户可能想配置 JKS 用于生成补丁时签名
                keystorePassword = etStorePassword.getText().toString().trim();
                keyAlias = etKeyAlias.getText().toString().trim();
                keyPassword = etKeyPassword.getText().toString().trim();
                
                // 如果勾选了签名验证，验证配置完整性
                if (newRequireSignature) {
                    if (!validateJksConfig()) {
                        DialogHelper.showToast(requireContext(), "⚠ 请完整配置 JKS 签名信息");
                        return;
                    }
                }
                
                // 保存设置到 HotUpdateHelper
                hotUpdateHelper.setRequireSignature(newRequireSignature);
                hotUpdateHelper.setRequireEncryption(newRequireEncryption);
                
                // 保存 JKS 配置到 SharedPreferences
                saveJksConfig();
                
                Log.i(TAG, "=== 保存 JKS 配置 ===");
                Log.i(TAG, "  文件: " + (selectedKeystoreFile != null ? selectedKeystoreFile.getAbsolutePath() : "null"));
                Log.i(TAG, "  密钥库密码: " + (keystorePassword != null && !keystorePassword.isEmpty() ? "已设置" : "未设置"));
                Log.i(TAG, "  密钥别名: " + keyAlias);
                Log.i(TAG, "  密钥密码: " + (keyPassword != null && !keyPassword.isEmpty() ? "已设置" : "未设置"));
                
                // 显示当前策略
                StringBuilder status = new StringBuilder("✓ 安全策略已更新\n\n");
                status.append("APK 签名验证: ").append(newRequireSignature ? "✓ 已开启" : "✗ 已关闭").append("\n");
                if (selectedKeystoreFile != null && selectedKeystoreFile.exists()) {
                    status.append("  JKS 文件: ").append(selectedKeystoreFile.getName()).append("\n");
                    status.append("  密钥别名: ").append(keyAlias).append("\n");
                } else if (newRequireSignature) {
                    status.append("  ⚠ 未配置 JKS 文件\n");
                }
                status.append("补丁加密验证: ").append(newRequireEncryption ? "✓ 已开启" : "✗ 已关闭");
                
                tvInfo.setText(status.toString());
                DialogHelper.showToast(requireContext(), "✓ 安全策略已保存");
            })
            .setNegativeButton("取消", null)
            .create();
        
        // 设置按钮点击事件，打开文件选择器
        btnSelectJks.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");  // 接受所有文件类型
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            
            try {
                filePickerLauncher.launch(Intent.createChooser(intent, "选择 JKS 文件"));
            } catch (Exception e) {
                Log.e(TAG, "无法打开文件选择器", e);
                DialogHelper.showToast(requireContext(), "无法打开文件选择器");
            }
        });
        
        dialog.show();
    }
}
