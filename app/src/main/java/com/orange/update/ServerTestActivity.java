package com.orange.update;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 服务端测试页面
 * 用于测试热更新服务端 API
 */
public class ServerTestActivity extends AppCompatActivity {

    private static final String TAG = "ServerTest";
    private static final String DEFAULT_SERVER_URL = "https://android-hotupdateserver.zeabur.app";

    private EditText etServerUrl;
    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnGetApps;
    private Button btnGetPatches;
    private Button btnCheckUpdate;
    private TextView tvResult;
    private ProgressBar progressBar;

    private String authToken = null;
    private String currentAppId = null; // 当前选择的应用ID
    private String currentAppName = null; // 当前选择的应用名称
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_test);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etServerUrl = findViewById(R.id.et_server_url);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnGetApps = findViewById(R.id.btn_get_apps);
        btnGetPatches = findViewById(R.id.btn_get_patches);
        btnCheckUpdate = findViewById(R.id.btn_check_update);
        tvResult = findViewById(R.id.tv_result);
        progressBar = findViewById(R.id.progress_bar);
        Button btnOpenRegister = findViewById(R.id.btn_open_register);

        // 设置默认服务器地址
        etServerUrl.setText(DEFAULT_SERVER_URL);
        
        // 不设置默认用户名和密码，提示用户注册
        etUsername.setHint("请先在网页端注册账号");
        etPassword.setHint("输入密码");

        // 初始状态禁用 API 按钮
        updateButtonStates(false);
        
        // 打开注册页面按钮
        btnOpenRegister.setOnClickListener(v -> openRegisterPage());
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> login());
        btnGetApps.setOnClickListener(v -> getApps());
        btnGetPatches.setOnClickListener(v -> getPatches());
        btnCheckUpdate.setOnClickListener(v -> checkUpdate());
    }

    private void updateButtonStates(boolean loggedIn) {
        mainHandler.post(() -> {
            btnGetApps.setEnabled(loggedIn);
            btnGetPatches.setEnabled(loggedIn);
            btnCheckUpdate.setEnabled(loggedIn);
        });
    }

    private void showLoading(boolean show) {
        mainHandler.post(() -> {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            btnLogin.setEnabled(!show);
            btnGetApps.setEnabled(!show && authToken != null);
            btnGetPatches.setEnabled(!show && authToken != null);
            btnCheckUpdate.setEnabled(!show && authToken != null);
        });
    }

    private void showResult(String result) {
        mainHandler.post(() -> tvResult.setText(result));
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * 打开注册页面
     */
    private void openRegisterPage() {
        String serverUrl = etServerUrl.getText().toString().trim();
        if (serverUrl.isEmpty()) {
            showToast("请先填写服务器地址");
            return;
        }
        
        // 打开浏览器访问注册页面
        String registerUrl = serverUrl + "/register";
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(registerUrl));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "打开注册页面失败", e);
            showToast("打开浏览器失败");
        }
    }

    /**
     * 登录
     */
    private void login() {
        String serverUrl = etServerUrl.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showToast("请填写完整信息");
            return;
        }

        showLoading(true);
        showResult("正在登录...");

        executor.execute(() -> {
            try {
                URL url = new URL(serverUrl + "/api/auth/login");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                // 构建请求体
                JSONObject requestBody = new JSONObject();
                requestBody.put("username", username);
                requestBody.put("password", password);

                // 发送请求
                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                String response = readResponse(conn);

                if (responseCode == 200) {
                    JSONObject jsonResponse = new JSONObject(response);
                    authToken = jsonResponse.getString("token");
                    
                    String result = "✓ 登录成功！\n\n";
                    result += "用户: " + jsonResponse.getJSONObject("user").getString("username") + "\n";
                    result += "Token: " + authToken.substring(0, Math.min(20, authToken.length())) + "...\n";
                    
                    showResult(result);
                    showToast("登录成功");
                    updateButtonStates(true);
                } else {
                    showResult("✗ 登录失败\n\n状态码: " + responseCode + "\n响应: " + response);
                    showToast("登录失败");
                    authToken = null;
                    updateButtonStates(false);
                }

            } catch (Exception e) {
                Log.e(TAG, "登录失败", e);
                showResult("✗ 登录失败\n\n错误: " + e.getMessage());
                showToast("登录失败: " + e.getMessage());
                authToken = null;
                updateButtonStates(false);
            } finally {
                showLoading(false);
            }
        });
    }

    /**
     * 获取应用列表
     */
    private void getApps() {
        if (authToken == null) {
            showToast("请先登录");
            return;
        }

        String serverUrl = etServerUrl.getText().toString().trim();
        showLoading(true);
        showResult("正在获取应用列表...");

        executor.execute(() -> {
            try {
                URL url = new URL(serverUrl + "/api/apps");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                String response = readResponse(conn);

                if (responseCode == 200) {
                    JSONArray apps = new JSONArray(response);
                    
                    if (apps.length() == 0) {
                        showResult("✓ 应用列表为空\n\n请先在服务端创建应用");
                        showToast("应用列表为空");
                        return;
                    }
                    
                    // 显示应用列表并让用户选择
                    showAppSelectionDialog(apps);
                } else {
                    showResult("✗ 获取失败\n\n状态码: " + responseCode + "\n响应: " + response);
                    showToast("获取失败");
                }

            } catch (Exception e) {
                Log.e(TAG, "获取应用列表失败", e);
                showResult("✗ 获取失败\n\n错误: " + e.getMessage());
                showToast("获取失败: " + e.getMessage());
            } finally {
                showLoading(false);
            }
        });
    }

    /**
     * 显示应用选择对话框
     */
    private void showAppSelectionDialog(JSONArray apps) {
        mainHandler.post(() -> {
            try {
                String[] appNames = new String[apps.length()];
                String[] appIds = new String[apps.length()];
                
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject app = apps.getJSONObject(i);
                    String appName = app.optString("app_name", "未知应用");
                    String packageName = app.optString("package_name", "");
                    String appId = app.optString("app_id", "");
                    
                    appNames[i] = appName + "\n(" + packageName + ")";
                    appIds[i] = appId;
                }
                
                new android.app.AlertDialog.Builder(this)
                    .setTitle("选择应用 (" + apps.length() + ")")
                    .setItems(appNames, (dialog, which) -> {
                        try {
                            JSONObject selectedApp = apps.getJSONObject(which);
                            currentAppId = selectedApp.optString("app_id", null);
                            currentAppName = selectedApp.optString("app_name", "未知应用");
                            
                            String packageName = selectedApp.optString("package_name", "");
                            int id = selectedApp.optInt("id", 0);
                            
                            showResult("✓ 已选择应用\n\n" +
                                      "应用名称: " + currentAppName + "\n" +
                                      "包名: " + packageName + "\n" +
                                      "应用ID: " + currentAppId + "\n" +
                                      "ID: " + id + "\n\n" +
                                      "现在可以检查更新了");
                            showToast("已选择: " + currentAppName);
                            
                            Log.i(TAG, "选择应用: " + currentAppName + " (appId=" + currentAppId + ")");
                        } catch (Exception e) {
                            Log.e(TAG, "选择应用失败", e);
                            showToast("选择失败");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
                    
            } catch (Exception e) {
                Log.e(TAG, "显示应用选择对话框失败", e);
                showToast("显示对话框失败");
            } finally {
                showLoading(false);
            }
        });
    }

    /**
     * 获取补丁列表
     */
    private void getPatches() {
        if (authToken == null) {
            showToast("请先登录");
            return;
        }

        String serverUrl = etServerUrl.getText().toString().trim();
        showLoading(true);
        showResult("正在获取补丁列表...");

        executor.execute(() -> {
            try {
                URL url = new URL(serverUrl + "/api/patches?page=1&limit=10");
                Log.d(TAG, "获取补丁列表 URL: " + url);
                
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "补丁列表响应码: " + responseCode);
                
                String response = readResponse(conn);
                Log.d(TAG, "补丁列表响应内容: " + response);

                if (responseCode == 200) {
                    JSONObject jsonResponse = new JSONObject(response);
                    
                    // 服务器返回格式可能是 {"patches": [...]} 或 {"data": {"patches": [...]}}
                    JSONArray patches;
                    if (jsonResponse.has("data")) {
                        JSONObject data = jsonResponse.getJSONObject("data");
                        patches = data.getJSONArray("patches");
                    } else {
                        patches = jsonResponse.getJSONArray("patches");
                    }
                    
                    StringBuilder result = new StringBuilder("✓ 补丁列表 (" + patches.length() + ")\n\n");
                    
                    for (int i = 0; i < patches.length(); i++) {
                        JSONObject patch = patches.getJSONObject(i);
                        
                        // 兼容不同的字段名
                        String version = patch.optString("version", patch.optString("patch_version", "未知"));
                        long size = patch.optLong("size", patch.optLong("file_size", 0));
                        String status = patch.optString("status", "未知");
                        
                        result.append("🔧 ").append(version).append("\n");
                        result.append("   补丁ID: ").append(patch.optInt("id", patch.optInt("patch_id", 0))).append("\n");
                        result.append("   应用ID: ").append(patch.optInt("app_id", 0)).append("\n");
                        result.append("   大小: ").append(formatSize(size)).append("\n");
                        result.append("   状态: ").append(status).append("\n");
                        if (patch.has("description") && !patch.isNull("description")) {
                            result.append("   说明: ").append(patch.getString("description")).append("\n");
                        }
                        result.append("\n");
                    }
                    
                    showResult(result.toString());
                    showToast("获取成功");
                } else {
                    showResult("✗ 获取失败\n\n状态码: " + responseCode + "\n响应: " + response);
                    showToast("获取失败");
                }

            } catch (Exception e) {
                Log.e(TAG, "获取补丁列表失败", e);
                showResult("✗ 获取失败\n\n错误: " + e.getMessage());
                showToast("获取失败: " + e.getMessage());
            } finally {
                showLoading(false);
            }
        });
    }

    /**
     * 检查更新
     */
    private void checkUpdate() {
        if (authToken == null) {
            showToast("请先登录");
            Log.w(TAG, "检查更新失败: 未登录");
            return;
        }
        
        // 从 HotUpdateHelper 获取 appId
        String appId = null;
        try {
            appId = HotUpdateHelper.getInstance().getAppId();
        } catch (Exception e) {
            Log.w(TAG, "获取 appId 失败: " + e.getMessage());
        }
        
        // 如果没有配置 appId，使用手动选择的 appId
        if (appId == null || appId.isEmpty()) {
            if (currentAppId == null) {
                showToast("请先选择应用或在 PatchApplication 中配置 APP_ID");
                Log.w(TAG, "检查更新失败: 未配置应用ID");
                return;
            }
            appId = currentAppId;
        }

        String serverUrl = etServerUrl.getText().toString().trim();
        String packageName = getPackageName();
        String currentVersion = "1.0.0";

        try {
            currentVersion = getPackageManager().getPackageInfo(packageName, 0).versionName;
            Log.d(TAG, "当前应用版本: " + currentVersion);
        } catch (Exception e) {
            Log.e(TAG, "获取版本号失败", e);
        }

        String appName = currentAppName != null ? currentAppName : "当前应用";
        showLoading(true);
        showResult("正在检查更新...\n\n应用: " + appName + "\n应用ID: " + appId + "\n包名: " + packageName + "\n当前版本: " + currentVersion);

        String finalCurrentVersion = currentVersion;
        String finalAppId = appId;
        executor.execute(() -> {
            try {
                // 使用配置的或选择的 appId
                String urlStr = serverUrl + "/api/client/check-update?version=" + finalCurrentVersion + 
                               "&appId=" + finalAppId;
                Log.d(TAG, "检查更新 URL: " + urlStr);
                
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                Log.d(TAG, "发送检查更新请求...");

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "服务器响应码: " + responseCode);
                String response = readResponse(conn);
                Log.d(TAG, "服务器响应内容: " + response);
                
                // 解析响应，查看详细信息
                try {
                    JSONObject debugJson = new JSONObject(response);
                    Log.d(TAG, "=== 调试信息 ===");
                    Log.d(TAG, "code: " + debugJson.optInt("code"));
                    Log.d(TAG, "message: " + debugJson.optString("message"));
                    if (debugJson.has("data")) {
                        JSONObject data = debugJson.getJSONObject("data");
                        Log.d(TAG, "hasUpdate: " + data.optBoolean("hasUpdate"));
                        if (data.has("patchInfo")) {
                            JSONObject patchInfo = data.getJSONObject("patchInfo");
                            Log.d(TAG, "patchVersion: " + patchInfo.optString("patchVersion"));
                            Log.d(TAG, "baseVersion: " + patchInfo.optString("baseVersion", "未知"));
                        }
                    }
                    Log.d(TAG, "=== 调试信息结束 ===");
                } catch (Exception e) {
                    Log.w(TAG, "解析调试信息失败: " + e.getMessage());
                }

                if (responseCode == 200) {
                    JSONObject jsonResponse = new JSONObject(response);
                    Log.d(TAG, "解析响应成功");
                    
                    // 服务器返回格式: {"code":0, "message":"...", "data":{...}}
                    // 需要先提取 data 字段
                    JSONObject data = jsonResponse.optJSONObject("data");
                    if (data == null) {
                        Log.e(TAG, "响应中没有 data 字段");
                        showResult("✗ 响应格式错误\n\n缺少 data 字段");
                        showToast("响应格式错误");
                        return;
                    }
                    
                    // 检查是否需要强制大版本更新
                    if (data.has("forceUpdate") && data.getBoolean("forceUpdate")) {
                        Log.i(TAG, "检测到需要强制更新");
                        StringBuilder result = new StringBuilder();
                        result.append("⚠️ 需要强制更新！\n\n");
                        result.append("当前版本: ").append(finalCurrentVersion).append("\n");
                        result.append("最新版本: ").append(data.getString("latestVersion")).append("\n");
                        result.append("下载地址: ").append(data.getString("downloadUrl")).append("\n\n");
                        result.append("更新说明:\n").append(jsonResponse.getString("message")).append("\n\n");
                        result.append("⚠️ 您的版本过低，必须更新到最新版本才能继续使用。\n");
                        result.append("热更新补丁功能不可用，请下载完整 APK 更新。");
                        
                        showResult(result.toString());
                        showToast("需要强制更新到最新版本");
                        return;
                    }
                    
                    // 检查热更新补丁
                    boolean hasUpdate = data.getBoolean("hasUpdate");
                    Log.i(TAG, "是否有更新: " + hasUpdate);
                    
                    StringBuilder result = new StringBuilder();
                    if (hasUpdate) {
                        result.append("✓ 发现热更新补丁！\n\n");
                        JSONObject patch = data.getJSONObject("patchInfo");
                        Log.i(TAG, "补丁信息: 版本=" + patch.getString("patchVersion") + 
                                   ", 大小=" + patch.getLong("fileSize") + " bytes");
                        result.append("新版本: ").append(patch.getString("patchVersion")).append("\n");
                        result.append("补丁大小: ").append(formatSize(patch.getLong("fileSize"))).append("\n");
                        result.append("下载地址: ").append(patch.getString("downloadUrl")).append("\n");
                        if (patch.has("description") && !patch.isNull("description")) {
                            result.append("\n更新说明:\n").append(patch.getString("description")).append("\n");
                        }
                        result.append("\nMD5: ").append(patch.optString("md5", "无"));
                        
                        // 显示安全配置
                        if (data.has("securityConfig") && !data.isNull("securityConfig")) {
                            JSONObject securityConfig = data.getJSONObject("securityConfig");
                            result.append("\n\n安全配置:");
                            result.append("\n- 要求签名: ").append(securityConfig.getBoolean("requireSignature") ? "是" : "否");
                            result.append("\n- 要求加密: ").append(securityConfig.getBoolean("requireEncryption") ? "是" : "否");
                        }
                        
                        showResult(result.toString());
                        showToast("发现新版本");
                        
                        // 弹出下载安装对话框
                        showDownloadDialog(patch);
                    } else {
                        result.append("✓ 已是最新版本\n\n");
                        result.append("当前版本: ").append(finalCurrentVersion).append("\n");
                        result.append("无需更新");
                        Log.i(TAG, "当前已是最新版本");
                    }
                    
                    if (!hasUpdate) {
                        showResult(result.toString());
                        showToast("已是最新版本");
                    }
                } else {
                    Log.e(TAG, "检查更新失败 - 状态码: " + responseCode + ", 响应: " + response);
                    showResult("✗ 检查失败\n\n状态码: " + responseCode + "\n响应: " + response);
                    showToast("检查失败");
                }

            } catch (Exception e) {
                Log.e(TAG, "检查更新失败", e);
                showResult("✗ 检查失败\n\n错误: " + e.getMessage());
                showToast("检查失败: " + e.getMessage());
            } finally {
                showLoading(false);
            }
        });
    }

    /**
     * 读取响应
     */
    private String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader;
        if (conn.getResponseCode() >= 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        }
        
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 显示下载安装对话框
     */
    private void showDownloadDialog(JSONObject patchInfo) {
        mainHandler.post(() -> {
            try {
                String version = patchInfo.getString("patchVersion");
                long size = patchInfo.getLong("fileSize");
                String description = patchInfo.optString("description", "无");
                
                new android.app.AlertDialog.Builder(this)
                    .setTitle("发现新版本")
                    .setMessage("版本: " + version + "\n" +
                               "大小: " + formatSize(size) + "\n" +
                               "更新说明: " + description + "\n\n" +
                               "是否立即下载并安装？")
                    .setPositiveButton("立即更新", (dialog, which) -> {
                        downloadAndInstallPatch(patchInfo);
                    })
                    .setNegativeButton("稍后", null)
                    .show();
            } catch (Exception e) {
                Log.e(TAG, "显示下载对话框失败", e);
            }
        });
    }

    /**
     * 下载并安装补丁
     */
    private void downloadAndInstallPatch(JSONObject patchInfo) {
        executor.execute(() -> {
            try {
                String downloadUrl = patchInfo.getString("downloadUrl");
                String patchVersion = patchInfo.getString("patchVersion");
                
                mainHandler.post(() -> {
                    showLoading(true);
                    showResult("正在下载补丁...\n\n版本: " + patchVersion);
                });
                
                Log.d(TAG, "开始下载补丁: " + downloadUrl);
                
                // 下载补丁文件
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    throw new Exception("下载失败，状态码: " + responseCode);
                }
                
                long totalSize = conn.getContentLength();
                Log.d(TAG, "补丁文件大小: " + totalSize + " bytes");
                
                // 保存到临时文件
                File patchFile = new File(getCacheDir(), "patch_" + patchVersion + ".zip");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(patchFile);
                java.io.InputStream is = conn.getInputStream();
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long downloadedSize = 0;
                
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    downloadedSize += bytesRead;
                    
                    // 更新下载进度
                    long finalDownloadedSize = downloadedSize;
                    long finalTotalSize = totalSize;
                    mainHandler.post(() -> {
                        int progress = (int) ((finalDownloadedSize * 100) / finalTotalSize);
                        showResult("正在下载补丁...\n\n" +
                                  "版本: " + patchVersion + "\n" +
                                  "进度: " + progress + "% (" + 
                                  formatSize(finalDownloadedSize) + " / " + 
                                  formatSize(finalTotalSize) + ")");
                    });
                }
                
                fos.close();
                is.close();
                conn.disconnect();
                
                Log.d(TAG, "补丁下载完成: " + patchFile.getAbsolutePath());
                
                // 安装补丁
                mainHandler.post(() -> {
                    showResult("下载完成，正在安装补丁...");
                });
                
                HotUpdateHelper helper = HotUpdateHelper.getInstance(this);
                helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
                    @Override
                    public void onProgress(int percent, String message) {
                        mainHandler.post(() -> {
                            showResult("正在安装补丁...\n\n" +
                                      "进度: " + percent + "%\n" +
                                      message);
                        });
                    }
                    
                    @Override
                    public void onSuccess(HotUpdateHelper.PatchResult result) {
                        mainHandler.post(() -> {
                            showLoading(false);
                            showResult("✅ 热更新成功！\n\n" +
                                      "补丁版本: " + result.patchVersion + "\n" +
                                      "补丁ID: " + result.patchId + "\n\n" +
                                      "DEX 和 SO 已立即生效\n" +
                                      "资源更新需要重启应用");
                            showToast("热更新成功！");
                            
                            // 清理临时文件
                            if (patchFile.exists()) {
                                patchFile.delete();
                            }
                            
                            // 询问是否重启应用
                            new android.app.AlertDialog.Builder(ServerTestActivity.this)
                                .setTitle("更新成功")
                                .setMessage("补丁已安装成功！\n\n是否立即重启应用以应用资源更新？")
                                .setPositiveButton("立即重启", (dialog, which) -> {
                                    restartApp();
                                })
                                .setNegativeButton("稍后", null)
                                .show();
                        });
                    }
                    
                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> {
                            showLoading(false);
                            showResult("✗ 安装失败\n\n" + message);
                            showToast("安装失败: " + message);
                            
                            // 清理临时文件
                            if (patchFile.exists()) {
                                patchFile.delete();
                            }
                        });
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "下载安装补丁失败", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    showResult("✗ 下载失败\n\n" + e.getMessage());
                    showToast("下载失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 重启应用
     */
    private void restartApp() {
        android.content.Intent intent = getPackageManager()
            .getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                           android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
