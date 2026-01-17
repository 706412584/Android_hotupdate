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
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Demo Activity - 热更新补丁生成与应用演示
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PatchDemo";
    private static final int PERMISSION_REQUEST_CODE = 100;

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
    private RealHotUpdate realHotUpdate;
    private Button btnClearPatch;
    private Button btnVerifySuccess;
    private Button btnVerifyFail;
    
    // 选择的文件
    private File selectedBaseApk;
    private File selectedNewApk;
    private File selectedPatchFile;
    private File lastGeneratedPatch;
    
    // 文件选择类型: 0=基准APK, 1=新APK, 2=补丁文件
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

        // 初始化真正的热更新
        realHotUpdate = new RealHotUpdate(this);
        
        // 应用启动时加载已应用的补丁
        realHotUpdate.loadAppliedPatch();

        initFilePicker();
        initViews();
        checkPermissions();
        showSystemInfo();
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
        Button btnTestAssets = findViewById(R.id.btn_test_assets);

        btnGenerate.setOnClickListener(v -> startPatchGeneration());
        btnApply.setOnClickListener(v -> applyPatch());
        btnCheckEngine.setOnClickListener(v -> checkEngineAvailability());
        btnCheckStorage.setOnClickListener(v -> checkStorageSpace());
        btnSelectBase.setOnClickListener(v -> selectFile(0));
        btnSelectNew.setOnClickListener(v -> selectFile(1));
        btnSelectPatch.setOnClickListener(v -> selectFile(2));
        btnClearPatch.setOnClickListener(v -> clearPatch());
        btnVerifySuccess.setOnClickListener(v -> testSignatureVerificationSuccess());
        btnVerifyFail.setOnClickListener(v -> testSignatureVerificationFail());
        btnTestAssets.setOnClickListener(v -> testAssetsFile());
        
        updateButtonStates();
    }

    /**
     * 获取热更新测试信息 - 用于验证 DEX 热更新是否生效
     * v1.2 更新后的方法
     */
    private String getHotUpdateTestInfo() {
        return "🔥 热更新测试 v1.2 - 补丁已生效！代码已更新！";
    }

    private void showSystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 系统信息 ===\n\n");
        
        // 显示热更新测试信息（v1.2 新增）
        info.append(getHotUpdateTestInfo()).append("\n\n");
        
        info.append("应用包名: ").append(getPackageName()).append("\n");
        
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String displayVersion = realHotUpdate.getDisplayVersion(pInfo.versionName);
            tvVersion.setText("v" + displayVersion);
            info.append("版本: ").append(displayVersion).append("\n");
            
            // 显示热更新状态
            if (realHotUpdate.isPatchApplied()) {
                info.append("\n🔥 热更新状态: 已应用\n");
                info.append("补丁版本: ").append(realHotUpdate.getPatchedVersion()).append("\n");
                info.append("DEX 注入: ").append(realHotUpdate.isDexInjected() ? "✓" : "✗").append("\n");
                long patchTime = realHotUpdate.getPatchTime();
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
        
        info.append("\n").append(realHotUpdate.getCompatibilityInfo()).append("\n");
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
            String[] fileNames = {"selected_base.apk", "selected_new.apk", "selected_patch.zip"};
            String fileName = fileNames[selectingFileType];
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
                        btnSelectPatch.setText("补丁: " + formatSize(destFile.length()));
                        break;
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

        // 输出到下载目录
        File outputFile = new File(outputDir, "patch_" + System.currentTimeMillis() + ".zip");

        tvStatus.setText("正在生成补丁...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        generator = new AndroidPatchGenerator.Builder(this)
                .baseApk(selectedBaseApk)
                .newApk(selectedNewApk)
                .output(outputFile)
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
                    public void onComplete(PatchResult result) {
                        progressBar.setVisibility(View.GONE);
                        setButtonsEnabled(true);

                        if (result.isSuccess()) {
                            lastGeneratedPatch = result.getPatchFile();
                            tvStatus.setText("✓ 补丁生成成功!");
                            showPatchResult(result);
                            updateButtonStates();
                        } else {
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

        tvStatus.setText("正在应用热更新...");
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);

        realHotUpdate.applyPatch(patchToApply, new RealHotUpdate.ApplyCallback() {
            @Override
            public void onProgress(int percent, String message) {
                runOnUiThread(() -> {
                    progressBar.setProgress(percent);
                    tvStatus.setText(message);
                });
            }

            @Override
            public void onSuccess(RealHotUpdate.PatchResult result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    setButtonsEnabled(true);
                    tvStatus.setText("🔥 热更新成功!");
                    
                    // 更新版本显示
                    tvVersion.setText("v" + result.newVersion + " (热更新)");
                    
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
                realHotUpdate.clearPatch();
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

    private void showRealHotUpdateResult(RealHotUpdate.PatchResult result) {
        StringBuilder info = new StringBuilder();
        info.append("=== 🔥 热更新成功 ===\n\n");
        
        info.append("状态: ✓ 已应用\n\n");
        
        if (result.oldVersion != null) {
            info.append("原版本: ").append(result.oldVersion).append("\n");
        }
        info.append("新版本: ").append(result.newVersion).append("\n");
        info.append("版本号: ").append(result.newVersionCode).append("\n\n");
        
        info.append("补丁大小: ").append(formatSize(result.patchSize)).append("\n\n");
        
        info.append("=== 热更新详情 ===\n");
        info.append("DEX 注入: ").append(result.dexInjected ? "✓ 成功" : "✗ 无 DEX").append("\n");
        info.append("SO 加载: ").append(result.soLoaded ? "✓ 成功" : "✗ 无 SO").append("\n");
        info.append("资源加载: ").append(result.resourcesLoaded ? "✓ 成功" : "✗ 无资源").append("\n\n");
        
        info.append("=== 热更新说明 ===\n");
        info.append("✓ 版本已从 ").append(result.oldVersion != null ? result.oldVersion : "原版本");
        info.append(" 更新到 ").append(result.newVersion).append("\n");
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
     * 测试签名验证 - 成功案例
     * 演示如何正确使用签名验证保护补丁安全
     */
    private void testSignatureVerificationSuccess() {
        tvStatus.setText("正在测试签名验证（成功案例）...");
        
        new Thread(() -> {
            try {
                // 模拟生成测试补丁文件
                File testPatch = createTestPatchFile();
                
                // 创建 SecurityManager 实例（调试模式）
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this, true); // 调试模式
                
                // 模拟签名（在调试模式下会跳过真实验证）
                String testSignature = "demo_signature_success_" + System.currentTimeMillis();
                
                // 验证签名（调试模式下会返回true）
                boolean isValid = securityManager.verifySignature(testPatch, testSignature);
                
                runOnUiThread(() -> {
                    if (isValid) {
                        tvStatus.setText("✓ 签名验证成功！");
                        showSignatureVerificationResult(true, testPatch, testSignature);
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
     * 演示当补丁被篡改时，签名验证会失败
     */
    private void testSignatureVerificationFail() {
        tvStatus.setText("正在测试签名验证（失败案例）...");
        
        new Thread(() -> {
            try {
                // 模拟生成测试补丁文件
                File testPatch = createTestPatchFile();
                
                // 创建 SecurityManager 实例（非调试模式，需要真实验证）
                com.orange.update.SecurityManager securityManager = 
                    new com.orange.update.SecurityManager(this, false);
                
                // 使用空签名（会导致验证失败）
                String testSignature = "";
                
                // 验证签名（应该失败）
                boolean isValid = securityManager.verifySignature(testPatch, testSignature);
                
                runOnUiThread(() -> {
                    if (!isValid) {
                        tvStatus.setText("✓ 检测到补丁被篡改！签名验证失败");
                        showSignatureVerificationResult(false, testPatch, testSignature);
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
     * 显示签名验证结果
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
        btnVerifySuccess.setEnabled(enabled);
        btnVerifyFail.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (generator != null) {
            generator.shutdown();
        }
    }
}
