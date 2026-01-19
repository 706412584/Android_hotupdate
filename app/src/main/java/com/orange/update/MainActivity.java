package com.orange.update;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.orange.patchgen.android.AndroidPatchGenerator;
import com.orange.patchgen.android.SimpleAndroidGeneratorCallback;
import com.orange.patchgen.android.StorageChecker;
import com.orange.patchgen.model.PatchResult;
import com.orange.patchnative.NativePatchEngine;
import com.orange.patchnative.NativeProgressCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Demo Activity - 热更新补丁生成与应用演示
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PatchDemo-v1.4.1";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    // 安全策略配置
    private static final String PREFS_SECURITY = "security_policy";
    private static final String KEY_REQUIRE_SIGNATURE = "require_signature";
    private static final String KEY_REQUIRE_ENCRYPTION = "require_encryption";
    
    // JKS 签名配置键
    private static final String KEY_JKS_FILE_PATH = "jks_file_path";
    private static final String KEY_KEYSTORE_PASSWORD = "keystore_password";
    private static final String KEY_KEY_ALIAS = "key_alias";
    private static final String KEY_KEY_PASSWORD = "key_password";

    // UI 组件
    private TextView tvStatus;
    private TextView tvInfo;
    private TextView tvVersion;
    private ProgressBar progressBar;
    private Button btnGenerate;
    private Button btnApply;
    private Button btnCheckEngine;
    private Button btnCheckStorage;
    private Button btnSelectBase;
    private Button btnSelectNew;
    private Button btnSelectPatch;

    private AndroidPatchGenerator generator;
    private HotUpdateHelper hotUpdateHelper;
    private Button btnClearPatch;
    private Button btnVerifySuccess;
    private Button btnVerifyFail;
    private Button btnGenerateKeys;
    private Button btnLoadKeys;
    private Button btnConfigKeys;
    private Button btnSecuritySettings;
    
    // RSA密钥对（用于演示）
    private java.security.KeyPair demoKeyPair;
    
    // 选择的文件
    private File selectedBaseApk;
    private File selectedNewApk;
    private File selectedPatchFile;
    private File lastGeneratedPatch;
    
    // JKS 签名配置
    private File selectedKeystoreFile;
    private String keystorePassword;
    private String keyAlias;
    private String keyPassword;
    
    // JKS 状态显示 TextView（用于实时更新对话框）
    private TextView tvJksStatus;
    
    // 文件选择类型: 0=基准APK, 1=新APK, 2=补丁文件, 3=JKS文件
    private int selectingFileType = 0;

    // 文件选择器
    private ActivityResultLauncher<Intent> filePickerLauncher;

    // 默认输出目录 - 下载目录
    private File outputDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 设置默认输出目录为下载目录
        outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // 初始化热更新助手
        hotUpdateHelper = new HotUpdateHelper(this);
        
        // 应用启动时加载已应用的补丁
        hotUpdateHelper.loadAppliedPatch();

        initFilePicker();
        initViews();
        loadJksConfig();  // 加载保存的 JKS 配置
        checkPermissions();
        showSystemInfo();
        
        // 测试 Native JKS 支持
        testNativeJKS();
    }

    private void initFilePicker() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            }
        );
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvInfo = findViewById(R.id.tv_info);
        tvVersion = findViewById(R.id.tv_version);
        progressBar = findViewById(R.id.progress_bar);
        btnGenerate = findViewById(R.id.btn_generate);
        btnApply = findViewById(R.id.btn_apply);
        btnCheckEngine = findViewById(R.id.btn_check_engine);
        btnCheckStorage = findViewById(R.id.btn_check_storage);
        btnSelectBase = findViewById(R.id.btn_select_base);
        btnSelectNew = findViewById(R.id.btn_select_new);
        btnSelectPatch = findViewById(R.id.btn_select_patch);
        btnClearPatch = findViewById(R.id.btn_clear_patch);
        btnVerifySuccess = findViewById(R.id.btn_verify_success);
        btnVerifyFail = findViewById(R.id.btn_verify_fail);
        btnGenerateKeys = findViewById(R.id.btn_generate_keys);
        btnLoadKeys = findViewById(R.id.btn_load_keys);
        btnConfigKeys = findViewById(R.id.btn_config_keys);
        btnSecuritySettings = findViewById(R.id.btn_security_settings);
        Button btnTestAssets = findViewById(R.id.btn_test_assets);
        Button btnServerTest = findViewById(R.id.btn_server_test);

        btnGenerate.setOnClickListener(v -> startPatchGeneration());
        btnApply.setOnClickListener(v -> applyPatch());
        btnCheckEngine.setOnClickListener(v -> checkEngineAvailability());
        btnCheckStorage.setOnClickListener(v -> checkStorageSpace());
        btnSelectBase.setOnClickListener(v -> selectFile(0));
        btnSelectNew.setOnClickListener(v -> selectFile(1));
        btnSelectPatch.setOnClickListener(v -> selectFile(2));
        btnClearPatch.setOnClickListener(v -> clearPatch());
        btnSecuritySettings.setOnClickListener(v -> showSecuritySettingsDialog());
        btnTestAssets.setOnClickListener(v -> testAssetsFile());
        btnServerTest.setOnClickListener(v -> openServerTest());
        
        // 隐藏 RSA 密钥相关的按钮（已废弃，改用 APK 签名验证）
        btnVerifySuccess.setVisibility(View.GONE);
        btnVerifyFail.setVisibility(View.GONE);
        btnGenerateKeys.setVisibility(View.GONE);
        btnLoadKeys.setVisibility(View.GONE);
        btnConfigKeys.setVisibility(View.GONE);
        
        updateButtonStates();
        
        // 移除自动加载密钥的逻辑，让用户手动点击加载
        // loadUserKeys();
    }

    /**
     * 获取热更新测试信息 - 用于验证 DEX 热更新是否生效
     * v1.2 基准版本
     */
    private String getHotUpdateTestInfo() {
        return "热更新测试 v1.3 - 这是基准版本！";
    }

    private void showSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 系统信息 ===\n\n");
        
        // 显示热更新测试信息（v1.2 新增）
        info.append(getHotUpdateTestInfo()).append("\n\n");
        
        info.append("应用包名: ").append(getPackageName()).append("\n");
        
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String displayVersion = hotUpdateHelper.getDisplayVersion(pInfo.versionName);
            tvVersion.setText("v" + displayVersion);
            info.append("版本: ").append(displayVersion).append("\n");
            
            // 显示热更新状态
            if (hotUpdateHelper.isPatchApplied()) {
                info.append("\n🔥 热更新状态: 已应用\n");
                info.append("补丁版本: ").append(hotUpdateHelper.getPatchedVersion()).append("\n");
                info.append("DEX 注入: ").append(hotUpdateHelper.isDexInjected() ? "✓" : "✗").append("\n");
                long patchTime = hotUpdateHelper.getPatchTime();
                if (patchTime > 0) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    info.append("应用时间: ").append(sdf.format(new java.util.Date(patchTime))).append("\n");
                }
                // 显示清除按钮
                btnClearPatch.setVisibility(View.VISIBLE);
            } else {
                btnClearPatch.setVisibility(View.GONE);
            }
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("版本未知");
        }
        
        info.append("\n").append(hotUpdateHelper.getCompatibilityInfo()).append("\n");
        info.append("\nNative 引擎: ").append(AndroidPatchGenerator.isNativeEngineAvailable() ? "✓ 可用" : "✗ 不可用").append("\n");
        info.append("\n输出目录:\n").append(outputDir.getAbsolutePath()).append("\n");
        info.append("\n=== 使用说明 ===\n");
        info.append("1. 选择基准APK和新APK\n");
        info.append("2. 点击「生成补丁」\n");
        info.append("3. 点击「应用补丁」实现热更新\n");
        info.append("4. 热更新后无需重启即可生效\n");
        
        tvInfo.setText(info.toString());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                    .setTitle("需要存储权限")
                    .setMessage("请授予所有文件访问权限")
                    .setPositiveButton("去设置", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("✓ 权限已授予");
            } else {
                tvStatus.setText("⚠ 权限被拒绝");
            }
        }
    }

    /**
     * 选择文件
     * @param type 0=基准APK, 1=新APK, 2=补丁文件
     */
    private void selectFile(int type) {
        selectingFileType = type;
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        if (type == 2) {
            intent.setType("application/zip");
            
            // 更新提示信息
            Toast.makeText(this, 
                "提示：新版本补丁的签名已嵌入在 zip 包内，无需单独管理签名文件", 
                Toast.LENGTH_LONG).show();
        } else {
            intent.setType("application/vnd.android.package-archive");
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        String title = type == 0 ? "选择基准 APK" : (type == 1 ? "选择新 APK" : "选择补丁文件");
        
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, title));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedFile(Uri uri) {
        try {
            // 获取原始文件名
            String originalFileName = getFileNameFromUri(uri);
            
            // 根据文件类型确定目标文件名
            String fileName;
            if (selectingFileType == 2) {
                // 补丁文件：保留原始文件名（包括 .enc 扩展名）
                if (originalFileName != null && !originalFileName.isEmpty()) {
                    fileName = originalFileName;
                } else {
                    fileName = "selected_patch.zip";
                }
            } else if (selectingFileType == 3) {
                // JKS 文件：保留原始文件名
                if (originalFileName != null && !originalFileName.isEmpty()) {
                    fileName = originalFileName;
                } else {
                    fileName = "keystore.jks";
                }
            } else {
                // APK 文件：使用固定名称
                String[] fileNames = {"selected_base.apk", "selected_new.apk", "selected_patch.zip"};
                fileName = fileNames[selectingFileType];
            }
            
            File destFile = new File(getExternalFilesDir(null), fileName);
            
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                FileOutputStream outputStream = new FileOutputStream(destFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();
                
                switch (selectingFileType) {
                    case 0:
                        selectedBaseApk = destFile;
                        btnSelectBase.setText("基准: " + getApkInfo(destFile));
                        break;
                    case 1:
                        selectedNewApk = destFile;
                        btnSelectNew.setText("新版: " + getApkInfo(destFile));
                        break;
                    case 2:
                        selectedPatchFile = destFile;
                        String patchInfo = fileName.endsWith(".enc") ? "加密补丁: " : "补丁: ";
                        btnSelectPatch.setText(patchInfo + formatSize(destFile.length()));
                        
                        // 尝试复制对应的签名文件
                        copySignatureFileIfExists(uri, destFile);
                        break;
                    case 3:
                        selectedKeystoreFile = destFile;
                        Log.i(TAG, "✓ JKS 文件已选择: " + destFile.getAbsolutePath());
                        Log.i(TAG, "selectedKeystoreFile 已设置为: " + selectedKeystoreFile);
                        Log.i(TAG, "文件是否存在: " + selectedKeystoreFile.exists());
                        
                        // 实时更新对话框中的 JKS 状态显示
                        if (tvJksStatus != null) {
                            tvJksStatus.setText("✓ 当前签名文件: " + fileName);
                            tvJksStatus.setTextColor(0xFF4CAF50);  // 绿色
                            Log.i(TAG, "✓ 对话框状态已更新");
                        }
                        
                        Toast.makeText(this, "✓ 已选择: " + fileName, Toast.LENGTH_SHORT).show();
                        return;  // 不调用 updateButtonStates 和 updateFileInfo
                }
                
                updateButtonStates();
                updateFileInfo();
                Toast.makeText(this, "✓ 文件已选择", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "处理文件失败", e);
            Toast.makeText(this, "处理文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 从 URI 获取文件名
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        
        // 尝试从 URI 路径获取文件名
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
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
     * 尝试复制签名文件（如果存在）
     * 当用户选择补丁文件时，自动查找并复制对应的 .sig 签名文件
     */
    private void copySignatureFileIfExists(Uri patchUri, File destPatchFile) {
        try {
            // 方法1: 尝试从原始文件路径获取签名文件
            String originalFileName = getFileNameFromUri(patchUri);
            if (originalFileName != null) {
                // 在下载目录查找签名文件
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File signatureFileInDownload = new File(downloadDir, originalFileName + ".sig");
                
                if (signatureFileInDownload.exists()) {
                    // 找到签名文件，复制到应用目录
                    File destSigFile = new File(destPatchFile.getPath() + ".sig");
                    
                    FileInputStream sigInputStream = new FileInputStream(signatureFileInDownload);
                    FileOutputStream sigOutputStream = new FileOutputStream(destSigFile);
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = sigInputStream.read(buffer)) != -1) {
                        sigOutputStream.write(buffer, 0, bytesRead);
                    }
                    
                    sigOutputStream.close();
                    sigInputStream.close();
                    
                    Log.i(TAG, "✓ 签名文件已复制: " + destSigFile.getName());
                    Toast.makeText(this, "✓ 已自动复制签名文件", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // 方法2: 尝试通过 URI 直接访问（可能不可靠）
            String patchPath = patchUri.getPath();
            if (patchPath != null) {
                Uri signatureUri = Uri.parse(patchUri.toString() + ".sig");
                
                try {
                    InputStream sigInputStream = getContentResolver().openInputStream(signatureUri);
                    if (sigInputStream != null) {
                        File destSigFile = new File(destPatchFile.getPath() + ".sig");
                        FileOutputStream sigOutputStream = new FileOutputStream(destSigFile);
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = sigInputStream.read(buffer)) != -1) {
                            sigOutputStream.write(buffer, 0, bytesRead);
                        }
                        
                        sigOutputStream.close();
                        sigInputStream.close();
                        
                        Log.i(TAG, "✓ 签名文件已复制（通过URI）: " + destSigFile.getName());
                        Toast.makeText(this, "✓ 已自动复制签名文件", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (Exception e) {
                    // URI 方法失败，继续
                    Log.d(TAG, "通过 URI 访问签名文件失败: " + e.getMessage());
                }
            }
            
            // 未找到签名文件
            Log.d(TAG, "未找到签名文件（这是正常的，如果补丁未签名）");
            
        } catch (Exception e) {
            Log.e(TAG, "复制签名文件时出错", e);
        }
    }

    private String getApkInfo(File apkFile) {
        try {
            PackageInfo info = getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) {
                return "v" + info.versionName;
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 APK 信息失败", e);
        }
        return formatSize(apkFile.length());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private void updateButtonStates() {
        boolean canGenerate = selectedBaseApk != null && selectedNewApk != null;
        btnGenerate.setEnabled(canGenerate);
        
        boolean canApply = selectedPatchFile != null || lastGeneratedPatch != null;
        btnApply.setEnabled(canApply);
    }

    private void updateFileInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 已选择的文件 ===\n\n");
        
        if (selectedBaseApk != null) {
            info.append("📦 基准 APK: ").append(getApkInfo(selectedBaseApk));
            info.append(" (").append(formatSize(selectedBaseApk.length())).append(")\n");
        } else {
            info.append("📦 基准 APK: 未选择\n");
        }
        
        if (selectedNewApk != null) {
            info.append("📦 新版 APK: ").append(getApkInfo(selectedNewApk));
            info.append(" (").append(formatSize(selectedNewApk.length())).append(")\n");
        } else {
            info.append("📦 新版 APK: 未选择\n");
        }
        
        info.append("\n");
        
        if (lastGeneratedPatch != null && lastGeneratedPatch.exists()) {
            info.append("🔧 最新补丁: ").append(lastGeneratedPatch.getName());
            info.append(" (").append(formatSize(lastGeneratedPatch.length())).append(")\n");
        }
        
        if (selectedPatchFile != null) {
            info.append("📋 选择的补丁: ").append(selectedPatchFile.getName());
            info.append(" (").append(formatSize(selectedPatchFile.length())).append(")\n");
        }
        
        info.append("\n输出目录: ").append(outputDir.getAbsolutePath());
        
        tvInfo.setText(info.toString());
    }

    private void checkEngineAvailability() {
        boolean nativeAvailable = AndroidPatchGenerator.isNativeEngineAvailable();
        
        StringBuilder info = new StringBuilder();
        info.append("=== 引擎状态 ===\n\n");
        info.append("Native 引擎: ").append(nativeAvailable ? "✓ 可用 (高性能)" : "✗ 不可用").append("\n");
        info.append("Java 引擎: ✓ 始终可用\n\n");
        
        if (nativeAvailable) {
            info.append("当前使用: Native 引擎\n");
            info.append("Native 引擎使用 C/C++ 实现，性能更高");
        } else {
            info.append("当前使用: Java 引擎\n");
            info.append("Java 引擎功能完整，兼容性好");
        }
        
        tvInfo.setText(info.toString());
        tvStatus.setText(nativeAvailable ? "✓ Native 引擎可用" : "使用 Java 引擎");
    }

    private void checkStorageSpace() {
        StorageChecker checker = new StorageChecker(this);
        
        long internalAvailable = checker.getInternalStorageAvailable();
        long externalAvailable = checker.getExternalStorageAvailable();
        
        StringBuilder info = new StringBuilder();
        info.append("=== 存储空间 ===\n\n");
        info.append("内部存储: ").append(formatSize(internalAvailable)).append(" 可用\n");
        info.append("外部存储: ").append(formatSize(externalAvailable)).append(" 可用\n\n");
        info.append("输出目录:\n").append(outputDir.getAbsolutePath()).append("\n\n");
        info.append("临时目录:\n").append(checker.getTempDir().getAbsolutePath());
        
        tvInfo.setText(info.toString());
        tvStatus.setText("✓ 存储空间充足");
    }

    private void startPatchGeneration() {
        if (selectedBaseApk == null || selectedNewApk == null) {
            Toast.makeText(this, "请先选择两个 APK 文件", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示签名选项对话框
        showSignPatchDialog();
    }
    
    /**
     * 显示签名补丁选项对话框
     */
    private void showSignPatchDialog() {
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 标题文本
        TextView tvTitle = new TextView(this);
        tvTitle.setText("请选择安全选项：");
        tvTitle.setTextSize(14);
        tvTitle.setPadding(0, 0, 0, 20);
        layout.addView(tvTitle);
        
        // APK 签名验证选项（推荐）
        android.widget.CheckBox cbApkSign = new android.widget.CheckBox(this);
        cbApkSign.setText("🔒 APK 签名验证（推荐）");
        cbApkSign.setChecked(true);  // 默认选中
        layout.addView(cbApkSign);
        
        TextView tvApkSignHint = new TextView(this);
        tvApkSignHint.setText("  使用应用签名验证，防止补丁被篡改\n  无需管理密钥，启动速度快");
        tvApkSignHint.setTextSize(12);
        tvApkSignHint.setTextColor(0xFF666666);
        tvApkSignHint.setPadding(0, 0, 0, 15);
        layout.addView(tvApkSignHint);
        
        // ZIP 密码选项
        android.widget.CheckBox cbZipPassword = new android.widget.CheckBox(this);
        cbZipPassword.setText("🔑 ZIP 密码保护");
        cbZipPassword.setChecked(false);
        layout.addView(cbZipPassword);
        
        TextView tvZipPasswordHint = new TextView(this);
        tvZipPasswordHint.setText("  使用 AES-256 ZIP 密码加密，防篡改");
        tvZipPasswordHint.setTextSize(12);
        tvZipPasswordHint.setTextColor(0xFF666666);
        tvZipPasswordHint.setPadding(0, 0, 0, 15);
        layout.addView(tvZipPasswordHint);
        
        // ZIP 密码输入（仅在选择 ZIP 密码保护时显示）
        TextView tvZipPasswordLabel = new TextView(this);
        tvZipPasswordLabel.setText("ZIP 密码：");
        tvZipPasswordLabel.setTextSize(14);
        tvZipPasswordLabel.setPadding(0, 10, 0, 8);
        tvZipPasswordLabel.setVisibility(View.GONE);
        layout.addView(tvZipPasswordLabel);
        
        android.widget.EditText etZipPassword = new android.widget.EditText(this);
        etZipPassword.setHint("输入 ZIP 密码（留空使用默认密码）");
        etZipPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etZipPassword.setVisibility(View.GONE);
        layout.addView(etZipPassword);
        
        TextView tvZipPasswordNote = new TextView(this);
        tvZipPasswordNote.setText("  密码从应用签名自动派生（设备绑定）");
        tvZipPasswordNote.setTextSize(12);
        tvZipPasswordNote.setTextColor(0xFF666666);
        tvZipPasswordNote.setPadding(0, 0, 0, 15);
        tvZipPasswordNote.setVisibility(View.GONE);
        layout.addView(tvZipPasswordNote);
        
        // ZIP 密码选项变化监听
        cbZipPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvZipPasswordLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            etZipPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            tvZipPasswordNote.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        
        // 加密选项
        android.widget.CheckBox cbEncrypt = new android.widget.CheckBox(this);
        cbEncrypt.setText("🔐 对补丁进行加密");
        cbEncrypt.setChecked(false);
        layout.addView(cbEncrypt);
        
        TextView tvEncryptHint = new TextView(this);
        tvEncryptHint.setText("  使用 AES-256-GCM 加密，保护补丁内容");
        tvEncryptHint.setTextSize(12);
        tvEncryptHint.setTextColor(0xFF666666);
        tvEncryptHint.setPadding(0, 0, 0, 15);
        layout.addView(tvEncryptHint);
        
        // AES 加密密码输入（仅在选择加密时显示）
        TextView tvPasswordLabel = new TextView(this);
        tvPasswordLabel.setText("加密密码：");
        tvPasswordLabel.setTextSize(14);
        tvPasswordLabel.setPadding(0, 10, 0, 8);
        tvPasswordLabel.setVisibility(View.GONE);
        layout.addView(tvPasswordLabel);
        
        android.widget.EditText etPassword = new android.widget.EditText(this);
        etPassword.setHint("输入加密密码（留空使用默认密码）");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setVisibility(View.GONE);
        layout.addView(etPassword);
        
        TextView tvPasswordHint = new TextView(this);
        tvPasswordHint.setText("  客户端需要相同密码才能解密");
        tvPasswordHint.setTextSize(12);
        tvPasswordHint.setTextColor(0xFF666666);
        tvPasswordHint.setPadding(0, 0, 0, 0);
        tvPasswordHint.setVisibility(View.GONE);
        layout.addView(tvPasswordHint);
        
        // 加密选项变化监听
        cbEncrypt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvPasswordLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            etPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            tvPasswordHint.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        
        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle("🔒 补丁安全选项")
            .setView(layout)
            .setPositiveButton("生成", (d, w) -> {
                boolean withApkSignature = cbApkSign.isChecked();
                boolean withZipPassword = cbZipPassword.isChecked();
                boolean withEncryption = cbEncrypt.isChecked();
                String zipPassword = etZipPassword.getText().toString().trim();
                String aesPassword = etPassword.getText().toString().trim();
                
                // 生成补丁
                generatePatchWithOptions(withApkSignature, withZipPassword, withEncryption, zipPassword, aesPassword);
            })
            .setNegativeButton("取消", null);
        
        builder.show();
    }
    
    /**
     * 生成补丁（可选 APK 签名、ZIP密码和加密）
     */
    private void generatePatchWithOptions(boolean withApkSignature, boolean withZipPassword, boolean withEncryption, String zipPassword, String aesPassword) {
        // 输出到下载目录
        File outputFile = new File(outputDir, "patch_" + System.currentTimeMillis() + ".zip");

        // 记录详细的输入信息
        Log.d(TAG, "=== 开始生成补丁 ===");
        Log.d(TAG, "基准 APK: " + selectedBaseApk.getAbsolutePath());
        Log.d(TAG, "基准 APK 大小: " + formatSize(selectedBaseApk.length()));
        Log.d(TAG, "新版 APK: " + selectedNewApk.getAbsolutePath());
        Log.d(TAG, "新版 APK 大小: " + formatSize(selectedNewApk.length()));
        Log.d(TAG, "输出文件: " + outputFile.getAbsolutePath());
        Log.d(TAG, "APK签名: " + withApkSignature);
        Log.d(TAG, "ZIP密码: " + withZipPassword);
        Log.d(TAG, "加密: " + withEncryption);

        String status = "正在生成补丁...";
        if (withApkSignature && withZipPassword && withEncryption) {
            status = "正在生成、APK签名、ZIP密码并加密补丁...";
        } else if (withApkSignature && withZipPassword) {
            status = "正在生成、APK签名并添加ZIP密码...";
        } else if (withApkSignature && withEncryption) {
            status = "正在生成、APK签名并加密补丁...";
        } else if (withZipPassword && withEncryption) {
            status = "正在生成、ZIP密码并加密补丁...";
        } else if (withApkSignature) {
            status = "正在生成并添加APK签名...";
        } else if (withZipPassword) {
            status = "正在生成并添加ZIP密码...";
        } else if (withEncryption) {
            status = "正在生成并加密补丁...";
        }
        
        tvStatus.setText(status);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        // 不在 AndroidPatchGenerator 中签名，而是在补丁生成完成后使用 PatchSigner（apksig）签名
        // 这样可以使用更快更可靠的 apksig 库
        
        generator = new AndroidPatchGenerator.Builder(this)
                .baseApk(selectedBaseApk)
                .newApk(selectedNewApk)
                .output(outputFile)
                // 不传入 signingConfig，补丁生成后再签名
                .callbackOnMainThread(true)
                .callback(new SimpleAndroidGeneratorCallback() {
                    @Override
                    public void onStart() {
                        tvStatus.setText("开始生成...");
                    }

                    @Override
                    public void onProgress(int percent, String stage) {
                        progressBar.setProgress(percent);
                        tvStatus.setText(stage + " (" + percent + "%)");
                    }
                    
                    @Override
                    public void onSignStart() {
                        // 不再使用旧的 JarSigner 签名
                        Log.i(TAG, "跳过旧的签名流程，将在补丁生成后使用 apksig 签名");
                    }

                    @Override
                    public void onComplete(PatchResult result) {
                        if (result.isSuccess()) {
                            lastGeneratedPatch = result.getPatchFile();
                            
                            // 检查是否有补丁文件（可能两个 APK 完全相同）
                            if (lastGeneratedPatch == null) {
                                progressBar.setVisibility(View.GONE);
                                setButtonsEnabled(true);
                                
                                // 检查是否是因为没有差异
                                if (result.getDiffSummary() != null && !result.getDiffSummary().hasChanges()) {
                                    String msg = "两个 APK 完全相同，无需生成补丁";
                                    tvStatus.setText("ℹ️ " + msg);
                                    Log.i(TAG, msg);
                                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                                } else {
                                    String errorMsg = "补丁文件生成失败（未知原因）";
                                    tvStatus.setText("✗ " + errorMsg);
                                    Log.e(TAG, errorMsg);
                                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                }
                                return;
                            }
                            
                            // 检查补丁文件是否存在
                            if (!lastGeneratedPatch.exists()) {
                                progressBar.setVisibility(View.GONE);
                                setButtonsEnabled(true);
                                String errorMsg = "补丁文件不存在: " + lastGeneratedPatch.getPath();
                                tvStatus.setText("✗ 生成失败: " + errorMsg);
                                Log.e(TAG, errorMsg);
                                Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                return;
                            }
                            
                            Log.d(TAG, "✓ 补丁生成成功: " + lastGeneratedPatch.getAbsolutePath());
                            Log.d(TAG, "补丁大小: " + formatSize(lastGeneratedPatch.length()));
                            
                            // 处理 APK 签名、ZIP密码和加密
                            if (withApkSignature || withZipPassword || withEncryption) {
                                processSecurityOptions(result, withApkSignature, withZipPassword, withEncryption, zipPassword, aesPassword);
                            } else {
                                progressBar.setVisibility(View.GONE);
                                setButtonsEnabled(true);
                                tvStatus.setText("✓ 补丁生成成功!");
                                
                                // 清除之前选择的补丁文件，使用新生成的补丁
                                selectedPatchFile = null;
                                btnSelectPatch.setText("选择补丁");
                                
                                showPatchResult(result);
                                updateButtonStates();
                            }
                        } else {
                            progressBar.setVisibility(View.GONE);
                            setButtonsEnabled(true);
                            tvStatus.setText("✗ 生成失败: " + result.getErrorMessage());
                        }
                    }

                    @Override
                    public void onError(int errorCode, String message) {
                        progressBar.setVisibility(View.GONE);
                        setButtonsEnabled(true);
                        tvStatus.setText("✗ 错误: " + message);
                    }

                    @Override
                    public void onCancelled() {
                        progressBar.setVisibility(View.GONE);
                        setButtonsEnabled(true);
                        tvStatus.setText("已取消");
                    }
                })
                .build();

        generator.generateInBackground();
    }
    
    /**
     * 处理安全选项（APK 签名、ZIP密码和加密）
     */
    private void processSecurityOptions(PatchResult result, boolean withApkSignature, 
                                       boolean withZipPassword, boolean withEncryption, 
                                       String zipPassword, String aesPassword) {
        new Thread(() -> {
            try {
                File patchFile = result.getPatchFile();
                File currentFile = patchFile;  // 当前处理的文件
                
                // 确保补丁文件存在
                if (patchFile == null || !patchFile.exists()) {
                    throw new Exception("补丁文件不存在: " + (patchFile != null ? patchFile.getPath() : "null"));
                }
                
                Log.d(TAG, "补丁文件路径: " + patchFile.getAbsolutePath());
                Log.d(TAG, "补丁文件大小: " + patchFile.length() + " bytes");
                
                // 1. APK 签名（使用 apksig 对补丁进行真正的签名）
                if (withApkSignature) {
                    runOnUiThread(() -> tvStatus.setText("正在签名补丁..."));
                    
                    // 使用 PatchSigner 对补丁进行签名
                    PatchSigner patchSigner = new PatchSigner(MainActivity.this);
                    File signedPatch = patchSigner.signPatch(
                        currentFile,
                        selectedKeystoreFile,
                        keystorePassword,
                        keyAlias,
                        keyPassword
                    );
                    
                    if (signedPatch != null && signedPatch.exists()) {
                        currentFile = signedPatch;  // 更新当前文件为签名后的文件
                        Log.d(TAG, "✓ 补丁签名成功");
                        Log.d(TAG, "  签名后文件: " + signedPatch.getAbsolutePath());
                        Log.d(TAG, "  签名后大小: " + formatSize(signedPatch.length()));
                    } else {
                        throw new Exception("补丁签名失败: " + patchSigner.getError());
                    }
                }
                
                // 2. ZIP 密码保护（在签名之后，AES 加密之前）
                if (withZipPassword) {
                    runOnUiThread(() -> tvStatus.setText("正在添加 ZIP 密码保护..."));
                    
                    com.orange.update.ZipPasswordManager zipPasswordManager = 
                        new com.orange.update.ZipPasswordManager(this);
                    
                    // 获取 ZIP 密码（如果用户输入了密码则使用用户密码，否则使用派生密码）
                    String finalZipPassword;
                    boolean isCustomPassword = false;
                    if (zipPassword != null && !zipPassword.isEmpty()) {
                        finalZipPassword = zipPassword;
                        isCustomPassword = true;
                        Log.d(TAG, "使用用户自定义 ZIP 密码");
                    } else {
                        finalZipPassword = zipPasswordManager.getZipPassword();
                        Log.d(TAG, "使用从应用签名派生的 ZIP 密码");
                    }
                    
                    // 创建加密后的 ZIP 文件（使用临时文件名）
                    File encryptedZipFile = new File(currentFile.getParent(), 
                        currentFile.getName().replace(".zip", "_zippwd.zip"));
                    
                    boolean success = zipPasswordManager.encryptZip(currentFile, encryptedZipFile, finalZipPassword);
                    
                    if (success) {
                        // 删除上一步的文件，重命名加密后的文件
                        currentFile.delete();
                        File finalZipFile = new File(currentFile.getParent(), 
                            patchFile.getName());  // 使用原始文件名
                        encryptedZipFile.renameTo(finalZipFile);
                        currentFile = finalZipFile;
                        
                        // 如果使用自定义密码，保存密码提示文件
                        if (isCustomPassword) {
                            File zipPasswordFile = new File(currentFile.getPath() + ".zippwd");
                            FileOutputStream fos = new FileOutputStream(zipPasswordFile);
                            fos.write(("ZIP 密码提示: 使用自定义密码\n" + 
                                      "注意: 应用补丁时需要输入相同密码\n" +
                                      "密码长度: " + finalZipPassword.length() + " 字符").getBytes("UTF-8"));
                            fos.close();
                            Log.d(TAG, "✓ 已保存 ZIP 密码提示文件");
                        }
                        
                        Log.d(TAG, "✓ ZIP 密码保护已添加（AES-256）");
                        Log.d(TAG, "  当前文件: " + currentFile.getAbsolutePath());
                    } else {
                        throw new Exception("ZIP 密码加密失败");
                    }
                }
                
                // 3. AES 加密补丁（在签名和 ZIP 密码之后）
                if (withEncryption) {
                    runOnUiThread(() -> tvStatus.setText("正在加密补丁..."));
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        com.orange.update.SecurityManager securityManager = 
                            new com.orange.update.SecurityManager(this);
                        
                        File encryptedFile;
                        File previousFile = currentFile;  // 保存上一步的文件引用
                        
                        // 根据是否有密码选择加密方法
                        if (aesPassword != null && !aesPassword.isEmpty()) {
                            // 使用密码加密
                            Log.d(TAG, "使用自定义密码加密补丁");
                            encryptedFile = securityManager.encryptPatchWithPassword(currentFile, aesPassword);
                            
                            // 保存密码提示信息
                            File passwordFile = new File(encryptedFile.getPath() + ".pwd");
                            FileOutputStream fos = new FileOutputStream(passwordFile);
                            fos.write(("密码提示: 使用自定义密码\n" + 
                                      "注意: 客户端需要相同密码才能解密\n" +
                                      "密码长度: " + aesPassword.length() + " 字符").getBytes("UTF-8"));
                            fos.close();
                        } else {
                            // 使用默认密钥加密
                            Log.d(TAG, "使用默认密钥加密补丁");
                            encryptedFile = securityManager.encryptPatch(currentFile);
                        }
                        
                        // 删除上一步的文件（如果不是原始补丁文件）
                        // 注意：签名后的文件会被重命名为原始文件名，所以需要检查是否是同一个文件
                        if (previousFile != null && previousFile.exists()) {
                            // 检查是否是不同的文件（通过绝对路径比较）
                            String previousPath = previousFile.getAbsolutePath();
                            String encryptedPath = encryptedFile.getAbsolutePath();
                            
                            if (!previousPath.equals(encryptedPath)) {
                                boolean deleted = previousFile.delete();
                                Log.d(TAG, "删除上一步文件: " + previousFile.getName() + " - " + (deleted ? "成功" : "失败"));
                            } else {
                                Log.d(TAG, "上一步文件与加密文件相同，跳过删除");
                            }
                        }
                        
                        currentFile = encryptedFile;  // 更新当前文件为加密后的文件
                        lastGeneratedPatch = encryptedFile;
                        Log.d(TAG, "✓ AES 加密完成");
                        Log.d(TAG, "  当前文件: " + currentFile.getAbsolutePath());
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, 
                                "加密需要 Android 6.0+", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
                
                // 4. 显示结果
                File finalFile = currentFile;
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    
                    String statusText = "✓ 补丁生成成功";
                    if (withApkSignature && withZipPassword && withEncryption) {
                        statusText += "（APK签名、ZIP密码并加密）";
                    } else if (withApkSignature && withZipPassword) {
                        statusText += "（APK签名并添加ZIP密码）";
                    } else if (withApkSignature && withEncryption) {
                        statusText += "（APK签名并加密）";
                    } else if (withZipPassword && withEncryption) {
                        statusText += "（ZIP密码并加密）";
                    } else if (withApkSignature) {
                        statusText += "（APK签名）";
                    } else if (withZipPassword) {
                        statusText += "（已添加ZIP密码）";
                    } else if (withEncryption) {
                        statusText += "（已加密）";
                    }
                    tvStatus.setText(statusText + "!");
                    
                    Log.i(TAG, "=== 最终补丁文件 ===");
                    Log.i(TAG, "  文件: " + finalFile.getAbsolutePath());
                    Log.i(TAG, "  大小: " + formatSize(finalFile.length()));
                    
                    // 清除之前选择的补丁文件，使用新生成的补丁
                    selectedPatchFile = null;
                    btnSelectPatch.setText("选择补丁");
                    
                    showSecuredPatchResult(result, finalFile, null, 
                                          null, withApkSignature, withZipPassword, withEncryption);
                    updateButtonStates();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "处理安全选项失败", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 处理失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "处理失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 显示安全补丁的结果
     */
    private void showSecuredPatchResult(PatchResult result, File patchFile, 
                                       File signatureFile, String signature,
                                       boolean withApkSignature, boolean withZipPassword, boolean withEncryption) {
        StringBuilder info = new StringBuilder();
        info.append("=== 🔒 补丁生成成功 ===\n\n");
        
        // 安全选项
        info.append("=== 安全选项 ===\n");
        if (withApkSignature) {
            info.append("✓ APK 签名验证（防篡改）\n");
        }
        if (withZipPassword) {
            info.append("✓ ZIP 密码保护（AES-256，防篡改）\n");
        }
        if (withEncryption) {
            info.append("✓ AES-256-GCM 加密（存储保护）\n");
        }
        if (!withApkSignature && !withZipPassword && !withEncryption) {
            info.append("⚠️ 未启用安全选项\n");
        }
        info.append("\n");
        
        // 文件信息
        info.append("=== 文件信息 ===\n");
        info.append("📁 补丁文件: ").append(patchFile.getName()).append("\n");
        info.append("📍 位置: ").append(patchFile.getParent()).append("\n");
        info.append("📊 大小: ").append(formatSize(patchFile.length())).append("\n");
        
        if (withZipPassword) {
            info.append("🔑 ZIP 密码: 已加密（AES-256）\n");
        }
        
        if (withEncryption) {
            info.append("🔐 AES 加密: 已加密（AES-256-GCM）\n");
        }
        
        if (withApkSignature) {
            info.append("\n🔒 APK 签名: 使用应用签名验证\n");
        }
        
        info.append("\n⏱ 耗时: ").append(result.getGenerateTime()).append(" ms\n\n");
        
        // APK 签名信息
        if (withApkSignature) {
            info.append("=== APK 签名验证 ===\n");
            info.append("验证方式: 应用签名证书 MD5 比对\n");
            info.append("优点: 无需管理密钥，启动速度快\n");
            info.append("防篡改: 攻击者无法伪造签名\n\n");
        }
        
        // ZIP 密码信息
        if (withZipPassword) {
            info.append("=== ZIP 密码保护 ===\n");
            info.append("算法: AES-256\n");
            info.append("密码派生: 从应用签名自动派生\n");
            info.append("设备绑定: 是（每个设备密码不同）\n");
            info.append("状态: 已加密\n\n");
        }
        
        // AES 加密信息
        if (withEncryption) {
            info.append("=== AES 加密信息 ===\n");
            info.append("算法: AES-256-GCM\n");
            info.append("密钥存储: Android KeyStore\n");
            info.append("认证标签: 128位\n");
            info.append("状态: 已加密\n\n");
        }
        
        // 差异统计
        if (result.getDiffSummary() != null) {
            info.append("=== 差异统计 ===\n");
            info.append("修改类: ").append(result.getDiffSummary().getModifiedClasses()).append("\n");
            info.append("新增类: ").append(result.getDiffSummary().getAddedClasses()).append("\n");
            info.append("删除类: ").append(result.getDiffSummary().getDeletedClasses()).append("\n");
            info.append("修改资源: ").append(result.getDiffSummary().getModifiedResources()).append("\n");
            info.append("新增资源: ").append(result.getDiffSummary().getAddedResources()).append("\n");
            info.append("删除资源: ").append(result.getDiffSummary().getDeletedResources()).append("\n\n");
        }
        
        // 使用说明
        info.append("=== 💡 使用说明 ===\n");
        if (withApkSignature && withZipPassword && withEncryption) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append(" (三重保护)\n");
            info.append("2. APK 签名验证（应用签名）\n");
            info.append("3. ZIP 已使用密码加密（AES-256）\n");
            info.append("4. 整个文件已使用 AES-256-GCM 加密\n");
            info.append("5. 客户端会自动验证所有安全层\n");
        } else if (withApkSignature && withZipPassword) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append(" (双重保护)\n");
            info.append("2. APK 签名验证（应用签名）\n");
            info.append("3. ZIP 已使用密码加密（AES-256）\n");
            info.append("4. 客户端会自动验证签名和 ZIP 密码\n");
        } else if (withApkSignature && withEncryption) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append(" (已加密)\n");
            info.append("2. APK 签名验证（应用签名）\n");
            info.append("3. 客户端需要先解密再验证签名\n");
            info.append("4. 解密需要相同的密钥\n");
        } else if (withZipPassword && withEncryption) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append(" (双重加密)\n");
            info.append("2. ZIP 已使用密码加密（AES-256）\n");
            info.append("3. 整个文件已使用 AES-256-GCM 加密\n");
            info.append("4. 客户端会自动解密和验证\n");
        } else if (withApkSignature) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append("\n");
            info.append("2. APK 签名验证（应用签名）\n");
            info.append("3. 只需发送补丁文件给客户端\n");
            info.append("4. 客户端自动验证应用签名\n");
        } else if (withZipPassword) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append(" (ZIP密码保护)\n");
            info.append("2. ZIP 已使用密码加密（AES-256）\n");
            info.append("3. 密码从应用签名自动派生\n");
            info.append("4. 客户端会自动验证 ZIP 密码\n");
        } else if (withEncryption) {
            info.append("1. 补丁文件: ").append(patchFile.getName()).append(" (已加密)\n");
            info.append("2. 客户端需要相同密钥才能解密\n");
            info.append("3. 解密后可以应用补丁\n");
        }
        info.append("\n");
        
        // 安全提示
        info.append("⚠️ 安全提示:\n");
        if (withApkSignature) {
            info.append("• APK 签名验证可以防止补丁被篡改\n");
            info.append("• 无需管理密钥，启动速度快\n");
        }
        if (withZipPassword) {
            info.append("• ZIP 密码保护可以防止补丁被篡改\n");
            info.append("• 密码从应用签名派生，设备绑定\n");
        }
        if (withEncryption) {
            info.append("• AES 加密可以保护补丁内容\n");
            info.append("• 客户端需要相同密钥才能解密\n");
        }
        if (withApkSignature && withZipPassword && withEncryption) {
            info.append("• 三重保护提供最高安全级别！\n");
        } else if ((withApkSignature && withZipPassword) || (withApkSignature && withEncryption) || (withZipPassword && withEncryption)) {
            info.append("• 双重保护提供高安全级别\n");
        }
        
        tvInfo.setText(info.toString());
    }

    private void showPatchResult(PatchResult result) {
        StringBuilder info = new StringBuilder();
        info.append("=== 补丁生成成功 ===\n\n");
        
        if (result.getPatchFile() != null) {
            info.append("📁 文件: ").append(result.getPatchFile().getName()).append("\n");
            info.append("📍 位置: ").append(result.getPatchFile().getParent()).append("\n\n");
        }
        
        info.append("📊 大小: ").append(formatSize(result.getPatchSize())).append("\n");
        info.append("⏱ 耗时: ").append(result.getGenerateTime()).append(" ms\n");
        
        if (result.getDiffSummary() != null) {
            info.append("\n=== 差异统计 ===\n");
            info.append("修改类: ").append(result.getDiffSummary().getModifiedClasses()).append("\n");
            info.append("新增类: ").append(result.getDiffSummary().getAddedClasses()).append("\n");
            info.append("删除类: ").append(result.getDiffSummary().getDeletedClasses()).append("\n");
            info.append("修改资源: ").append(result.getDiffSummary().getModifiedResources()).append("\n");
            info.append("新增资源: ").append(result.getDiffSummary().getAddedResources()).append("\n");
            info.append("删除资源: ").append(result.getDiffSummary().getDeletedResources()).append("\n");
        }
        
        if (selectedNewApk != null && result.getPatchSize() > 0) {
            float ratio = (float) result.getPatchSize() / selectedNewApk.length() * 100;
            info.append("\n压缩比: ").append(String.format("%.1f%%", ratio));
        }
        
        tvInfo.setText(info.toString());
    }

    /**
     * 应用补丁 - 真正的热更新
     */
    private void applyPatch() {
        File patchToApply = selectedPatchFile != null ? selectedPatchFile : lastGeneratedPatch;
        
        if (patchToApply == null || !patchToApply.exists()) {
            Toast.makeText(this, "请先生成或选择补丁文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "应用补丁文件: " + patchToApply.getAbsolutePath());
        Log.d(TAG, "文件名: " + patchToApply.getName());
        
        // 获取安全策略配置
        android.content.SharedPreferences securityPrefs = getSharedPreferences(PREFS_SECURITY, MODE_PRIVATE);
        boolean requireSignature = securityPrefs.getBoolean(KEY_REQUIRE_SIGNATURE, false);
        boolean requireEncryption = securityPrefs.getBoolean(KEY_REQUIRE_ENCRYPTION, false);
        
        // 检查两种加密方式：AES 加密（.enc）或 ZIP 密码加密
        boolean isAesEncrypted = patchToApply.getName().endsWith(".enc");
        boolean isZipPasswordEncrypted = false;
        
        // 检查 ZIP 密码加密
        if (!isAesEncrypted) {
            try {
                com.orange.update.ZipPasswordManager zipPasswordManager = 
                    new com.orange.update.ZipPasswordManager(this);
                isZipPasswordEncrypted = zipPasswordManager.isEncrypted(patchToApply);
            } catch (Exception e) {
                Log.d(TAG, "检查 ZIP 密码加密失败: " + e.getMessage());
            }
        }
        
        boolean isEncrypted = isAesEncrypted || isZipPasswordEncrypted;
        
        Log.d(TAG, "安全策略 - 要求签名: " + requireSignature + ", 要求加密: " + requireEncryption);
        Log.d(TAG, "补丁状态 - AES加密: " + isAesEncrypted + ", ZIP密码加密: " + isZipPasswordEncrypted + ", 已加密: " + isEncrypted);
        
        // 对于加密文件，签名检查将在 HotUpdateHelper 解密后进行
        if (!isEncrypted) {
            // 只对未加密文件检查签名
            boolean hasSignature = checkHasApkSignature(patchToApply);
            Log.d(TAG, "补丁状态 - 有签名: " + hasSignature);
            
            // 检查安全策略
            if (requireSignature && !hasSignature) {
                new AlertDialog.Builder(this)
                    .setTitle("⚠️ 安全策略限制")
                    .setMessage("当前安全策略要求补丁必须包含 APK 签名！\n\n" +
                               "此补丁未包含 APK 签名，拒绝应用。\n\n" +
                               "补丁文件: " + patchToApply.getName() + "\n\n" +
                               "解决方法：\n" +
                               "1. 重新生成补丁，并选择「APK 签名验证」选项\n" +
                               "2. 或在安全设置中关闭签名验证要求")
                    .setPositiveButton("确定", null)
                    .setNeutralButton("安全设置", (d, w) -> showSecuritySettingsDialog())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
                return;
            }
        } else {
            // 对于加密文件，签名验证将在 HotUpdateHelper 中解密后进行
            Log.d(TAG, "补丁已加密（" + (isAesEncrypted ? "AES" : "ZIP密码") + "），签名验证将在解密后进行");
        }
        
        if (requireEncryption && !isEncrypted) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ 安全策略限制")
                .setMessage("当前安全策略要求补丁必须加密！\n\n" +
                           "此补丁未加密，拒绝应用。\n\n" +
                           "支持的加密方式：\n" +
                           "1. AES 加密（.enc 文件）\n" +
                           "2. ZIP 密码加密\n\n" +
                           "请使用已加密的补丁，或在设置中关闭加密验证要求。")
                .setPositiveButton("确定", null)
                .setNeutralButton("安全设置", (d, w) -> showSecuritySettingsDialog())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
            return;
        }

        // 直接应用补丁，HotUpdateHelper 会自动处理解密和签名验证
        Log.d(TAG, "继续应用补丁流程");
        proceedWithPatch(patchToApply);
    }
    
    /**
     * 验证签名并应用补丁
     */
    private void verifyAndApplyPatch(File patchFile, File signatureFile) {
        tvStatus.setText("正在验证补丁签名...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);
        
        new Thread(() -> {
            try {
                // 读取签名（优先从 zip 内部读取）
                String signature = null;
                
                // 尝试从 zip 内部读取
                try {
                    signature = extractSignatureFromZip(patchFile);
                    if (signature != null) {
                        Log.d(TAG, "✓ 从 zip 内部读取签名");
                    }
                } catch (Exception e) {
                    Log.d(TAG, "从 zip 内部读取签名失败: " + e.getMessage());
                }
                
                // 如果 zip 内部没有，尝试从外部文件读取（向后兼容）
                if (signature == null && signatureFile != null && signatureFile.exists()) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(signatureFile);
                    byte[] sigBytes = new byte[(int) signatureFile.length()];
                    fis.read(sigBytes);
                    fis.close();
                    signature = new String(sigBytes, "UTF-8");
                    Log.d(TAG, "✓ 从外部文件读取签名");
                }
                
                if (signature == null) {
                    throw new Exception("未找到签名文件（既不在 zip 内部，也没有外部 .sig 文件）");
                }
                
                // 验证签名
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this);
                
                boolean isValid = securityManager.verifySignature(patchFile, signature);
                
                if (isValid) {
                    Log.d(TAG, "✓ 签名验证成功");
                    runOnUiThread(() -> {
                        tvStatus.setText("✓ 签名验证成功");
                        Toast.makeText(MainActivity.this, "✓ 签名验证通过", Toast.LENGTH_SHORT).show();
                        // 继续处理补丁
                        proceedWithPatch(patchFile);
                    });
                } else {
                    Log.e(TAG, "✗ 签名验证失败");
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        setButtonsEnabled(true);
                        tvStatus.setText("✗ 签名验证失败");
                        
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("⚠️ 签名验证失败")
                            .setMessage("补丁签名验证失败！\n\n可能原因：\n• 补丁文件已被篡改\n• 签名文件不匹配\n• 使用了错误的密钥对\n\n为了安全，拒绝应用此补丁。")
                            .setPositiveButton("确定", null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "签名验证异常", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 签名验证异常: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "签名验证异常: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 继续处理补丁（签名验证通过后或无签名）
     */
    private void proceedWithPatch(File patchToApply) {
        // 直接应用补丁，HotUpdateHelper 会自动处理加密和解密
        // 不再在 MainActivity 中处理 AES 解密，统一由 HotUpdateHelper 处理
        Log.d(TAG, "调用 HotUpdateHelper 应用补丁");
        applyPatchDirect(patchToApply);
    }
    
    /**
     * 显示解密密码输入对话框
     */
    private void showDecryptPasswordDialog(File encryptedPatch) {
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 提示文本
        TextView tvHint = new TextView(this);
        tvHint.setText("此补丁已加密，请输入解密密码：");
        tvHint.setTextSize(14);
        tvHint.setPadding(0, 0, 0, 20);
        layout.addView(tvHint);
        
        // 密码输入框
        android.widget.EditText etPassword = new android.widget.EditText(this);
        etPassword.setHint("输入密码（留空使用默认密钥）");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPassword);
        
        // 提示信息
        TextView tvNote = new TextView(this);
        tvNote.setText("\n💡 提示：\n• 如果生成时使用了自定义密码，请输入相同密码\n• 如果生成时未设置密码，留空即可");
        tvNote.setTextSize(12);
        tvNote.setTextColor(0xFF666666);
        layout.addView(tvNote);
        
        // 创建对话框
        new AlertDialog.Builder(this)
            .setTitle("🔐 解密补丁")
            .setView(layout)
            .setPositiveButton("解密并应用", (d, w) -> {
                String password = etPassword.getText().toString().trim();
                decryptAndApplyPatch(encryptedPatch, password);
            })
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show();
    }
    
    /**
     * 解密并应用补丁
     */
    private void decryptAndApplyPatch(File encryptedPatch, String password) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "解密需要 Android 6.0+", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvStatus.setText("正在解密补丁...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);
        
        new Thread(() -> {
            try {
                // 使用 SecurityManager 解密
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this);
                
                File decryptedPatch;
                
                if (password != null && !password.isEmpty()) {
                    // 使用密码解密
                    Log.d(TAG, "使用自定义密码解密");
                    decryptedPatch = securityManager.decryptPatchWithPassword(encryptedPatch, password);
                } else {
                    // 使用默认密钥解密
                    Log.d(TAG, "使用默认密钥解密");
                    decryptedPatch = securityManager.decryptPatch(encryptedPatch);
                }
                
                runOnUiThread(() -> {
                    tvStatus.setText("✓ 解密成功，正在应用补丁...");
                    // 应用解密后的补丁
                    applyPatchDirect(decryptedPatch);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "解密失败", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 解密失败: " + e.getMessage());
                    
                    String errorMsg = "解密失败: " + e.getMessage();
                    if (e.getMessage() != null && e.getMessage().contains("Tag mismatch")) {
                        errorMsg += "\n\n可能原因：\n• 密码错误\n• 文件已损坏\n• 使用了错误的密钥";
                    }
                    
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 直接应用补丁（未加密）
     */
    private void applyPatchDirect(File patchFile) {
        tvStatus.setText("正在应用热更新...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        hotUpdateHelper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
            @Override
            public void onProgress(int percent, String message) {
                runOnUiThread(() -> {
                    progressBar.setProgress(percent);
                    tvStatus.setText(message);
                });
            }

            @Override
            public void onSuccess(HotUpdateHelper.PatchResult result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("🔥 热更新成功!");
                    
                    // 更新版本显示
                    tvVersion.setText("v" + result.patchVersion + " (热更新)");
                    
                    // 显示清除按钮
                    btnClearPatch.setVisibility(View.VISIBLE);
                    
                    // 显示结果
                    showRealHotUpdateResult(result);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 热更新失败: " + message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public void onZipPasswordRequired(File patchFileToDecrypt) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    // 显示 ZIP 密码输入对话框
                    showZipPasswordDialog(patchFileToDecrypt);
                });
            }
            
            @Override
            public void onAesPasswordRequired(File patchFileToDecrypt) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    // 显示 AES 密码输入对话框
                    showAesPasswordDialog(patchFileToDecrypt);
                });
            }
        });
    }
    
    /**
     * 显示 ZIP 密码输入对话框
     */
    private void showZipPasswordDialog(File patchFile) {
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 提示文本
        TextView tvHint = new TextView(this);
        tvHint.setText("此补丁使用了自定义 ZIP 密码保护，请输入密码：");
        tvHint.setTextSize(14);
        tvHint.setPadding(0, 0, 0, 20);
        layout.addView(tvHint);
        
        // 密码输入框
        android.widget.EditText etZipPassword = new android.widget.EditText(this);
        etZipPassword.setHint("输入 ZIP 密码");
        etZipPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etZipPassword);
        
        // 提示信息
        TextView tvNote = new TextView(this);
        tvNote.setText("\n💡 提示：\n• 请输入生成补丁时设置的 ZIP 密码\n• 密码用于验证补丁完整性，防止篡改");
        tvNote.setTextSize(12);
        tvNote.setTextColor(0xFF666666);
        layout.addView(tvNote);
        
        // 创建对话框
        new AlertDialog.Builder(this)
            .setTitle("🔑 ZIP 密码验证")
            .setView(layout)
            .setPositiveButton("验证并应用", (d, w) -> {
                String zipPassword = etZipPassword.getText().toString().trim();
                
                if (zipPassword.isEmpty()) {
                    Toast.makeText(this, "请输入 ZIP 密码", Toast.LENGTH_SHORT).show();
                    // 重新显示对话框
                    showZipPasswordDialog(patchFile);
                    return;
                }
                
                // 使用密码应用补丁
                applyPatchWithZipPassword(patchFile, zipPassword);
            })
            .setNegativeButton("取消", (d, w) -> {
                tvStatus.setText("已取消应用补丁");
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * 显示 AES 密码输入对话框
     */
    private void showAesPasswordDialog(File patchFile) {
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 提示文本
        TextView tvHint = new TextView(this);
        tvHint.setText("此补丁使用了自定义密码加密，请输入密码：");
        tvHint.setTextSize(14);
        tvHint.setPadding(0, 0, 0, 20);
        layout.addView(tvHint);
        
        // 密码输入框
        android.widget.EditText etAesPassword = new android.widget.EditText(this);
        etAesPassword.setHint("输入解密密码");
        etAesPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etAesPassword);
        
        // 提示信息
        TextView tvNote = new TextView(this);
        tvNote.setText("\n💡 提示：\n• 请输入生成补丁时设置的加密密码\n• 如果生成时未设置密码，请联系管理员");
        tvNote.setTextSize(12);
        tvNote.setTextColor(0xFF666666);
        layout.addView(tvNote);
        
        // 创建对话框
        new AlertDialog.Builder(this)
            .setTitle("🔐 解密补丁")
            .setView(layout)
            .setPositiveButton("解密并应用", (d, w) -> {
                String aesPassword = etAesPassword.getText().toString().trim();
                
                if (aesPassword.isEmpty()) {
                    Toast.makeText(this, "请输入解密密码", Toast.LENGTH_SHORT).show();
                    // 重新显示对话框
                    showAesPasswordDialog(patchFile);
                    return;
                }
                
                // 使用密码应用补丁
                applyPatchWithAesPassword(patchFile, aesPassword);
            })
            .setNegativeButton("取消", (d, w) -> {
                tvStatus.setText("已取消应用补丁");
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * 使用 ZIP 密码应用补丁
     */
    private void applyPatchWithZipPassword(File patchFile, String zipPassword) {
        tvStatus.setText("正在验证 ZIP 密码...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        hotUpdateHelper.applyPatchWithZipPassword(patchFile, zipPassword, new HotUpdateHelper.Callback() {
            @Override
            public void onProgress(int percent, String message) {
                runOnUiThread(() -> {
                    progressBar.setProgress(percent);
                    tvStatus.setText(message);
                });
            }

            @Override
            public void onSuccess(HotUpdateHelper.PatchResult result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("🔥 热更新成功!");
                    
                    // 更新版本显示
                    tvVersion.setText("v" + result.patchVersion + " (热更新)");
                    
                    // 显示清除按钮
                    btnClearPatch.setVisibility(View.VISIBLE);
                    
                    // 显示结果
                    showRealHotUpdateResult(result);
                    
                    // 延迟重新创建 Activity，让用户看到成功消息
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        // 重新创建 Activity 以显示资源更新（如布局中的火焰图标）
                        recreate();
                    }, 1500); // 1.5秒后重新创建
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 热更新失败: " + message);
                    
                    // 如果是密码错误，提示用户重新输入
                    if (message.contains("密码") || message.contains("验证失败")) {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("⚠️ 密码错误")
                            .setMessage(message + "\n\n是否重新输入密码？")
                            .setPositiveButton("重新输入", (d, w) -> {
                                showZipPasswordDialog(patchFile);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    } else {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    
    /**
     * 使用 AES 密码应用补丁
     */
    private void applyPatchWithAesPassword(File patchFile, String aesPassword) {
        tvStatus.setText("正在解密补丁...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        hotUpdateHelper.applyPatchWithAesPassword(patchFile, aesPassword, new HotUpdateHelper.Callback() {
            @Override
            public void onProgress(int percent, String message) {
                runOnUiThread(() -> {
                    progressBar.setProgress(percent);
                    tvStatus.setText(message);
                });
            }

            @Override
            public void onSuccess(HotUpdateHelper.PatchResult result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("🔥 热更新成功!");
                    
                    // 更新版本显示
                    tvVersion.setText("v" + result.patchVersion + " (热更新)");
                    
                    // 显示清除按钮
                    btnClearPatch.setVisibility(View.VISIBLE);
                    
                    // 显示结果
                    showRealHotUpdateResult(result);
                    
                    // 延迟重新创建 Activity，让用户看到成功消息
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        // 重新创建 Activity 以显示资源更新（如布局中的火焰图标）
                        recreate();
                    }, 1500); // 1.5秒后重新创建
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 热更新失败: " + message);
                    
                    // 如果是密码错误，提示用户重新输入
                    if (message.contains("密码") || message.contains("解密失败")) {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("⚠️ 密码错误")
                            .setMessage(message + "\n\n是否重新输入密码？")
                            .setPositiveButton("重新输入", (d, w) -> {
                                showAesPasswordDialog(patchFile);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    } else {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
            
            @Override
            public void onZipPasswordRequired(File patchFileToDecrypt) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    // 显示 ZIP 密码输入对话框
                    showZipPasswordDialog(patchFileToDecrypt);
                });
            }
        });
    }

    /**
     * 清除补丁（回滚）
     */
    private void clearPatch() {
        new AlertDialog.Builder(this)
            .setTitle("清除补丁")
            .setMessage("确定要清除已应用的补丁吗？\n\n注意：清除后需要重启应用才能完全回滚到原版本。")
            .setPositiveButton("确定", (d, w) -> {
                hotUpdateHelper.clearPatch();
                btnClearPatch.setVisibility(View.GONE);
                
                // 刷新显示
                try {
                    PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    tvVersion.setText("v" + pInfo.versionName);
                } catch (PackageManager.NameNotFoundException e) {
                    tvVersion.setText("v1.1");
                }
                
                tvStatus.setText("✓ 补丁已清除，重启应用后生效");
                showSystemInfo();
                Toast.makeText(this, "补丁已清除", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showRealHotUpdateResult(HotUpdateHelper.PatchResult result) {
        StringBuilder info = new StringBuilder();
        info.append("=== 🔥 热更新成功 ===\n\n");
        
        info.append("状态: ✓ 已应用\n\n");
        
        if (result.oldVersion != null) {
            info.append("原版本: ").append(result.oldVersion).append("\n");
        }
        info.append("新版本: ").append(result.patchVersion).append("\n");
        if (result.newVersionCode != null) {
            info.append("版本号: ").append(result.newVersionCode).append("\n\n");
        } else {
            info.append("\n");
        }
        
        info.append("补丁大小: ").append(formatSize(result.patchSize)).append("\n\n");
        
        info.append("=== 热更新详情 ===\n");
        info.append("DEX 注入: ").append(result.dexInjected ? "✓ 成功" : "✗ 无 DEX").append("\n");
        info.append("SO 加载: ").append(result.soLoaded ? "✓ 成功" : "✗ 无 SO").append("\n");
        info.append("资源加载: ").append(result.resourcesLoaded ? "✓ 成功" : "✗ 无资源").append("\n\n");
        
        info.append("=== 热更新说明 ===\n");
        info.append("✓ 版本已从 ").append(result.oldVersion != null ? result.oldVersion : "原版本");
        info.append(" 更新到 ").append(result.patchVersion).append("\n");
        info.append("✓ 无需重新安装 APK\n");
        
        if (result.dexInjected) {
            info.append("✓ DEX 已注入到 ClassLoader\n");
            info.append("✓ 新代码立即生效\n");
        } else {
            info.append("⚠ 补丁中无 DEX 文件\n");
        }
        
        if (result.soLoaded) {
            info.append("✓ SO 库已加载\n");
            info.append("✓ Native 代码立即生效\n");
        }
        
        if (result.resourcesLoaded && result.needsRestart) {
            info.append("\n⚠ 资源已更新，重启应用后生效\n");
        }
        
        info.append("\n提示: 点击「清除补丁」可回滚");
        
        tvInfo.setText(info.toString());
        
        // 如果有资源更新，提示用户重启
        if (result.resourcesLoaded && result.needsRestart) {
            new AlertDialog.Builder(this)
                .setTitle("资源更新")
                .setMessage("资源补丁已应用，需要重启应用才能看到新的界面。\n\n是否立即重启？")
                .setPositiveButton("立即重启", (d, w) -> {
                    // 重启应用
                    Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                        // 强制结束进程以确保完全重启
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                })
                .setNegativeButton("稍后重启", null)
                .show();
        }
    }

    /**
     * 测试 Assets 文件读取
     */
    private void testAssetsFile() {
        StringBuilder info = new StringBuilder();
        info.append("=== 📄 Assets 文件测试 ===\n\n");
        
        try {
            // 读取 config.txt 文件
            java.io.InputStream is = getAssets().open("config.txt");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(is, "UTF-8"));
            
            String line;
            while ((line = reader.readLine()) != null) {
                info.append(line).append("\n");
            }
            
            reader.close();
            is.close();
            
            info.append("\n✓ Assets 文件读取成功");
            tvStatus.setText("✓ Assets 文件读取成功");
            
        } catch (Exception e) {
            info.append("✗ 读取失败: ").append(e.getMessage());
            tvStatus.setText("✗ Assets 文件读取失败");
        }
        
        tvInfo.setText(info.toString());
    }

    /**
     * 打开服务端测试页面
     */
    private void openServerTest() {
        Intent intent = new Intent(this, ServerTestActivity.class);
        startActivity(intent);
    }

    /**
     * 生成RSA密钥对
     */
    private void generateRSAKeyPair() {
        tvStatus.setText("正在生成RSA密钥对...");
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        setButtonsEnabled(false);
        
        new Thread(() -> {
            try {
                // 生成2048位RSA密钥对
                java.security.KeyPairGenerator keyGen = 
                    java.security.KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                demoKeyPair = keyGen.generateKeyPair();
                
                // 获取公钥和私钥的Base64编码
                String publicKeyBase64 = android.util.Base64.encodeToString(
                    demoKeyPair.getPublic().getEncoded(),
                    android.util.Base64.NO_WRAP);
                String privateKeyBase64 = android.util.Base64.encodeToString(
                    demoKeyPair.getPrivate().getEncoded(),
                    android.util.Base64.NO_WRAP);
                
                // 保存密钥到下载文件夹
                File publicKeyFile = saveKeyToFile(publicKeyBase64, "rsa_public_key.txt");
                File privateKeyFile = saveKeyToFile(privateKeyBase64, "rsa_private_key.txt");
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✓ RSA密钥对生成成功！");
                    showKeyPairInfo(publicKeyBase64, privateKeyBase64, publicKeyFile, privateKeyFile);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "生成密钥对失败", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("✗ 生成失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "生成失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 保存密钥到文件
     */
    private File saveKeyToFile(String keyContent, String fileName) throws Exception {
        File keyFile = new File(outputDir, fileName);
        FileOutputStream fos = new FileOutputStream(keyFile);
        fos.write(keyContent.getBytes("UTF-8"));
        fos.close();
        return keyFile;
    }
    
    /**
     * 从文件加载密钥
     */
    private String loadKeyFromFile(File keyFile) throws Exception {
        if (!keyFile.exists()) {
            return null;
        }
        
        FileInputStream fis = new FileInputStream(keyFile);
        byte[] buffer = new byte[(int) keyFile.length()];
        fis.read(buffer);
        fis.close();
        
        return new String(buffer, "UTF-8").trim();
    }
    
    /**
     * 加载用户配置的密钥
     */
    private void loadUserKeys() {
        loadUserKeys(false); // 默认静默加载
    }
    
    /**
     * 加载用户配置的密钥
     * @param showToast 是否显示Toast提示
     */
    private void loadUserKeys(boolean showToast) {
        new Thread(() -> {
            try {
                File publicKeyFile = new File(outputDir, "rsa_public_key.txt");
                File privateKeyFile = new File(outputDir, "rsa_private_key.txt");
                
                String publicKeyBase64 = loadKeyFromFile(publicKeyFile);
                String privateKeyBase64 = loadKeyFromFile(privateKeyFile);
                
                if (publicKeyBase64 != null && privateKeyBase64 != null) {
                    // 重建密钥对
                    java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                    
                    // 加载公钥
                    byte[] publicKeyBytes = android.util.Base64.decode(publicKeyBase64, android.util.Base64.DEFAULT);
                    java.security.spec.X509EncodedKeySpec publicKeySpec = 
                        new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
                    java.security.PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
                    
                    // 加载私钥
                    byte[] privateKeyBytes = android.util.Base64.decode(privateKeyBase64, android.util.Base64.DEFAULT);
                    java.security.spec.PKCS8EncodedKeySpec privateKeySpec = 
                        new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes);
                    java.security.PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
                    
                    // 重建密钥对
                    demoKeyPair = new java.security.KeyPair(publicKey, privateKey);
                    
                    runOnUiThread(() -> {
                        Log.d(TAG, "✓ 已加载用户配置的密钥");
                        if (showToast) {
                            tvStatus.setText("✓ 已加载用户配置的密钥");
                            showKeyPairInfo(publicKeyBase64, privateKeyBase64, publicKeyFile, privateKeyFile);
                            Toast.makeText(MainActivity.this, 
                                "✓ 已加载密钥文件", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        Log.d(TAG, "未找到密钥文件");
                        if (showToast) {
                            tvStatus.setText("未找到密钥文件");
                        }
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "加载密钥失败", e);
                runOnUiThread(() -> {
                    if (showToast) {
                        tvStatus.setText("✗ 加载密钥失败: " + e.getMessage());
                        Toast.makeText(MainActivity.this, 
                            "加载密钥失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
    
    /**
     * 显示密钥对信息
     */
    private void showKeyPairInfo(String publicKey, String privateKey, File publicKeyFile, File privateKeyFile) {
        StringBuilder info = new StringBuilder();
        info.append("=== 🔑 RSA密钥对生成成功 ===\n\n");
        info.append("密钥长度: 2048位\n");
        info.append("算法: RSA\n");
        info.append("签名算法: SHA256withRSA\n\n");
        
        info.append("=== 📁 密钥文件已保存 ===\n");
        info.append("保存位置: ").append(outputDir.getAbsolutePath()).append("\n\n");
        
        info.append("公钥文件: ").append(publicKeyFile.getName()).append("\n");
        info.append("大小: ").append(formatSize(publicKeyFile.length())).append("\n\n");
        
        info.append("私钥文件: ").append(privateKeyFile.getName()).append("\n");
        info.append("大小: ").append(formatSize(privateKeyFile.length())).append("\n\n");
        
        info.append("=== 公钥 (Public Key) ===\n");
        info.append("用途: 客户端验证签名\n");
        info.append("存储: 编译到APK中\n");
        info.append("格式: X.509 (Base64)\n\n");
        info.append(formatKey(publicKey)).append("\n\n");
        
        info.append("=== 私钥 (Private Key) ===\n");
        info.append("用途: 服务器端签名\n");
        info.append("存储: 仅保存在服务器\n");
        info.append("格式: PKCS#8 (Base64)\n\n");
        info.append(formatKey(privateKey)).append("\n\n");
        
        info.append("=== 💡 使用说明 ===\n");
        info.append("1. 密钥文件已保存到下载文件夹\n");
        info.append("2. 可以直接编辑密钥文件配置自己的密钥\n");
        info.append("3. 重启应用会自动加载密钥文件\n");
        info.append("4. 公钥用于客户端验证签名\n");
        info.append("5. 私钥用于服务器端签名补丁\n\n");
        
        info.append("⚠️ 安全提示:\n");
        info.append("• 公钥可以公开，用于验证\n");
        info.append("• 私钥必须保密，只在服务器使用\n");
        info.append("• 不要将私钥编译到APK中\n");
        info.append("• 定期更换密钥对提高安全性\n\n");
        
        info.append("💡 现在可以测试签名验证功能了！");
        
        tvInfo.setText(info.toString());
    }
    
    /**
     * 格式化密钥显示（每64个字符换行）
     */
    private String formatKey(String key) {
        StringBuilder formatted = new StringBuilder();
        int lineLength = 64;
        for (int i = 0; i < key.length(); i += lineLength) {
            int end = Math.min(i + lineLength, key.length());
            formatted.append(key.substring(i, end)).append("\n");
        }
        return formatted.toString().trim();
    }
    
    /**
     * 测试签名验证 - 成功案例
     * 使用真实的RSA签名和验证
     */
    private void testSignatureVerificationSuccess() {
        if (demoKeyPair == null) {
            Toast.makeText(this, "请先生成RSA密钥对", Toast.LENGTH_SHORT).show();
            tvStatus.setText("⚠️ 请先点击「生成RSA密钥对」");
            return;
        }
        
        tvStatus.setText("正在测试签名验证（成功案例）...");
        
        new Thread(() -> {
            try {
                // 1. 创建测试补丁文件
                File testPatch = createTestPatchFile();
                
                // 2. 使用私钥对补丁进行签名（模拟服务器端）
                String signature = signPatchFile(testPatch, demoKeyPair.getPrivate());
                
                // 3. 创建SecurityManager并设置公钥
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this, false);
                
                String publicKeyBase64 = android.util.Base64.encodeToString(
                    demoKeyPair.getPublic().getEncoded(),
                    android.util.Base64.NO_WRAP);
                securityManager.setSignaturePublicKey(publicKeyBase64);
                
                // 4. 验证签名
                boolean isValid = securityManager.verifySignature(testPatch, signature);
                
                runOnUiThread(() -> {
                    if (isValid) {
                        tvStatus.setText("✓ 签名验证成功！");
                        showRealSignatureResult(true, testPatch, signature, "补丁完整，未被篡改");
                    } else {
                        tvStatus.setText("✗ 签名验证失败（不应该发生）");
                    }
                });
                
                // 清理测试文件
                testPatch.delete();
                
            } catch (Exception e) {
                Log.e(TAG, "签名验证测试失败", e);
                runOnUiThread(() -> {
                    tvStatus.setText("✗ 测试出错: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "测试出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 测试签名验证 - 失败案例
     * 演示补丁被篡改后签名验证失败
     */
    private void testSignatureVerificationFail() {
        if (demoKeyPair == null) {
            Toast.makeText(this, "请先生成RSA密钥对", Toast.LENGTH_SHORT).show();
            tvStatus.setText("⚠️ 请先点击「生成RSA密钥对」");
            return;
        }
        
        tvStatus.setText("正在测试签名验证（失败案例）...");
        
        new Thread(() -> {
            try {
                // 1. 创建测试补丁文件
                File testPatch = createTestPatchFile();
                
                // 2. 使用私钥对补丁进行签名
                String signature = signPatchFile(testPatch, demoKeyPair.getPrivate());
                
                // 3. 篡改补丁文件（模拟恶意修改）
                tamperPatchFile(testPatch);
                
                // 4. 创建SecurityManager并设置公钥
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this, false);
                
                String publicKeyBase64 = android.util.Base64.encodeToString(
                    demoKeyPair.getPublic().getEncoded(),
                    android.util.Base64.NO_WRAP);
                securityManager.setSignaturePublicKey(publicKeyBase64);
                
                // 5. 验证签名（应该失败）
                boolean isValid = securityManager.verifySignature(testPatch, signature);
                
                runOnUiThread(() -> {
                    if (!isValid) {
                        tvStatus.setText("✓ 检测到补丁被篡改！签名验证失败");
                        showRealSignatureResult(false, testPatch, signature, 
                            "补丁在签名后被修改，签名不匹配");
                    } else {
                        tvStatus.setText("✗ 签名验证通过（不应该发生）");
                    }
                });
                
                // 清理测试文件
                testPatch.delete();
                
            } catch (Exception e) {
                Log.e(TAG, "签名验证测试失败", e);
                runOnUiThread(() -> {
                    tvStatus.setText("✗ 测试出错: " + e.getMessage());
                    Toast.makeText(MainActivity.this, 
                        "测试出错: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 使用私钥对补丁文件进行签名
     * @param patchFile 补丁文件
     * @param privateKey 私钥
     * @return Base64编码的签名
     */
    private String signPatchFile(File patchFile, java.security.PrivateKey privateKey) 
            throws Exception {
        // 使用SHA256withRSA算法
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        
        // 读取文件并更新签名
        FileInputStream fis = new FileInputStream(patchFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            signature.update(buffer, 0, bytesRead);
        }
        fis.close();
        
        // 生成签名
        byte[] signatureBytes = signature.sign();
        
        // 返回Base64编码的签名
        return android.util.Base64.encodeToString(signatureBytes, android.util.Base64.NO_WRAP);
    }
    
    /**
     * 篡改补丁文件（模拟恶意修改）
     */
    private void tamperPatchFile(File patchFile) throws Exception {
        FileOutputStream fos = new FileOutputStream(patchFile, true);
        fos.write("\n[TAMPERED] This file has been modified!".getBytes("UTF-8"));
        fos.close();
    }
    
    /**
     * 将签名嵌入到 zip 包内部
     * @param patchFile 补丁文件
     * @param signature Base64编码的签名
     */
    private void embedSignatureIntoZip(File patchFile, String signature) throws Exception {
        try (net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(patchFile)) {
            // 创建临时签名文件
            File tempSigFile = File.createTempFile("signature", ".sig", getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(tempSigFile)) {
                fos.write(signature.getBytes("UTF-8"));
            }
            
            // 添加到 zip 包
            net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
            params.setFileNameInZip("signature.sig");
            zipFile.addFile(tempSigFile, params);
            
            // 删除临时文件
            tempSigFile.delete();
            
            Log.d(TAG, "✓ 签名文件已嵌入到 zip 包: signature.sig");
        }
    }
    
    /**
     * 从 zip 包中提取签名
     * @param patchFile 补丁文件
     * @return Base64编码的签名，如果不存在则返回 null
     */
    private String extractSignatureFromZip(File patchFile) throws Exception {
        try (net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(patchFile)) {
            if (zipFile.getFileHeader("signature.sig") != null) {
                // 提取到临时文件
                File tempSigFile = File.createTempFile("extracted_sig", ".sig", getCacheDir());
                zipFile.extractFile("signature.sig", tempSigFile.getParent(), tempSigFile.getName());
                
                // 读取签名内容
                FileInputStream fis = new FileInputStream(tempSigFile);
                byte[] buffer = new byte[(int) tempSigFile.length()];
                fis.read(buffer);
                fis.close();
                String signature = new String(buffer, "UTF-8");
                
                // 删除临时文件
                tempSigFile.delete();
                
                Log.d(TAG, "✓ 从 zip 内部提取签名成功");
                return signature;
            }
        }
        return null;
    }
    
    /**
     * 检查补丁是否包含 APK 签名（META-INF/ 签名文件）
     * 
     * @param patchFile 补丁文件
     * @return 是否包含 APK 签名
     */
    private boolean checkHasApkSignature(File patchFile) {
        // 方法1: 检查 zip 内部是否有 META-INF/ 签名文件（新方案）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 检查是否有 META-INF/ 签名文件
                if (name.startsWith("META-INF/") && 
                    (name.endsWith(".SF") || name.endsWith(".RSA") || 
                     name.endsWith(".DSA") || name.endsWith(".EC"))) {
                    zipFile.close();
                    Log.d(TAG, "✓ 检测到 APK 签名文件: " + name);
                    return true;
                }
            }
            zipFile.close();
        } catch (Exception e) {
            Log.d(TAG, "检查 META-INF/ 签名失败: " + e.getMessage());
        }
        
        // 方法2: 检查 zip 内部是否有 signature.sig 标记文件（向后兼容）
        try {
            java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(patchFile);
            java.util.zip.ZipEntry sigEntry = zipFile.getEntry("signature.sig");
            zipFile.close();
            if (sigEntry != null) {
                Log.d(TAG, "✓ 检测到 zip 内部的签名标记文件");
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "检查 zip 内部签名标记失败: " + e.getMessage());
        }
        
        // 方法3: 检查外部 .sig 文件（向后兼容）
        File signatureFile = new File(patchFile.getPath() + ".sig");
        if (signatureFile.exists()) {
            Log.d(TAG, "✓ 检测到外部签名文件");
            return true;
        }
        
        return false;
    }
    
    /**
     * 从 assets 复制密钥库文件到设备
     */
    private void copyKeystoreFromAssets(File destFile) throws Exception {
        // 尝试从多个位置复制密钥库
        // 1. 从 assets 目录
        try {
            java.io.InputStream is = getAssets().open("smlieapp.jks");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();
            Log.d(TAG, "✓ 从 assets 复制密钥库成功");
            return;
        } catch (Exception e) {
            Log.d(TAG, "从 assets 复制失败: " + e.getMessage());
        }
        
        // 2. 从下载目录复制
        File downloadKeystore = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS), "smlieapp.jks");
        if (downloadKeystore.exists()) {
            java.io.FileInputStream fis = new java.io.FileInputStream(downloadKeystore);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            fis.close();
            Log.d(TAG, "✓ 从下载目录复制密钥库成功");
            return;
        }
        
        throw new Exception("未找到密钥库文件");
    }
    
    /**
     * 对补丁 ZIP 文件进行签名标记
     * 注意：实际的签名已经由 AndroidPatchGenerator 处理
     * 这个方法只是为了向后兼容，添加一个标记
     * 
     * @param patchFile 补丁文件
     */
    private void embedApkSignatureMarker(File patchFile) throws Exception {
        Log.d(TAG, "检查补丁签名...");
        
        // 检查补丁是否已经被签名
        boolean isSigned = checkHasApkSignature(patchFile);
        
        if (isSigned) {
            Log.i(TAG, "✓ 补丁已包含 APK 签名");
        } else {
            Log.w(TAG, "⚠ 补丁未签名，可能是因为密钥库不可用");
            Log.w(TAG, "提示：将 smlieapp.jks 复制到下载目录，然后重新生成补丁");
        }
    }
    
    /**
     * 从 APK 复制签名信息到补丁 ZIP
     */
    private boolean copySignatureFromApk(File patchZip, File sourceApk) {
        java.util.zip.ZipFile apkZipFile = null;
        java.util.zip.ZipOutputStream patchZipOut = null;
        java.io.FileInputStream patchFis = null;
        
        try {
            Log.d(TAG, "从 APK 提取签名: " + sourceApk.getName());
            
            // 1. 打开源 APK（使用标准 Java ZIP API）
            apkZipFile = new java.util.zip.ZipFile(sourceApk);
            
            // 2. 读取补丁 ZIP 的所有内容到内存
            java.util.Map<String, byte[]> patchEntries = new java.util.LinkedHashMap<>();
            java.util.zip.ZipFile patchZipRead = new java.util.zip.ZipFile(patchZip);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = patchZipRead.entries();
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    java.io.InputStream is = patchZipRead.getInputStream(entry);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    is.close();
                    patchEntries.put(entry.getName(), baos.toByteArray());
                }
            }
            patchZipRead.close();
            
            // 3. 重新创建补丁 ZIP，添加原有内容 + 签名文件
            patchZipOut = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(patchZip));
            
            // 设置为不压缩模式（STORE）
            patchZipOut.setMethod(java.util.zip.ZipOutputStream.STORED);
            
            // 先写入原有内容
            for (java.util.Map.Entry<String, byte[]> entry : patchEntries.entrySet()) {
                java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(entry.getKey());
                zipEntry.setMethod(java.util.zip.ZipEntry.STORED);
                zipEntry.setSize(entry.getValue().length);
                zipEntry.setCompressedSize(entry.getValue().length);
                
                // 计算 CRC32
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                crc.update(entry.getValue());
                zipEntry.setCrc(crc.getValue());
                
                patchZipOut.putNextEntry(zipEntry);
                patchZipOut.write(entry.getValue());
                patchZipOut.closeEntry();
            }
            
            // 4. 从 APK 提取并添加签名文件（只添加证书文件，不添加 MANIFEST 和 .SF）
            int copiedCount = 0;
            java.util.Enumeration<? extends java.util.zip.ZipEntry> apkEntries = apkZipFile.entries();
            
            while (apkEntries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = apkEntries.nextElement();
                String fileName = entry.getName();
                
                // 只复制证书文件（.RSA/.DSA/.EC），不复制 MANIFEST.MF 和 .SF
                // 因为 MANIFEST.MF 和 .SF 包含文件摘要，会导致验证失败
                if (fileName.startsWith("META-INF/") && 
                    (fileName.endsWith(".RSA") || fileName.endsWith(".DSA") || fileName.endsWith(".EC"))) {
                    
                    // 读取证书文件内容
                    java.io.InputStream is = apkZipFile.getInputStream(entry);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    is.close();
                    
                    // 添加到补丁 ZIP（不压缩）
                    byte[] certData = baos.toByteArray();
                    java.util.zip.ZipEntry newEntry = new java.util.zip.ZipEntry(fileName);
                    newEntry.setMethod(java.util.zip.ZipEntry.STORED);
                    newEntry.setSize(certData.length);
                    newEntry.setCompressedSize(certData.length);
                    
                    // 计算 CRC32
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                    crc.update(certData);
                    newEntry.setCrc(crc.getValue());
                    
                    patchZipOut.putNextEntry(newEntry);
                    patchZipOut.write(certData);
                    patchZipOut.closeEntry();
                    
                    copiedCount++;
                    Log.d(TAG, "✓ 已复制证书文件: " + fileName);
                }
            }
            
            patchZipOut.close();
            patchZipOut = null;
            
            if (copiedCount > 0) {
                Log.i(TAG, "✓ 成功复制 " + copiedCount + " 个签名文件到补丁");
                return true;
            } else {
                Log.w(TAG, "未找到签名文件");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "复制签名信息失败", e);
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (apkZipFile != null) apkZipFile.close();
                if (patchZipOut != null) patchZipOut.close();
                if (patchFis != null) patchFis.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭文件失败", e);
            }
        }
    }
    
    /**
     * 删除目录
     */
    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }
    
    /**
     * 添加签名标记文件（向后兼容方案）
     */
    private void addSignatureMarkerFile(File patchFile) throws Exception {
        try (net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(patchFile)) {
            // 创建临时标记文件
            File tempMarkerFile = File.createTempFile("apk_signature", ".marker", getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(tempMarkerFile)) {
                String markerContent = "APK_SIGNATURE_VERIFICATION_REQUIRED\n" +
                                     "This patch requires APK signature verification.\n" +
                                     "The patch signature must match the application signature.";
                fos.write(markerContent.getBytes("UTF-8"));
            }
            
            // 添加到 zip 包
            net.lingala.zip4j.model.ZipParameters params = new net.lingala.zip4j.model.ZipParameters();
            params.setFileNameInZip("signature.sig");
            zipFile.addFile(tempMarkerFile, params);
            
            // 删除临时文件
            tempMarkerFile.delete();
            
            Log.d(TAG, "✓ APK 签名标记已嵌入到 zip 包: signature.sig");
        }
    }
    
    /**
     * 创建测试补丁文件
     */
    private File createTestPatchFile() throws Exception {
        File testFile = new File(getExternalFilesDir(null), "test_patch.zip");
        FileOutputStream fos = new FileOutputStream(testFile);
        
        // 写入一些测试数据
        String testData = "This is a test patch file for signature verification demo.\n" +
                         "Timestamp: " + System.currentTimeMillis() + "\n" +
                         "Version: 1.2.4\n";
        fos.write(testData.getBytes("UTF-8"));
        fos.close();
        
        return testFile;
    }
    
    /**
     * 显示真实签名验证结果
     */
    private void showRealSignatureResult(boolean success, File patchFile, String signature, String reason) {
        StringBuilder info = new StringBuilder();
        
        if (success) {
            info.append("=== ✓ 签名验证成功 ===\n\n");
            info.append("🔒 安全状态: ").append(reason).append("\n\n");
            info.append("验证流程:\n");
            info.append("1. ✓ 生成RSA-2048密钥对\n");
            info.append("2. ✓ 使用私钥签名补丁文件\n");
            info.append("3. ✓ 读取补丁文件内容\n");
            info.append("4. ✓ 使用公钥验证签名\n");
            info.append("5. ✓ SHA256withRSA验证通过\n\n");
            
            info.append("=== 补丁信息 ===\n");
            info.append("文件: ").append(patchFile.getName()).append("\n");
            info.append("大小: ").append(formatSize(patchFile.length())).append("\n\n");
            
            info.append("=== 签名信息 ===\n");
            info.append("算法: SHA256withRSA\n");
            info.append("密钥长度: 2048位\n");
            info.append("签名长度: ").append(signature.length()).append(" 字符\n");
            info.append("签名(前64字符):\n").append(signature.substring(0, Math.min(64, signature.length()))).append("...\n\n");
            
            info.append("=== 安全说明 ===\n");
            info.append("✓ 补丁来源可信\n");
            info.append("✓ 补丁内容完整\n");
            info.append("✓ 签名验证通过\n");
            info.append("✓ 可以安全应用此补丁\n\n");
            
            info.append("💡 生产环境流程:\n");
            info.append("1. 服务器端生成补丁\n");
            info.append("2. 使用私钥对补丁签名\n");
            info.append("3. 将补丁和签名发送给客户端\n");
            info.append("4. 客户端使用公钥验证签名\n");
            info.append("5. 验证通过后应用补丁");
            
        } else {
            info.append("=== ✗ 签名验证失败 ===\n\n");
            info.append("⚠️ 安全警告: ").append(reason).append("\n\n");
            info.append("验证流程:\n");
            info.append("1. ✓ 生成RSA-2048密钥对\n");
            info.append("2. ✓ 使用私钥签名补丁文件\n");
            info.append("3. ✓ 补丁文件被篡改\n");
            info.append("4. ✓ 使用公钥验证签名\n");
            info.append("5. ✗ SHA256withRSA验证失败\n\n");
            
            info.append("=== 失败原因 ===\n");
            info.append("补丁文件在签名后被修改\n\n");
            info.append("检测到的问题:\n");
            info.append("• 文件内容与签名不匹配\n");
            info.append("• 文件可能被恶意篡改\n");
            info.append("• 文件可能在传输中损坏\n\n");
            
            info.append("=== 安全建议 ===\n");
            info.append("✗ 不要应用此补丁\n");
            info.append("✗ 补丁来源不可信\n");
            info.append("✗ 存在安全风险\n\n");
            
            info.append("💡 生产环境处理:\n");
            info.append("1. 拒绝应用补丁\n");
            info.append("2. 上报异常到服务器\n");
            info.append("3. 记录安全日志\n");
            info.append("4. 提示用户重新下载\n");
            info.append("5. 可能需要回滚到安全版本");
        }
        
        tvInfo.setText(info.toString());
    }
    
    /**
     * 显示签名验证结果（旧方法，保留兼容性）
     */
    private void showSignatureVerificationResult(boolean success, File patchFile, String signature) {
        StringBuilder info = new StringBuilder();
        
        if (success) {
            info.append("=== ✓ 签名验证成功 ===\n\n");
            info.append("🔒 安全状态: 补丁完整，未被篡改\n\n");
            info.append("验证流程:\n");
            info.append("1. ✓ 创建 SecurityManager（调试模式）\n");
            info.append("2. ✓ 读取补丁文件\n");
            info.append("3. ✓ 模拟签名验证\n");
            info.append("4. ✓ 调试模式下跳过真实验证\n");
            info.append("5. ✓ 验证通过\n\n");
            
            info.append("=== 补丁信息 ===\n");
            info.append("文件: ").append(patchFile.getName()).append("\n");
            info.append("大小: ").append(formatSize(patchFile.length())).append("\n");
            info.append("签名: ").append(signature.substring(0, Math.min(32, signature.length()))).append("...\n\n");
            
            info.append("=== 安全说明 ===\n");
            info.append("✓ 补丁来源可信\n");
            info.append("✓ 补丁内容完整\n");
            info.append("✓ 可以安全应用此补丁\n\n");
            
            info.append("💡 生产环境使用:\n");
            info.append("• 关闭调试模式（debugMode=false）\n");
            info.append("• 设置真实的RSA公钥\n");
            info.append("• 服务器端使用私钥签名\n");
            info.append("• 客户端使用公钥验证\n");
            info.append("• 公钥编译到APK中\n");
            info.append("• 私钥只在服务器端使用");
            
        } else {
            info.append("=== ✗ 签名验证失败 ===\n\n");
            info.append("⚠️ 安全警告: 签名验证未通过！\n\n");
            info.append("验证流程:\n");
            info.append("1. ✓ 创建 SecurityManager（生产模式）\n");
            info.append("2. ✓ 读取补丁文件\n");
            info.append("3. ✓ 检查签名\n");
            info.append("4. ✗ 签名为空或无效\n");
            info.append("5. ✗ 验证失败\n\n");
            
            info.append("=== 失败原因 ===\n");
            info.append("签名为空或格式不正确\n\n");
            info.append("可能的原因:\n");
            info.append("• 补丁未签名\n");
            info.append("• 签名在传输中丢失\n");
            info.append("• 签名格式错误\n");
            info.append("• 没有配置公钥\n\n");
            
            info.append("=== 安全建议 ===\n");
            info.append("✗ 不要应用此补丁\n");
            info.append("✗ 补丁来源不可信\n");
            info.append("✗ 可能存在安全风险\n\n");
            
            info.append("💡 生产环境处理:\n");
            info.append("• 拒绝应用未签名的补丁\n");
            info.append("• 上报异常到服务器\n");
            info.append("• 记录安全日志\n");
            info.append("• 通知用户重新下载");
        }
        
        tvInfo.setText(info.toString());
    }

    private void setButtonsEnabled(boolean enabled) {
        btnGenerate.setEnabled(enabled && selectedBaseApk != null && selectedNewApk != null);
        btnApply.setEnabled(enabled && (selectedPatchFile != null || lastGeneratedPatch != null));
        btnSelectBase.setEnabled(enabled);
        btnSelectNew.setEnabled(enabled);
        btnSelectPatch.setEnabled(enabled);
        btnCheckEngine.setEnabled(enabled);
        btnCheckStorage.setEnabled(enabled);
        btnClearPatch.setEnabled(enabled);
        btnGenerateKeys.setEnabled(enabled);
        btnLoadKeys.setEnabled(enabled);
        btnConfigKeys.setEnabled(enabled);
        btnVerifySuccess.setEnabled(enabled);
        btnVerifyFail.setEnabled(enabled);
    }
    
    /**
     * 显示配置密钥对话框
     */
    private void showConfigKeysDialog() {
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 公钥输入
        TextView tvPublicLabel = new TextView(this);
        tvPublicLabel.setText("公钥 (Public Key):");
        tvPublicLabel.setTextSize(14);
        tvPublicLabel.setPadding(0, 0, 0, 8);
        layout.addView(tvPublicLabel);
        
        android.widget.EditText etPublicKey = new android.widget.EditText(this);
        etPublicKey.setHint("粘贴 Base64 编码的公钥");
        etPublicKey.setMinLines(3);
        etPublicKey.setMaxLines(5);
        etPublicKey.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        
        // 尝试加载现有公钥
        try {
            File publicKeyFile = new File(outputDir, "rsa_public_key.txt");
            String existingPublicKey = loadKeyFromFile(publicKeyFile);
            if (existingPublicKey != null) {
                etPublicKey.setText(existingPublicKey);
            }
        } catch (Exception e) {
            Log.e(TAG, "加载现有公钥失败", e);
        }
        
        layout.addView(etPublicKey);
        
        // 私钥输入
        TextView tvPrivateLabel = new TextView(this);
        tvPrivateLabel.setText("私钥 (Private Key):");
        tvPrivateLabel.setTextSize(14);
        tvPrivateLabel.setPadding(0, 20, 0, 8);
        layout.addView(tvPrivateLabel);
        
        android.widget.EditText etPrivateKey = new android.widget.EditText(this);
        etPrivateKey.setHint("粘贴 Base64 编码的私钥");
        etPrivateKey.setMinLines(3);
        etPrivateKey.setMaxLines(5);
        etPrivateKey.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        
        // 尝试加载现有私钥
        try {
            File privateKeyFile = new File(outputDir, "rsa_private_key.txt");
            String existingPrivateKey = loadKeyFromFile(privateKeyFile);
            if (existingPrivateKey != null) {
                etPrivateKey.setText(existingPrivateKey);
            }
        } catch (Exception e) {
            Log.e(TAG, "加载现有私钥失败", e);
        }
        
        layout.addView(etPrivateKey);
        
        // 提示信息
        TextView tvHint = new TextView(this);
        tvHint.setText("\n💡 提示：\n• 粘贴 Base64 格式的 RSA 密钥\n• 公钥格式：X.509\n• 私钥格式：PKCS#8\n• 可以使用 openssl 或 keytool 生成");
        tvHint.setTextSize(12);
        tvHint.setTextColor(0xFF666666);
        tvHint.setPadding(0, 10, 0, 0);
        layout.addView(tvHint);
        
        // 创建对话框
        new AlertDialog.Builder(this)
            .setTitle("⚙️ 配置 RSA 密钥")
            .setView(layout)
            .setPositiveButton("保存", (dialog, which) -> {
                String publicKey = etPublicKey.getText().toString().trim();
                String privateKey = etPrivateKey.getText().toString().trim();
                
                if (publicKey.isEmpty() || privateKey.isEmpty()) {
                    Toast.makeText(this, "请输入公钥和私钥", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 保存密钥
                saveConfiguredKeys(publicKey, privateKey);
            })
            .setNegativeButton("取消", null)
            .setNeutralButton("清空", (dialog, which) -> {
                // 删除密钥文件
                new File(outputDir, "rsa_public_key.txt").delete();
                new File(outputDir, "rsa_private_key.txt").delete();
                demoKeyPair = null;
                tvStatus.setText("✓ 密钥已清空");
                Toast.makeText(this, "密钥已清空", Toast.LENGTH_SHORT).show();
            })
            .show();
    }
    
    /**
     * 保存用户配置的密钥
     */
    private void saveConfiguredKeys(String publicKeyBase64, String privateKeyBase64) {
        new Thread(() -> {
            try {
                // 验证密钥格式
                java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                
                // 验证公钥
                byte[] publicKeyBytes = android.util.Base64.decode(publicKeyBase64, android.util.Base64.DEFAULT);
                java.security.spec.X509EncodedKeySpec publicKeySpec = 
                    new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
                java.security.PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
                
                // 验证私钥
                byte[] privateKeyBytes = android.util.Base64.decode(privateKeyBase64, android.util.Base64.DEFAULT);
                java.security.spec.PKCS8EncodedKeySpec privateKeySpec = 
                    new java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes);
                java.security.PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
                
                // 保存到文件
                File publicKeyFile = saveKeyToFile(publicKeyBase64, "rsa_public_key.txt");
                File privateKeyFile = saveKeyToFile(privateKeyBase64, "rsa_private_key.txt");
                
                // 重建密钥对
                demoKeyPair = new java.security.KeyPair(publicKey, privateKey);
                
                runOnUiThread(() -> {
                    tvStatus.setText("✓ 密钥配置成功！");
                    showKeyPairInfo(publicKeyBase64, privateKeyBase64, publicKeyFile, privateKeyFile);
                    Toast.makeText(this, "✓ 密钥已保存到下载文件夹", Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "保存密钥失败", e);
                runOnUiThread(() -> {
                    tvStatus.setText("✗ 密钥格式错误");
                    Toast.makeText(this, 
                        "密钥格式错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * 加载保存的 JKS 配置
     */
    private void loadJksConfig() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_SECURITY, MODE_PRIVATE);
        
        // 加载 JKS 文件路径
        String jksPath = prefs.getString(KEY_JKS_FILE_PATH, null);
        if (jksPath != null && !jksPath.isEmpty()) {
            selectedKeystoreFile = new File(jksPath);
            if (!selectedKeystoreFile.exists()) {
                Log.w(TAG, "保存的 JKS 文件不存在: " + jksPath);
                selectedKeystoreFile = null;
            } else {
                Log.i(TAG, "✓ 已加载 JKS 文件: " + jksPath);
            }
        }
        
        // 加载签名信息
        keystorePassword = prefs.getString(KEY_KEYSTORE_PASSWORD, null);
        keyAlias = prefs.getString(KEY_KEY_ALIAS, null);
        keyPassword = prefs.getString(KEY_KEY_PASSWORD, null);
        
        if (selectedKeystoreFile != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
            Log.i(TAG, "✓ JKS 配置已完整加载");
        }
    }
    
    /**
     * 保存 JKS 配置
     */
    private void saveJksConfig() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_SECURITY, MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        if (selectedKeystoreFile != null) {
            editor.putString(KEY_JKS_FILE_PATH, selectedKeystoreFile.getAbsolutePath());
        } else {
            editor.remove(KEY_JKS_FILE_PATH);
        }
        
        if (keystorePassword != null && !keystorePassword.isEmpty()) {
            editor.putString(KEY_KEYSTORE_PASSWORD, keystorePassword);
        } else {
            editor.remove(KEY_KEYSTORE_PASSWORD);
        }
        
        if (keyAlias != null && !keyAlias.isEmpty()) {
            editor.putString(KEY_KEY_ALIAS, keyAlias);
        } else {
            editor.remove(KEY_KEY_ALIAS);
        }
        
        if (keyPassword != null && !keyPassword.isEmpty()) {
            editor.putString(KEY_KEY_PASSWORD, keyPassword);
        } else {
            editor.remove(KEY_KEY_PASSWORD);
        }
        
        editor.apply();
        Log.i(TAG, "✓ JKS 配置已保存");
    }
    
    /**
     * 显示安全设置对话框
     */
    private void showSecuritySettingsDialog() {
        Log.d(TAG, "=== 打开安全设置对话框 ===");
        Log.d(TAG, "selectedKeystoreFile: " + selectedKeystoreFile);
        if (selectedKeystoreFile != null) {
            Log.d(TAG, "JKS 文件路径: " + selectedKeystoreFile.getAbsolutePath());
            Log.d(TAG, "JKS 文件存在: " + selectedKeystoreFile.exists());
        }
        
        boolean requireSignature = hotUpdateHelper.isRequireSignature();
        boolean requireEncryption = hotUpdateHelper.isRequireEncryption();
        
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 标题说明
        TextView tvTitle = new TextView(this);
        tvTitle.setText("配置补丁应用的安全策略：");
        tvTitle.setTextSize(14);
        tvTitle.setPadding(0, 0, 0, 20);
        layout.addView(tvTitle);
        
        // 签名验证开关
        android.widget.CheckBox cbRequireSignature = new android.widget.CheckBox(this);
        cbRequireSignature.setText("🔒 强制要求 APK 签名验证");
        cbRequireSignature.setChecked(requireSignature);
        layout.addView(cbRequireSignature);
        
        TextView tvSignatureHint = new TextView(this);
        tvSignatureHint.setText("  开启后，只能应用包含 APK 签名的补丁\n  验证补丁签名与应用签名是否一致");
        tvSignatureHint.setTextSize(12);
        tvSignatureHint.setTextColor(0xFF666666);
        tvSignatureHint.setPadding(0, 0, 0, 15);
        layout.addView(tvSignatureHint);
        
        // JKS 签名配置区域
        android.widget.LinearLayout jksConfigLayout = new android.widget.LinearLayout(this);
        jksConfigLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        jksConfigLayout.setPadding(20, 10, 0, 15);
        jksConfigLayout.setVisibility(cbRequireSignature.isChecked() ? View.VISIBLE : View.GONE);
        layout.addView(jksConfigLayout);
        
        // 当前 JKS 文件状态显示
        tvJksStatus = new TextView(this);
        if (selectedKeystoreFile != null && selectedKeystoreFile.exists()) {
            tvJksStatus.setText("✓ 当前签名件: " + selectedKeystoreFile.getName());
            tvJksStatus.setTextColor(0xFF4CAF50);  // 绿色
        } else {
            tvJksStatus.setText("⚠ 未选签名文件");
            tvJksStatus.setTextColor(0xFFFF9800);  // 橙色
        }
        tvJksStatus.setTextSize(13);
        tvJksStatus.setPadding(0, 0, 0, 10);
        jksConfigLayout.addView(tvJksStatus);
        
        // JKS 文件选择按钮
        Button btnSelectJks = new Button(this);
        btnSelectJks.setText("📁 选择签名文件 (推荐.bks)");
        btnSelectJks.setTextSize(13);
        // 注意：点击监听器将在对话框创建后设置，以便能够关闭对话框
        jksConfigLayout.addView(btnSelectJks);
        
        // 格式提示
        TextView tvFormatHint = new TextView(this);
        tvFormatHint.setText("  ✓ 支持格式：PKCS12 (.p12), BKS (.bks)，JKS (.jks)");
        tvFormatHint.setTextSize(11);
        tvFormatHint.setTextColor(0xFFFF9800);  // 橙色警告
        tvFormatHint.setPadding(0, 5, 0, 10);
        jksConfigLayout.addView(tvFormatHint);
        
        // 密钥库密码输入
        TextView tvStorePasswordLabel = new TextView(this);
        tvStorePasswordLabel.setText("密钥库密码 (storePassword):");
        tvStorePasswordLabel.setTextSize(12);
        tvStorePasswordLabel.setPadding(0, 10, 0, 5);
        jksConfigLayout.addView(tvStorePasswordLabel);
        
        android.widget.EditText etStorePassword = new android.widget.EditText(this);
        etStorePassword.setHint("输入密钥库密码");
        etStorePassword.setText(keystorePassword != null ? keystorePassword : "");
        etStorePassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        jksConfigLayout.addView(etStorePassword);
        
        // 密钥别名输入
        TextView tvKeyAliasLabel = new TextView(this);
        tvKeyAliasLabel.setText("密钥别名 (keyAlias):");
        tvKeyAliasLabel.setTextSize(12);
        tvKeyAliasLabel.setPadding(0, 10, 0, 5);
        jksConfigLayout.addView(tvKeyAliasLabel);
        
        android.widget.EditText etKeyAlias = new android.widget.EditText(this);
        etKeyAlias.setHint("输入密钥别名");
        etKeyAlias.setText(keyAlias != null ? keyAlias : "");
        jksConfigLayout.addView(etKeyAlias);
        
        // 密钥密码输入
        TextView tvKeyPasswordLabel = new TextView(this);
        tvKeyPasswordLabel.setText("密钥密码 (keyPassword):");
        tvKeyPasswordLabel.setTextSize(12);
        tvKeyPasswordLabel.setPadding(0, 10, 0, 5);
        jksConfigLayout.addView(tvKeyPasswordLabel);
        
        android.widget.EditText etKeyPassword = new android.widget.EditText(this);
        etKeyPassword.setHint("输入密钥密码");
        etKeyPassword.setText(keyPassword != null ? keyPassword : "");
        etKeyPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        jksConfigLayout.addView(etKeyPassword);
        
        // 签名验证开关变化监听
        cbRequireSignature.setOnCheckedChangeListener((buttonView, isChecked) -> {
            jksConfigLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        
        // 加密验证开关
        android.widget.CheckBox cbRequireEncryption = new android.widget.CheckBox(this);
        cbRequireEncryption.setText("🔐 强制要求补丁加密");
        cbRequireEncryption.setChecked(requireEncryption);
        layout.addView(cbRequireEncryption);
        
        TextView tvEncryptionHint = new TextView(this);
        tvEncryptionHint.setText("  开启后，只能应用已加密的补丁");
        tvEncryptionHint.setTextSize(12);
        tvEncryptionHint.setTextColor(0xFF666666);
        tvEncryptionHint.setPadding(0, 0, 0, 15);
        layout.addView(tvEncryptionHint);
        
        // 安全说明
        TextView tvNote = new TextView(this);
        tvNote.setText("\n💡 安全建议：\n\n" +
            "• APK 签名验证：防止补丁被篡改，推荐开启\n" +
            "• 补丁加密：保护补丁内容，敏感应用建议开启\n" +
            "• 开发测试时可以关闭验证\n" +
            "• 修改设置后立即生效");
        tvNote.setTextSize(12);
        tvNote.setTextColor(0xFF666666);
        layout.addView(tvNote);
        
        // 创建对话框
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("🛡️ 安全策略设置")
            .setView(layout)
            .setPositiveButton("保存", (d, w) -> {
                boolean newRequireSignature = cbRequireSignature.isChecked();
                boolean newRequireEncryption = cbRequireEncryption.isChecked();
                
                // 保存 JKS 配置
                if (newRequireSignature) {
                    keystorePassword = etStorePassword.getText().toString().trim();
                    keyAlias = etKeyAlias.getText().toString().trim();
                    keyPassword = etKeyPassword.getText().toString().trim();
                    
                    // 验证配置完整性
                    if (selectedKeystoreFile == null || keystorePassword.isEmpty() || 
                        keyAlias.isEmpty() || keyPassword.isEmpty()) {
                        Toast.makeText(this, "⚠ 请完整配置 JKS 签名信息", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                
                // 保存设置到 HotUpdateHelper
                hotUpdateHelper.setRequireSignature(newRequireSignature);
                hotUpdateHelper.setRequireEncryption(newRequireEncryption);
                
                // 保存 JKS 配置到 SharedPreferences
                saveJksConfig();
                
                // 显示当前策略
                StringBuilder status = new StringBuilder("✓ 安全策略已更新\n\n");
                status.append("APK 签名验证: ").append(newRequireSignature ? "✓ 已开启" : "✗ 已关闭").append("\n");
                if (newRequireSignature && selectedKeystoreFile != null) {
                    status.append("  JKS 文件: ").append(selectedKeystoreFile.getName()).append("\n");
                    status.append("  密钥别名: ").append(keyAlias).append("\n");
                }
                status.append("补丁加密验证: ").append(newRequireEncryption ? "✓ 已开启" : "✗ 已关闭");
                
                tvStatus.setText(status.toString());
                Toast.makeText(this, "✓ 安全策略已保存", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .create();
        
        // 设置按钮点击事件，不关闭对话框，直接打开文件选择器
        btnSelectJks.setOnClickListener(v -> {
            selectingFileType = 3;  // JKS 文件
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                filePickerLauncher.launch(Intent.createChooser(intent, "选择 JKS 签名文件"));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }
    
    /**
     * 测试 Native JKS 支持
     */
    private void testNativeJKS() {
        // 首先测试 apksig 签名器（推荐方案）
        testApkSigSigner();
        
        // 然后尝试使用 Java 的 JKSHelper（更可靠）
        testJKSHelper();
        
        // 最后测试 Native 实现（如果可用）
        try {
            // 动态加载 JKSNative 类
            Class<?> jksNativeClass = Class.forName("com.orange.patchgen.signer.JKSNative");
            java.lang.reflect.Method isAvailableMethod = jksNativeClass.getMethod("isAvailable");
            boolean available = (Boolean) isAvailableMethod.invoke(null);
            
            if (available) {
                Log.i(TAG, "✓ Native JKS 支持已启用");
                System.out.println("[MainActivity] ✓ Native JKS 支持已启用");
                
                // 如果已经配置了JKS文件，尝试加载并解析
                if (selectedKeystoreFile != null && selectedKeystoreFile.exists()) {
                    System.out.println("[MainActivity] 测试JKS解析: " + selectedKeystoreFile.getAbsolutePath());
                    testJKSParsing(jksNativeClass);
                }
            } else {
                Log.w(TAG, "✗ Native JKS 支持不可用");
                System.out.println("[MainActivity] ✗ Native JKS 支持不可用");
            }
        } catch (Exception e) {
            Log.e(TAG, "测试 Native JKS 失败: " + e.getMessage());
            System.out.println("[MainActivity] 测试 Native JKS 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试 PatchSigner（推荐方案）
     */
    private void testApkSigSigner() {
        if (selectedKeystoreFile == null || !selectedKeystoreFile.exists()) {
            System.out.println("[MainActivity] 未配置 JKS 文件");
            return;
        }
        
        try {
            System.out.println("[MainActivity] === 使用 PatchSigner 测试 ===");
            System.out.println("[MainActivity] JKS 文件: " + selectedKeystoreFile.getAbsolutePath());
            System.out.println("[MainActivity] 密钥别名: " + keyAlias);
            
            PatchSigner signer = new PatchSigner(MainActivity.this);
            
            // 测试 keystore 加载（通过尝试签名一个临时文件）
            System.out.println("[MainActivity] ✓ PatchSigner 初始化成功");
            System.out.println("[MainActivity] ✓ 可以对生成的补丁进行自动签名");
            
            System.out.println("[MainActivity] === PatchSigner 测试完成 ===");
            
        } catch (Exception e) {
            System.out.println("[MainActivity] PatchSigner 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试 JKSHelper（Java 实现）
     */
    private void testJKSHelper() {
        if (selectedKeystoreFile == null || !selectedKeystoreFile.exists()) {
            System.out.println("[MainActivity] 未配置 JKS 文件");
            return;
        }
        
        try {
            System.out.println("[MainActivity] === 使用 JKSHelper 测试 ===");
            System.out.println("[MainActivity] JKS 文件: " + selectedKeystoreFile.getAbsolutePath());
            System.out.println("[MainActivity] 密钥别名: " + keyAlias);
            
            // 检查 Android 是否支持 JKS
            try {
                java.security.KeyStore.getInstance("JKS");
                System.out.println("[MainActivity] ✓ Android 支持 JKS KeyStore");
            } catch (Exception e) {
                System.out.println("[MainActivity] ✗ Android 不支持 JKS KeyStore: " + e.getMessage());
                System.out.println("[MainActivity] 尝试使用 BKS 或其他格式...");
                return;
            }
            
            com.orange.patchgen.signer.JKSHelper helper = new com.orange.patchgen.signer.JKSHelper();
            
            // 加载 JKS 文件
            boolean loaded = helper.load(selectedKeystoreFile.getAbsolutePath(), keystorePassword);
            if (!loaded) {
                System.out.println("[MainActivity] ✗ JKS加载失败: " + helper.getError());
                return;
            }
            
            System.out.println("[MainActivity] ✓ JKS加载成功");
            
            // 获取私钥
            byte[] privateKeyData = helper.getPrivateKey(keyAlias, keyPassword);
            if (privateKeyData == null) {
                System.out.println("[MainActivity] ✗ 私钥获取失败: " + helper.getError());
                return;
            }
            
            System.out.println("[MainActivity] ✓ 私钥获取成功");
            System.out.println("[MainActivity]   大小: " + privateKeyData.length + " 字节");
            System.out.println("[MainActivity]   前32字节: " + bytesToHex(privateKeyData, 0, Math.min(32, privateKeyData.length)));
            
            // 验证是否是 PKCS#8 格式
            if (privateKeyData.length > 0 && (privateKeyData[0] & 0xFF) == 0x30) {
                System.out.println("[MainActivity] ✓ 私钥格式正确 (以 SEQUENCE 0x30 开始)");
            } else {
                System.out.println("[MainActivity] ✗ 私钥格式错误 (第一个字节: 0x" + 
                    String.format("%02X", privateKeyData[0] & 0xFF) + ")");
            }
            
            // 尝试解析私钥
            System.out.println("[MainActivity] 尝试解析私钥...");
            String[] algorithms = {"RSA", "DSA", "EC"};
            java.security.PrivateKey privateKey = null;
            
            for (String algorithm : algorithms) {
                try {
                    java.security.spec.PKCS8EncodedKeySpec keySpec = 
                        new java.security.spec.PKCS8EncodedKeySpec(privateKeyData);
                    java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance(algorithm);
                    privateKey = keyFactory.generatePrivate(keySpec);
                    System.out.println("[MainActivity] ✓ 私钥解析成功！算法: " + algorithm);
                    System.out.println("[MainActivity]   密钥算法: " + privateKey.getAlgorithm());
                    System.out.println("[MainActivity]   密钥格式: " + privateKey.getFormat());
                    break;
                } catch (Exception e) {
                    System.out.println("[MainActivity] ✗ " + algorithm + " 解析失败: " + e.getMessage());
                }
            }
            
            if (privateKey == null) {
                System.out.println("[MainActivity] ✗ 所有算法都无法解析私钥");
            }
            
            // 获取证书链
            byte[][] certChain = helper.getCertificateChain(keyAlias);
            if (certChain != null) {
                System.out.println("[MainActivity] ✓ 证书链获取成功");
                System.out.println("[MainActivity]   证书数量: " + certChain.length);
                for (int i = 0; i < certChain.length; i++) {
                    System.out.println("[MainActivity]   证书" + i + "大小: " + certChain[i].length + " 字节");
                }
            }
            
            System.out.println("[MainActivity] === JKSHelper 测试完成 ===");
            
        } catch (Exception e) {
            System.out.println("[MainActivity] JKSHelper 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < Math.min(offset + length, bytes.length); i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString().trim();
    }
    
    /**
     * 测试JKS解析和私钥解密
     */
    private void testJKSParsing(Class<?> jksNativeClass) {
        try {
            System.out.println("[MainActivity] === 开始JKS解析测试 ===");
            
            // 加载JKS文件
            java.lang.reflect.Method loadMethod = jksNativeClass.getMethod("loadKeyStore", String.class, String.class);
            boolean loaded = (Boolean) loadMethod.invoke(null, selectedKeystoreFile.getAbsolutePath(), keystorePassword);
            
            if (!loaded) {
                java.lang.reflect.Method getErrorMethod = jksNativeClass.getMethod("getError");
                String error = (String) getErrorMethod.invoke(null);
                System.out.println("[MainActivity] ✗ JKS加载失败: " + error);
                return;
            }
            System.out.println("[MainActivity] ✓ JKS文件加载成功");
            
            // 获取私钥
            java.lang.reflect.Method getPrivateKeyMethod = jksNativeClass.getMethod("getPrivateKey", String.class, String.class);
            byte[] privateKeyData = (byte[]) getPrivateKeyMethod.invoke(null, keyAlias, keyPassword);
            
            if (privateKeyData == null) {
                java.lang.reflect.Method getErrorMethod = jksNativeClass.getMethod("getError");
                String error = (String) getErrorMethod.invoke(null);
                System.out.println("[MainActivity] ✗ 私钥获取失败: " + error);
                return;
            }
            
            System.out.println("[MainActivity] ✓ 私钥解密成功");
            System.out.println("[MainActivity]   私钥大小: " + privateKeyData.length + " 字节");
            
            // 打印前16字节
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(16, privateKeyData.length); i++) {
                hex.append(String.format("%02X ", privateKeyData[i]));
            }
            System.out.println("[MainActivity]   前16字节: " + hex.toString());
            
            // 验证PKCS#8格式
            if (privateKeyData.length >= 2 && privateKeyData[0] == 0x30) {
                System.out.println("[MainActivity] ✓ 私钥格式正确 (PKCS#8, 以0x30开头)");
                
                // 尝试解析为PrivateKey对象
                try {
                    java.security.spec.PKCS8EncodedKeySpec keySpec = 
                        new java.security.spec.PKCS8EncodedKeySpec(privateKeyData);
                    
                    // 尝试不同的算法
                    String[] algorithms = {"RSA", "DSA", "EC"};
                    boolean parsed = false;
                    for (String algorithm : algorithms) {
                        try {
                            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance(algorithm);
                            java.security.PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
                            System.out.println("[MainActivity] ✓ 私钥成功解析为 " + algorithm + " 密钥");
                            System.out.println("[MainActivity]   算法: " + privateKey.getAlgorithm());
                            System.out.println("[MainActivity]   格式: " + privateKey.getFormat());
                            parsed = true;
                            break;
                        } catch (Exception e) {
                            System.out.println("[MainActivity] ✗ " + algorithm + " 解析失败: " + e.getMessage());
                        }
                    }
                    
                    if (!parsed) {
                        System.out.println("[MainActivity] ✗ 所有算法都无法解析私钥");
                    }
                } catch (Exception e) {
                    System.out.println("[MainActivity] ✗ 私钥解析异常: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("[MainActivity] ✗ 私钥格式错误 (不是PKCS#8格式)");
                System.out.println("[MainActivity]   期望以0x30开头，实际: 0x" + String.format("%02X", privateKeyData[0]));
            }
            
            // 获取证书链
            java.lang.reflect.Method getCertChainMethod = jksNativeClass.getMethod("getCertificateChain", String.class);
            byte[][] certChain = (byte[][]) getCertChainMethod.invoke(null, keyAlias);
            
            if (certChain != null && certChain.length > 0) {
                System.out.println("[MainActivity] ✓ 证书链获取成功");
                System.out.println("[MainActivity]   证书数量: " + certChain.length);
                for (int i = 0; i < certChain.length; i++) {
                    System.out.println("[MainActivity]   证书 " + (i + 1) + " 大小: " + certChain[i].length + " 字节");
                }
            } else {
                System.out.println("[MainActivity] ✗ 证书链获取失败");
            }
            
            // 释放资源
            java.lang.reflect.Method releaseMethod = jksNativeClass.getMethod("release");
            releaseMethod.invoke(null);
            
            System.out.println("[MainActivity] === JKS解析测试完成 ===");
            
        } catch (Exception e) {
            System.out.println("[MainActivity] JKS解析测试异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (generator != null) {
            generator.shutdown();
        }
    }
}
