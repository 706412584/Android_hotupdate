package com.orange.update.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.orange.update.R;
import com.orange.update.helper.DialogHelper;
import com.orange.update.helper.FilePickerHelper;
import com.orange.update.helper.FormatHelper;
import com.orange.update.viewmodel.PatchGenerateViewModel;

import java.io.File;

/**
 * 补丁生成 Fragment
 * 负责补丁生成相关的 UI 和交互
 */
public class PatchGenerateFragment extends Fragment {
    
    private PatchGenerateViewModel viewModel;
    private FilePickerHelper filePickerHelper;
    
    // UI 组件
    private TextView tvStatus;
    private TextView tvInfo;
    private ProgressBar progressBar;
    private Button btnSelectBase;
    private Button btnSelectNew;
    private Button btnGenerate;
    
    // 文件选择类型
    private int selectingFileType = 0; // 0=基准APK, 1=新APK
    
    // 文件选择器
    private ActivityResultLauncher<Intent> filePickerLauncher;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化 ViewModel
        viewModel = new ViewModelProvider(this).get(PatchGenerateViewModel.class);
        
        // 初始化文件选择器
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePickerHelper != null) {
                    filePickerHelper.handleResult(result);
                }
            }
        );
        
        filePickerHelper = new FilePickerHelper(requireActivity(), filePickerLauncher);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_patch_generate, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupListeners();
        observeViewModel();
        updateFileInfo();
    }
    
    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tv_status);
        tvInfo = view.findViewById(R.id.tv_info);
        progressBar = view.findViewById(R.id.progress_bar);
        btnSelectBase = view.findViewById(R.id.btn_select_base);
        btnSelectNew = view.findViewById(R.id.btn_select_new);
        btnGenerate = view.findViewById(R.id.btn_generate);
    }
    
    private void setupListeners() {
        btnSelectBase.setOnClickListener(v -> selectBaseApk());
        btnSelectNew.setOnClickListener(v -> selectNewApk());
        btnGenerate.setOnClickListener(v -> generatePatch());
    }
    
    private void observeViewModel() {
        // 观察生成进度
        viewModel.getGenerationProgress().observe(getViewLifecycleOwner(), progress -> {
            progressBar.setProgress(progress);
        });
        
        // 观察生成状态
        viewModel.getGenerationStatus().observe(getViewLifecycleOwner(), status -> {
            tvStatus.setText(status);
        });
        
        // 观察生成结果
        viewModel.getGenerationResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                if (result.isSuccess()) {
                    showSuccessResult(result);
                } else {
                    DialogHelper.showErrorDialog(requireContext(), "生成失败", 
                        result.getErrorMessage());
                }
            }
        });
        
        // 观察生成状态
        viewModel.getIsGenerating().observe(getViewLifecycleOwner(), isGenerating -> {
            progressBar.setVisibility(isGenerating ? View.VISIBLE : View.GONE);
            btnGenerate.setEnabled(!isGenerating && viewModel.canGenerate());
            btnSelectBase.setEnabled(!isGenerating);
            btnSelectNew.setEnabled(!isGenerating);
        });
    }
    
    private void selectBaseApk() {
        selectingFileType = 0;
        filePickerHelper.pickApkFile(new FilePickerHelper.FilePickerCallback() {
            @Override
            public void onFileSelected(Uri uri, File destFile) {
                viewModel.setBaseApk(destFile);
                btnSelectBase.setText("基准: " + FormatHelper.getApkInfo(requireContext(), destFile));
                updateFileInfo();
                updateButtonStates();
            }
            
            @Override
            public void onError(String message) {
                DialogHelper.showToast(requireContext(), message);
            }
        });
    }
    
    private void selectNewApk() {
        selectingFileType = 1;
        filePickerHelper.pickApkFile(new FilePickerHelper.FilePickerCallback() {
            @Override
            public void onFileSelected(Uri uri, File destFile) {
                viewModel.setNewApk(destFile);
                btnSelectNew.setText("新版: " + FormatHelper.getApkInfo(requireContext(), destFile));
                updateFileInfo();
                updateButtonStates();
            }
            
            @Override
            public void onError(String message) {
                DialogHelper.showToast(requireContext(), message);
            }
        });
    }
    
    private void generatePatch() {
        if (!viewModel.canGenerate()) {
            DialogHelper.showToast(requireContext(), "请先选择两个 APK 文件");
            return;
        }
        
        // 显示签名/加密选项对话框
        showSignPatchDialog();
    }
    
    /**
     * 显示签名补丁选项对话框
     */
    private void showSignPatchDialog() {
        // 创建对话框布局
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        // 标题文本
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("请选择安全选项：");
        tvTitle.setTextSize(14);
        tvTitle.setPadding(0, 0, 0, 20);
        layout.addView(tvTitle);
        
        // APK 签名验证选项（推荐）
        android.widget.CheckBox cbApkSign = new android.widget.CheckBox(requireContext());
        cbApkSign.setText("🔒 APK 签名验证（推荐）");
        cbApkSign.setChecked(true);  // 默认选中
        layout.addView(cbApkSign);
        
        TextView tvApkSignHint = new TextView(requireContext());
        tvApkSignHint.setText("  使用应用签名验证，防止补丁被篡改\n  无需管理密钥，启动速度快");
        tvApkSignHint.setTextSize(12);
        tvApkSignHint.setTextColor(0xFF666666);
        tvApkSignHint.setPadding(0, 0, 0, 15);
        layout.addView(tvApkSignHint);
        
        // ZIP 密码选项
        android.widget.CheckBox cbZipPassword = new android.widget.CheckBox(requireContext());
        cbZipPassword.setText("🔑 ZIP 密码保护");
        cbZipPassword.setChecked(false);
        layout.addView(cbZipPassword);
        
        TextView tvZipPasswordHint = new TextView(requireContext());
        tvZipPasswordHint.setText("  使用 AES-256 ZIP 密码加密，防篡改");
        tvZipPasswordHint.setTextSize(12);
        tvZipPasswordHint.setTextColor(0xFF666666);
        tvZipPasswordHint.setPadding(0, 0, 0, 15);
        layout.addView(tvZipPasswordHint);
        
        // ZIP 密码输入（仅在选择 ZIP 密码保护时显示）
        TextView tvZipPasswordLabel = new TextView(requireContext());
        tvZipPasswordLabel.setText("ZIP 密码：");
        tvZipPasswordLabel.setTextSize(14);
        tvZipPasswordLabel.setPadding(0, 10, 0, 8);
        tvZipPasswordLabel.setVisibility(View.GONE);
        layout.addView(tvZipPasswordLabel);
        
        android.widget.EditText etZipPassword = new android.widget.EditText(requireContext());
        etZipPassword.setHint("输入 ZIP 密码（留空使用默认密码）");
        etZipPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etZipPassword.setVisibility(View.GONE);
        layout.addView(etZipPassword);
        
        TextView tvZipPasswordNote = new TextView(requireContext());
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
        android.widget.CheckBox cbEncrypt = new android.widget.CheckBox(requireContext());
        cbEncrypt.setText("🔐 对补丁进行加密");
        cbEncrypt.setChecked(false);
        layout.addView(cbEncrypt);
        
        TextView tvEncryptHint = new TextView(requireContext());
        tvEncryptHint.setText("  使用 AES-256-GCM 加密，保护补丁内容");
        tvEncryptHint.setTextSize(12);
        tvEncryptHint.setTextColor(0xFF666666);
        tvEncryptHint.setPadding(0, 0, 0, 15);
        layout.addView(tvEncryptHint);
        
        // AES 加密密码输入（仅在选择加密时显示）
        TextView tvPasswordLabel = new TextView(requireContext());
        tvPasswordLabel.setText("加密密码：");
        tvPasswordLabel.setTextSize(14);
        tvPasswordLabel.setPadding(0, 10, 0, 8);
        tvPasswordLabel.setVisibility(View.GONE);
        layout.addView(tvPasswordLabel);
        
        android.widget.EditText etPassword = new android.widget.EditText(requireContext());
        etPassword.setHint("输入加密密码（留空使用默认密码）");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setVisibility(View.GONE);
        layout.addView(etPassword);
        
        TextView tvPasswordHint = new TextView(requireContext());
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
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext())
            .setTitle("🔒 补丁安全选项")
            .setView(layout)
            .setPositiveButton("生成", (d, w) -> {
                boolean withApkSignature = cbApkSign.isChecked();
                boolean withZipPassword = cbZipPassword.isChecked();
                boolean withEncryption = cbEncrypt.isChecked();
                String zipPassword = etZipPassword.getText().toString().trim();
                String aesPassword = etPassword.getText().toString().trim();
                
                // 创建输出文件
                File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outputFile = new File(outputDir, "patch_" + System.currentTimeMillis() + ".zip");
                
                // 生成补丁（带选项）
                viewModel.generatePatchWithOptions(requireContext(), outputFile, 
                    withApkSignature, withZipPassword, withEncryption, zipPassword, aesPassword);
            })
            .setNegativeButton("取消", null);
        
        builder.show();
    }
    
    private void updateFileInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 已选择的文件 ===\n\n");
        
        File baseApk = viewModel.getBaseApk();
        if (baseApk != null) {
            info.append("📦 基准 APK: ").append(FormatHelper.getApkInfo(requireContext(), baseApk));
            info.append(" (").append(FormatHelper.formatSize(baseApk.length())).append(")\n");
        } else {
            info.append("📦 基准 APK: 未选择\n");
        }
        
        File newApk = viewModel.getNewApk();
        if (newApk != null) {
            info.append("📦 新版 APK: ").append(FormatHelper.getApkInfo(requireContext(), newApk));
            info.append(" (").append(FormatHelper.formatSize(newApk.length())).append(")\n");
        } else {
            info.append("📦 新版 APK: 未选择\n");
        }
        
        tvInfo.setText(info.toString());
    }
    
    private void updateButtonStates() {
        btnGenerate.setEnabled(viewModel.canGenerate());
    }
    
    private void showSuccessResult(com.orange.patchgen.model.PatchResult result) {
        StringBuilder info = new StringBuilder();
        info.append("=== 补丁生成成功 ===\n\n");
        
        if (result.getPatchFile() != null) {
            info.append("📁 文件: ").append(result.getPatchFile().getName()).append("\n");
            info.append("📍 位置: ").append(result.getPatchFile().getParent()).append("\n\n");
        }
        
        info.append("📊 大小: ").append(FormatHelper.formatSize(result.getPatchSize())).append("\n");
        info.append("⏱ 耗时: ").append(result.getGenerateTime()).append(" ms\n");
        
        if (result.getDiffSummary() != null) {
            info.append("\n=== 差异统计 ===\n");
            info.append("修改类: ").append(result.getDiffSummary().getModifiedClasses()).append("\n");
            info.append("新增类: ").append(result.getDiffSummary().getAddedClasses()).append("\n");
            info.append("删除类: ").append(result.getDiffSummary().getDeletedClasses()).append("\n");
        }
        
        tvInfo.setText(info.toString());
        tvStatus.setText("✓ 补丁生成成功！");
        
        DialogHelper.showInfoDialog(requireContext(), "成功", "补丁生成成功！");
    }
}
