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
                    
                    StringBuilder result = new StringBuilder("✓ 应用列表 (" + apps.length() + ")\n\n");
                    
                    for (int i = 0; i < apps.length(); i++) {
                        JSONObject app = apps.getJSONObject(i);
                        result.append("📱 ").append(app.getString("name")).append("\n");
                        result.append("   包名: ").append(app.getString("package_name")).append("\n");
                        result.append("   版本: ").append(app.getString("current_version")).append("\n");
                        result.append("   ID: ").append(app.getInt("id")).append("\n\n");
                    }
                    
                    showResult(result.toString());
                    showToast("获取成功");
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
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                String response = readResponse(conn);

                if (responseCode == 200) {
                    JSONObject jsonResponse = new JSONObject(response);
                    JSONArray patches = jsonResponse.getJSONArray("patches");
                    
                    StringBuilder result = new StringBuilder("✓ 补丁列表 (" + patches.length() + ")\n\n");
                    
                    for (int i = 0; i < patches.length(); i++) {
                        JSONObject patch = patches.getJSONObject(i);
                        result.append("🔧 ").append(patch.getString("version")).append("\n");
                        result.append("   应用ID: ").append(patch.getInt("app_id")).append("\n");
                        result.append("   大小: ").append(formatSize(patch.getLong("size"))).append("\n");
                        result.append("   状态: ").append(patch.getString("status")).append("\n");
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
            return;
        }

        String serverUrl = etServerUrl.getText().toString().trim();
        String packageName = getPackageName();
        String currentVersion = "1.0.0";

        try {
            currentVersion = getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (Exception e) {
            Log.e(TAG, "获取版本号失败", e);
        }

        showLoading(true);
        showResult("正在检查更新...\n\n包名: " + packageName + "\n当前版本: " + currentVersion);

        String finalCurrentVersion = currentVersion;
        executor.execute(() -> {
            try {
                // 使用正确的 API 路径
                String urlStr = serverUrl + "/api/client/check-update?version=" + finalCurrentVersion;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                String response = readResponse(conn);

                if (responseCode == 200) {
                    JSONObject jsonResponse = new JSONObject(response);
                    boolean hasUpdate = jsonResponse.getBoolean("hasUpdate");
                    
                    StringBuilder result = new StringBuilder();
                    if (hasUpdate) {
                        result.append("✓ 发现新版本！\n\n");
                        JSONObject patch = jsonResponse.getJSONObject("patch");
                        result.append("新版本: ").append(patch.getString("version")).append("\n");
                        result.append("补丁大小: ").append(formatSize(patch.getLong("size"))).append("\n");
                        result.append("下载地址: ").append(patch.getString("downloadUrl")).append("\n");
                        if (patch.has("description") && !patch.isNull("description")) {
                            result.append("\n更新说明:\n").append(patch.getString("description")).append("\n");
                        }
                        result.append("\n强制更新: ").append(patch.getBoolean("forceUpdate") ? "是" : "否");
                    } else {
                        result.append("✓ 已是最新版本\n\n");
                        result.append("当前版本: ").append(finalCurrentVersion).append("\n");
                        result.append("无需更新");
                    }
                    
                    showResult(result.toString());
                    showToast(hasUpdate ? "发现新版本" : "已是最新版本");
                } else {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
