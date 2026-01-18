# 详细使用说明

本文档提供完整的使用说明，包括补丁生成、应用、原理等详细内容。

## 目录

- [补丁生成流程](#补丁生成流程)
- [补丁应用流程](#补丁应用流程)
- [热更新原理](#热更新原理)
- [Application 集成](#application-集成)
- [Demo 应用使用](#demo-应用使用)
- [命令行工具](#命令行工具)
- [Gradle 插件](#gradle-插件)

## 补丁生成流程

### 1. 准备 APK 文件

需要准备两个 APK 文件：
- **基准 APK (旧版本)** - 当前线上运行的版本
- **新 APK (新版本)** - 包含修复或新功能的版本

**注意事项：**
- 两个 APK 必须是同一个应用（包名相同）
- 建议使用 Release 版本的 APK
- 确保 APK 文件完整且未损坏

### 2. 生成补丁

#### 使用 Android SDK

```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .callbackOnMainThread(true)  // 回调在主线程
    .callback(new SimpleAndroidGeneratorCallback() {
        @Override
        public void onStart() {
            Log.d(TAG, "开始生成补丁");
        }

        @Override
        public void onProgress(int percent, String stage) {
            Log.d(TAG, stage + ": " + percent + "%");
        }

        @Override
        public void onComplete(PatchResult result) {
            if (result.isSuccess()) {
                Log.i(TAG, "补丁生成成功");
                Log.i(TAG, "补丁大小: " + result.getPatchSize());
                Log.i(TAG, "耗时: " + result.getGenerateTime() + "ms");
            } else {
                Log.e(TAG, "生成失败: " + result.getErrorMessage());
            }
        }

        @Override
        public void onError(int errorCode, String message) {
            Log.e(TAG, "错误: " + message);
        }
    })
    .build();

// 后台生成
generator.generateInBackground();
```

#### 使用命令行工具

```bash
java -jar patch-cli.jar \
  --base /path/to/app-v1.0.apk \
  --new /path/to/app-v1.1.apk \
  --output /path/to/patch.zip
```

### 3. 补丁内容

补丁包会自动包含以下变更：
- ✅ **DEX 文件** - 修改、新增、删除的类
- ✅ **资源文件** - 修改的布局、图片、字符串等
- ✅ **SO 库** - 修改的 Native 库
- ✅ **Assets 文件** - 修改的 Assets 资源
- ✅ **元数据** - 版本信息、变更统计

### 4. 补丁加密（可选）

为了保护补丁内容，可以对生成的补丁进行加密：

```java
// 生成补丁后加密
SecurityManager securityManager = new SecurityManager(context);
File patchFile = new File("/path/to/patch.zip");

// 使用 AES-256-GCM 加密
File encryptedPatch = securityManager.encryptPatch(patchFile);
// 生成: patch.zip.enc

Log.i(TAG, "补丁已加密: " + encryptedPatch.getPath());
```

**加密特性：**
- 算法：AES-256-GCM（认证加密）
- 密钥管理：Android KeyStore（设备绑定）
- 最低版本：Android 6.0+ (API 23+)
- 文件扩展名：`.enc`
- 解密方式：使用 `SecurityManager.decryptPatch()` 手动解密

### 5. 补丁签名（可选）

为了防止补丁被篡改，可以对补丁进行签名：

```java
// 服务器端：使用私钥签名
// openssl dgst -sha256 -sign private_key.pem -out patch.sig patch.zip
// base64 patch.sig > patch.sig.base64

// 客户端：验证签名
SecurityManager securityManager = new SecurityManager(context);
securityManager.setSignaturePublicKey(publicKeyBase64);

File patchFile = new File("/path/to/patch.zip");
String signature = "从服务器获取的 Base64 签名";

if (securityManager.verifySignature(patchFile, signature)) {
    Log.i(TAG, "签名验证通过");
} else {
    Log.e(TAG, "签名验证失败");
}
```

**签名特性：**
- 算法：SHA256withRSA
- 密钥长度：RSA-2048
- 公钥：打包在 APK 中
- 私钥：只在服务器端使用

### 6. 组合使用签名和加密（推荐）

在生产环境中，建议同时使用签名和加密：

```java
// 服务器端流程
SecurityManager securityManager = new SecurityManager(context);

// 1. 生成补丁
File patchFile = generatePatch(baseApk, newApk);

// 2. 加密补丁（可选使用密码）
String password = "your_secure_password"; // 或留空使用 KeyStore
File encryptedPatch = password.isEmpty() 
    ? securityManager.encryptPatch(patchFile)
    : securityManager.encryptPatchWithPassword(patchFile, password);

// 3. 对加密文件签名
String signature = signFile(encryptedPatch, privateKey);
saveSignature(signature, encryptedPatch.getPath() + ".sig");

// 客户端流程
// 1. 下载加密补丁和签名
File encryptedPatch = downloadPatch();
String signature = downloadSignature();

// 2. 验证签名
if (!securityManager.verifySignature(encryptedPatch, signature)) {
    Log.e(TAG, "签名验证失败");
    return;
}

// 3. 解密并应用补丁
String password = getPasswordFromConfig(); // 从配置获取密码
File decryptedPatch = password.isEmpty()
    ? securityManager.decryptPatch(encryptedPatch)
    : securityManager.decryptPatchWithPassword(encryptedPatch, password);

HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatch(decryptedPatch, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, message + ": " + percent + "%");
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        Log.i(TAG, "补丁应用成功！");
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "应用失败: " + message);
    }
});
```

### 7. 使用密码加密补丁

支持使用自定义密码加密补丁：

```java
// 生成时使用密码加密
SecurityManager securityManager = new SecurityManager(context);
File patchFile = new File("/path/to/patch.zip");
String password = "your_secure_password";

// 使用密码加密
File encryptedPatch = securityManager.encryptPatchWithPassword(patchFile, password);
Log.i(TAG, "补丁已加密: " + encryptedPatch.getPath());

// 客户端应用时需要提供相同的密码
SecurityManager clientSecurityManager = new SecurityManager(context);
String password = getPasswordFromConfig(); // 从配置或安全存储获取

try {
    // 使用密码解密
    File decryptedPatch = clientSecurityManager.decryptPatchWithPassword(encryptedPatch, password);
    
    // 应用解密后的补丁
    HotUpdateHelper helper = new HotUpdateHelper(context);
    helper.applyPatch(decryptedPatch, new HotUpdateHelper.Callback() {
        @Override
        public void onProgress(int percent, String message) {
            Log.d(TAG, message + ": " + percent + "%");
        }
        
        @Override
        public void onSuccess(HotUpdateHelper.PatchResult result) {
            Log.i(TAG, "补丁解密并应用成功！");
        }
        
        @Override
        public void onError(String message) {
            Log.e(TAG, "应用失败: " + message);
        }
    });
} catch (SecurityException e) {
    Log.e(TAG, "解密失败: " + e.getMessage());
    // 可能的错误：密码错误、文件损坏等
}
```

**注意：**
- Demo 应用会弹出密码输入对话框，这只是为了演示方便
- 在实际应用中，密码应该从配置文件、服务器或安全存储中获取
- 密码作为参数传入 `decryptPatchWithPassword()` 方法

**密码加密特性：**
- 算法：PBKDF2WithHmacSHA256 + AES-256-GCM
- 迭代次数：10000 次
- 密钥派生：从密码派生 256 位密钥
- 客户端需要相同密码才能解密
- 支持密码提示文件（.pwd）

**安全级别对比：**

| 方案 | 防篡改 | 防窃取 | 密码保护 | 推荐场景 |
|------|--------|--------|----------|----------|
| 无保护 | ❌ | ❌ | ❌ | 开发测试 |
| 仅签名 | ✅ | ❌ | ❌ | 一般应用 |
| 仅加密（KeyStore） | ❌ | ✅ | ❌ | 内容保护 |
| 仅加密（密码） | ❌ | ✅ | ✅ | 需要密码保护 |
| 签名+加密（KeyStore） | ✅ | ✅ | ❌ | 生产环境 |
| 签名+加密（密码） | ✅ | ✅ | ✅ | 最高安全级别 |

### 8. 防篡改保护（v1.3.2 新增）

为了防止补丁在解密后被恶意篡改，系统提供了**补丁完整性验证**和**自动恢复**功能。

#### 功能特性

- ✅ **SHA-256 哈希验证**：应用补丁时计算并保存哈希值
- ✅ **启动时验证**：每次应用启动时验证补丁完整性
- ✅ **自动检测篡改**：检测到文件被修改时自动识别
- ✅ **自动恢复**：从加密存储中自动恢复被篡改的补丁
- ✅ **篡改计数**：最多允许 3 次篡改尝试
- ✅ **安全清除**：超过限制后自动清除补丁数据
- ✅ **用户提示**：恢复成功后显示 Toast 提示重启

#### 工作原理

```
应用启动 (attachBaseContext)
    ↓
检测补丁完整性（SHA-256）
    ↓
┌─────────────┬─────────────┐
│  验证通过   │  验证失败   │
│             │             │
│  加载补丁   │  检测篡改   │
│             │             │
│  正常运行   │  删除文件   │
└─────────────┴─────────────┘
                    ↓
            标记需要恢复
                    ↓
        Application.onCreate()
                    ↓
        从加密存储恢复补丁
                    ↓
            验证恢复结果
                    ↓
        ┌───────────────┐
        │  恢复成功？   │
        └───────────────┘
            ↓       ↓
          成功     失败
            ↓       ↓
        提示重启  增加计数
            ↓       ↓
        下次加载  超过3次
        恢复补丁  清除数据
```

#### 使用方式

**无需额外配置**，防篡改功能已经集成到`HotUpdateHelper` 中：

// 方式：使用 HotUpdateHelper（自动启用）
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.loadAppliedPatch(); // 自动验证完整性
```

#### 日志示例

**正常加载（验证通过）**：
```
D PatchApplication: Loading applied patch: patch_1768678370576
D PatchApplication: ✅ Patch integrity verified: 4f2db21b81332290...
D PatchApplication: Patch contains resources, merging with original APK
I PatchApplication: Resources merged successfully, size: 1440680
D PatchApplication: Dex patch loaded successfully
D PatchApplication: Resource patch loaded successfully
I PatchApplication: ✅ Patch loading completed with integrity verification
```

**检测到篡改（自动恢复）**：
```
D PatchApplication: Loading applied patch: patch_1768678370576
E PatchApplication: ⚠️ PATCH INTEGRITY CHECK FAILED!
E PatchApplication: Expected: 4f2db21b813322904e7136432a804f6540ccb5cbb90470ea2c0ccd3bc6e47663
E PatchApplication: Actual:   2fc7f3d53a193a527d3e521e0517bf22f4669f9afcd88d6924efbd95647ccace
E PatchApplication: ⚠️ Patch integrity verification failed
E PatchApplication: ⚠️ Patch tampered! Attempt: 1/3
D PatchApplication: Deleted tampered patch file
W PatchApplication: ⚠️ Patch cleared. Will attempt recovery in onCreate()

I PatchApplication: 🔄 Attempting to recover patch from encrypted storage: patch_1768678370576
D PatchStorage: Prepared patch to applied directory: patch_1768678370576
D PatchStorage: Saved patch hash: 4f2db21b81332290...
I PatchApplication: ✅ Patch recovered successfully from encrypted storage
I PatchApplication: ✅ Hash verified: 4f2db21b81332290...
I PatchApplication: ⚠️ Please restart the app to load the recovered patch

[Toast 提示] 补丁已恢复，请重启应用
```

**超过篡改限制（清除数据）**：
```
E PatchApplication: ⚠️ Patch tampered! Attempt: 3/3
E PatchApplication: ⚠️ Too many tamper attempts (3), clearing all patch metadata
E PatchApplication: ⚠️ All patch data cleared. User needs to re-apply patch.
```

#### 安全保障层级

现在热更新系统有 **5 层安全防护**：

1. **下载时**：签名验证（防止网络传输被篡改）
2. **存储时**：AES-256 加密（防止存储被窃取）
3. **应用时**：SHA-256 哈希验证（防止解密后被篡改）✅ 新增
4. **启动时**：完整性验证（防止运行时被篡改）✅ 新增
5. **恢复时**：自动从加密存储恢复（自动修复）✅ 新增

#### 测试防篡改功能

可以手动篡改补丁文件来测试防篡改功能：

```bash
# 1. 应用补丁后，篡改补丁文件
adb shell "echo 'tampered' >> /data/data/your.package/files/update/applied/current_patch.zip"

# 2. 重启应用
adb shell am force-stop your.package
adb shell am start -n your.package/.MainActivity

# 3. 查看日志
adb logcat -s PatchApplication:* PatchStorage:*

# 预期结果：
# - 检测到篡改
# - 自动从加密存储恢复
# - 显示 Toast 提示重启
# - 再次重启后补丁正常加载
```

#### 相关文档

- [SECURITY_IMPROVEMENT.md](../SECURITY_IMPROVEMENT.md) - 详细的安全改进方案
- [SECURITY_TEST_GUIDE.md](../SECURITY_TEST_GUIDE.md) - 完整的测试指南
- [AUTO_RECOVERY_TEST.md](../AUTO_RECOVERY_TEST.md) - 自动恢复测试指南
- [INTEGRITY_TEST_RESULT.md](../INTEGRITY_TEST_RESULT.md) - 测试结果报告

#### 性能影响

- **检测篡改**: ~10ms（SHA-256 计算）
- **自动恢复**: ~100-200ms（解密 + 验证）
- **用户感知**: 几乎无感知（在后台执行）

#### 注意事项

- ✅ 防篡改功能在 v1.3.2+ 版本中自动启用
- ✅ 无需额外配置或代码修改
- ✅ 兼容旧版本补丁（向后兼容）
- ✅ 不影响正常的补丁应用流程
- ✅ 恢复成功后需要重启应用才能加载恢复的补丁

## 补丁应用流程

### 1. 应用补丁

```java
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, message + ": " + percent + "%");
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        Log.i(TAG, "热更新成功！");
        Log.i(TAG, "补丁版本: " + result.patchVersion);
        Log.i(TAG, "补丁大小: " + result.patchSize + " 字节");
        
        if (result.needsRestart) {
            // 提示用户重启应用（仅资源更新需要）
            showRestartDialog();
        }
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "热更新失败: " + message);
    }
});
```

### 2. 应用加密补丁

#### 方式一：使用 SecurityManager 手动解密（推荐）

```java
SecurityManager securityManager = new SecurityManager(context);
File encryptedPatch = new File("/path/to/patch.zip.enc");

try {
    File decryptedPatch;
    
    // 根据加密方式选择解密方法
    if (hasPassword) {
        // 使用密码解密
        String password = "your_secure_password"; // 从配置或用户输入获取
        decryptedPatch = securityManager.decryptPatchWithPassword(encryptedPatch, password);
    } else {
        // 使用 KeyStore 解密
        decryptedPatch = securityManager.decryptPatch(encryptedPatch);
    }
    
    Log.i(TAG, "解密成功: " + decryptedPatch.getPath());
    
    // 应用解密后的补丁
    HotUpdateHelper helper = new HotUpdateHelper(context);
    helper.applyPatch(decryptedPatch, callback);
    
} catch (SecurityException e) {
    Log.e(TAG, "解密失败: " + e.getMessage());
    // 可能的错误：
    // - "解密需要 Android 6.0+"
    // - "Tag mismatch" (密码错误)
    // - "文件损坏"
}
```

#### 方式二：自动检测并解密（Demo 应用方式）

Demo 应用会自动检测 `.enc` 文件并弹出密码输入对话框，这只是为了演示方便：

```java
HotUpdateHelper helper = new HotUpdateHelper(context);

// Demo 应用会自动检测 .enc 扩展名
File encryptedPatch = new File("/path/to/patch.zip.enc");
helper.applyPatch(encryptedPatch, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, message + ": " + percent + "%");
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        Log.i(TAG, "补丁应用成功！");
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "应用失败: " + message);
    }
});
// 注意：Demo 应用会弹出密码输入对话框，这只是 UI 演示
// 在实际应用中，应该使用方式一，通过参数传入密码
```

**推荐做法：**
- 在实际应用中，使用 `SecurityManager` 手动解密，密码作为参数传入
- 密码可以从配置文件、服务器、或安全存储中获取
- Demo 应用的弹窗只是为了演示方便，不应该在生产环境中使用

**手动解密（可选）：**

```java
SecurityManager securityManager = new SecurityManager(context);

try {
    File encryptedPatch = new File("/path/to/patch.zip.enc");
    File decryptedPatch = securityManager.decryptPatch(encryptedPatch);
    
    Log.i(TAG, "解密成功: " + decryptedPatch.getPath());
    
    // 然后应用解密后的补丁
    helper.applyPatch(decryptedPatch, callback);
    
} catch (SecurityException e) {
    Log.e(TAG, "解密失败: " + e.getMessage());
}
```

### 3. 验证签名后应用补丁

```java
SecurityManager securityManager = new SecurityManager(context);
securityManager.setSignaturePublicKey(publicKeyBase64);

File patchFile = new File("/path/to/patch.zip");
String signature = "从服务器获取的签名";

// 验证签名
if (securityManager.verifySignature(patchFile, signature)) {
    Log.i(TAG, "签名验证通过");
    
    // 应用补丁
    HotUpdateHelper helper = new HotUpdateHelper(context);
    helper.applyPatch(patchFile, callback);
} else {
    Log.e(TAG, "签名验证失败，拒绝应用补丁");
}
```

### 4. 组合使用签名和加密

```java
SecurityManager securityManager = new SecurityManager(context);
securityManager.setSignaturePublicKey(publicKeyBase64);

File encryptedPatch = new File("/path/to/patch.zip.enc");
String signature = "从服务器获取的签名";

// 1. 先验证签名（验证加密文件的签名）
if (!securityManager.verifySignature(encryptedPatch, signature)) {
    Log.e(TAG, "签名验证失败");
    return;
}

Log.i(TAG, "签名验证通过，开始解密并应用补丁");

// 2. 解密补丁
String password = getPasswordFromConfig(); // 从配置获取密码
File decryptedPatch = securityManager.decryptPatchWithPassword(encryptedPatch, password);

// 3. 应用补丁
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatch(decryptedPatch, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, message + ": " + percent + "%");
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        Log.i(TAG, "补丁验证、解密并应用成功！");
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "应用失败: " + message);
    }
});
```

### 5. 回滚补丁

```java
// 简单回滚
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.clearPatch();

// 清除并重启
helper.clearPatch();
Intent intent = context.getPackageManager()
    .getLaunchIntentForPackage(context.getPackageName());
if (intent != null) {
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    android.os.Process.killProcess(android.os.Process.myPid());
}
```

### 6. 配置安全策略（可选）

可以配置安全策略，强制要求补丁签名或加密：

```java
// 使用 HotUpdateHelper 的安全策略 API（推荐）
HotUpdateHelper helper = new HotUpdateHelper(context);

// 配置安全策略
helper.setRequireSignature(true);  // 强制要求签名
helper.setRequireEncryption(true); // 强制要求加密

// 应用补丁时会自动检查安全策略
helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, message + ": " + percent + "%");
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        Log.i(TAG, "补丁应用成功");
    }
    
    @Override
    public void onError(String message) {
        // 如果补丁不符合安全策略，会返回错误
        // 例如："当前安全策略要求补丁必须签名！此补丁未签名，拒绝应用。"
        Log.e(TAG, "应用失败: " + message);
    }
});

// 查询当前安全策略
boolean requireSignature = helper.isRequireSignature();
boolean requireEncryption = helper.isRequireEncryption();
Log.d(TAG, "要求签名: " + requireSignature + ", 要求加密: " + requireEncryption);
```

**安全策略说明：**
- `setRequireSignature(true)`: 开启后只能应用已签名的补丁
  - 支持 zip 内部签名（`signature.sig`）
  - 支持外部签名文件（`.sig`）
- `setRequireEncryption(true)`: 开启后只能应用已加密的补丁（文件名以 `.enc` 结尾）
- 如果补丁不符合策略要求，会拒绝应用并显示详细错误信息
- 适合在生产环境中强制执行安全规范
- Demo 应用提供了可视化的安全策略配置界面

## 热更新原理

### DEX 热更新

**原理：**
1. 通过反射获取 `ClassLoader` 的 `pathList` 对象
2. 获取 `pathList` 中的 `dexElements` 数组
3. 使用 `DexClassLoader` 加载补丁 DEX
4. 将补丁 DEX 的 `dexElements` 插入到数组最前面
5. 类加载时优先从补丁 DEX 查找

**特点：**
- ✅ 立即生效，无需重启
- ✅ 支持修改、新增、删除类
- ⚠️ 某些类可能被 ART 提前编译

### 资源热更新

**原理：**
1. 创建新的 `AssetManager` 并加载补丁资源
2. 替换所有 `Resources` 对象的 `AssetManager`
3. 清空 `ResourcesManager` 缓存
4. 修改 `LoadedApk` 的 `mResDir`

**特点：**
- ⚠️ 需要重启 Activity 才能看到新界面
- ✅ 支持修改布局、图片、字符串等
- ✅ 兼容 MIUI 等定制 ROM

### SO 库热更新

**原理：**
1. 提取补丁中的 SO 文件到应用目录
2. 通过反射获取 `ClassLoader` 的 `pathList`
3. 修改 `nativeLibraryPathElements`（API 23+）或 `nativeLibraryDirectories`（API 21-22）
4. 将补丁 SO 路径插入到最前面

**特点：**
- ✅ 立即生效，无需重启
- ✅ 支持多 ABI（armeabi-v7a, arm64-v8a, x86, x86_64）

### Assets 热更新

**原理：**
- Assets 文件作为资源的一部分
- 通过 `AssetManager` 加载
- 随资源热更新一起生效

**特点：**
- ⚠️ 需要重启应用
- ✅ 支持修改配置文件、数据文件等

## Application 集成

为了让补丁在应用启动时自动加载，需要在 `Application` 中集成热更新功能。这是**必须**的步骤，否则补丁不会生效。

### 方式一：使用 HotUpdateHelper（推荐 - 最简单）

**1. 创建自定义 Application 类**

```java
package com.example.myapp;

import android.app.Application;
import android.content.Context;
import com.orange.update.HotUpdateHelper;

public class MyApplication extends Application {
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 加载已应用的补丁
        HotUpdateHelper helper = new HotUpdateHelper(this);
        helper.loadAppliedPatch();
    }
}
```

**2. 在 AndroidManifest.xml 中注册**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myapp">
    
    <application
        android:name=".MyApplication"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        ...>
        
        <!-- 其他配置 -->
        
    </application>
</manifest>
```

### 方式二：直接继承 PatchApplication（Demo 应用方式）

如果你想使用 Demo 应用中的完整实现，可以直接继承 `PatchApplication`：

**1. 将 PatchApplication 复制到你的项目**

从 Demo 应用复制 `app/src/main/java/com/orange/update/PatchApplication.java` 到你的项目中。

**2. 继承 PatchApplication**

```java
package com.example.myapp;

import com.orange.update.PatchApplication;

public class MyApplication extends PatchApplication {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 你的其他初始化代码
        initSDK();
    }
    
    private void initSDK() {
        // 初始化第三方 SDK
    }
}
```

**3. 在 AndroidManifest.xml 中注册**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myapp">
    
    <application
        android:name=".MyApplication"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        ...>
        
        <!-- 其他配置 -->
        
    </application>
</manifest>
```

**PatchApplication 的优势：**
- ✅ 已经实现了完整的补丁加载逻辑
- ✅ 包含资源合并（Tinker 方式）
- ✅ 自动检测补丁类型（DEX/资源）
- ✅ 完善的错误处理和日志
- ✅ 在 attachBaseContext 中正确加载补丁

### 为什么必须在 attachBaseContext 中？

`attachBaseContext()` 是 Application 生命周期中**最早**的回调方法，在这里加载补丁可以确保：

1. **DEX 补丁在类加载前注入** - 所有类加载时都能使用补丁中的代码
2. **资源补丁在 Activity 创建前加载** - 所有 Activity 都能使用补丁中的资源
3. **避免类加载冲突** - 如果在 `onCreate()` 中加载，某些类可能已经被加载

**错误示例（不要这样做）：**
```java
@Override
public void onCreate() {
    super.onCreate();
    
    // ❌ 错误：太晚了，某些类已经被加载
    HotUpdateHelper helper = new HotUpdateHelper(this);
    helper.loadAppliedPatch();
}
```

### 高级配置

#### 1. 添加日志和错误处理

```java
public class MyApplication extends Application {
    
    private static final String TAG = "MyApplication";
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        try {
            HotUpdateHelper helper = new HotUpdateHelper(this);
            helper.loadAppliedPatch();
            
            // 检查是否有已应用的补丁
            if (helper.hasAppliedPatch()) {
                PatchInfo patchInfo = helper.getAppliedPatchInfo();
                Log.i(TAG, "已加载补丁: " + patchInfo.getPatchVersion());
            } else {
                Log.d(TAG, "没有已应用的补丁");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "加载补丁失败", e);
            // 不要抛出异常，让应用继续运行
        }
    }
}
```

#### 2. 条件加载补丁

```java
public class MyApplication extends Application {
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 只在非调试模式下加载补丁
        if (!BuildConfig.DEBUG) {
            HotUpdateHelper helper = new HotUpdateHelper(this);
            helper.loadAppliedPatch();
        }
    }
}
```

#### 3. PatchApplication 完整源码（Demo 实现）

Demo 应用中的 `PatchApplication` 完整实现如下（位于 `app/src/main/java/com/orange/update/PatchApplication.java`）：

```java
package com.orange.update;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * 热更新 Application
 * 
 * 在 attachBaseContext 中加载补丁，确保：
 * 1. DEX 补丁在类加载前注入
 * 2. 资源补丁在 Activity 创建前加载
 * 3. 所有组件都能使用更新后的代码和资源
 */
public class PatchApplication extends Application {
    
    private static final String TAG = "PatchApplication";
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 在最早的时机加载补丁
        loadPatchIfNeeded();
    }
    
    /**
     * 加载已应用的补丁
     * 
     * 1. 检查是否有已应用的补丁
     * 2. 如果补丁包含资源，使用 ResourceMerger 合并原始 APK 和补丁资源
     * 3. 生成完整资源包到 merged_resources.apk
     * 4. 加载完整资源包（而不是直接使用补丁）
     * 5. 加载 DEX 补丁
     */
    private void loadPatchIfNeeded() {
        try {
            // 注意：在 attachBaseContext 中不能使用 getApplicationContext()
            // 因为 Application 还没有完全初始化，需要手动创建 SharedPreferences
            android.content.SharedPreferences prefs = getSharedPreferences("patch_storage_prefs", Context.MODE_PRIVATE);
            String appliedPatchId = prefs.getString("applied_patch_id", null);
            
            if (appliedPatchId == null || appliedPatchId.isEmpty()) {
                Log.d(TAG, "No applied patch to load");
                return;
            }
            
            Log.d(TAG, "Loading applied patch: " + appliedPatchId);
            
            // 获取已应用的补丁文件
            java.io.File updateDir = new java.io.File(getFilesDir(), "update");
            java.io.File appliedDir = new java.io.File(updateDir, "applied");
            java.io.File appliedFile = new java.io.File(appliedDir, "current_patch.zip");
            
            if (!appliedFile.exists()) {
                Log.w(TAG, "Applied patch file not found: " + appliedFile.getAbsolutePath());
                return;
            }
            
            String patchPath = appliedFile.getAbsolutePath();
            
            // 检查补丁是否包含资源
            if (hasResourcePatch(appliedFile)) {
                Log.d(TAG, "Patch contains resources, merging with original APK");
                
                // 使用 ResourceMerger 合并资源（Tinker 的方式）
                java.io.File mergedResourceFile = new java.io.File(appliedDir, "merged_resources.apk");
                
                boolean merged = ResourceMerger.mergeResources(
                    this, appliedFile, mergedResourceFile);
                
                if (merged && mergedResourceFile.exists()) {
                    Log.i(TAG, "Resources merged successfully, size: " + mergedResourceFile.length());
                    // 使用合并后的完整资源包
                    patchPath = mergedResourceFile.getAbsolutePath();
                } else {
                    Log.w(TAG, "Failed to merge resources, using patch directly");
                }
            }
            
            // 注入 DEX 补丁
            if (!DexPatcher.isPatchInjected(this, patchPath)) {
                DexPatcher.injectPatchDex(this, patchPath);
                Log.d(TAG, "Dex patch loaded successfully");
            }
            
            // 加载资源补丁（使用合并后的完整资源包）
            try {
                ResourcePatcher.loadPatchResources(this, patchPath);
                Log.d(TAG, "Resource patch loaded successfully");
            } catch (ResourcePatcher.PatchResourceException e) {
                Log.w(TAG, "Failed to load resource patch", e);
            }
            
            Log.i(TAG, "Patch loading completed in attachBaseContext");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load patch in attachBaseContext", e);
        }
    }
    
    /**
     * 检查补丁是否包含资源
     */
    private boolean hasResourcePatch(java.io.File patchFile) {
        String fileName = patchFile.getName().toLowerCase(java.util.Locale.ROOT);
        
        // 如果是 APK 或 ZIP 文件，可能包含资源
        if (fileName.endsWith(".apk") || fileName.endsWith(".zip")) {
            return true;
        }
        
        // 如果是纯 DEX 文件，不包含资源
        if (fileName.endsWith(".dex")) {
            return false;
        }
        
        // 检查文件魔数
        try {
            byte[] header = new byte[4];
            java.io.FileInputStream fis = new java.io.FileInputStream(patchFile);
            fis.read(header);
            fis.close();
            
            // ZIP/APK 魔数: PK (0x50 0x4B)
            if (header[0] == 0x50 && header[1] == 0x4B) {
                return true;
            }
            
            // DEX 魔数: dex\n (0x64 0x65 0x78 0x0A)
            if (header[0] == 0x64 && header[1] == 0x65 && 
                header[2] == 0x78 && header[3] == 0x0A) {
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check patch file type", e);
        }
        
        return false;
    }
}
```

**关键实现细节：**

1. **在 attachBaseContext 中不能使用 getApplicationContext()**
   - Application 还没有完全初始化
   - 需要直接使用 `getSharedPreferences()` 和 `getFilesDir()`

2. **资源合并是必须的**
   - 补丁只包含差异资源，不是完整资源包
   - 使用 `ResourceMerger.mergeResources()` 合并原始 APK 和补丁
   - 生成完整资源包到 `merged_resources.apk`
   - 加载完整资源包而不是直接使用补丁

3. **文件类型检测**
   - 通过文件扩展名和魔数判断是否包含资源
   - ZIP/APK 魔数: `PK` (0x50 0x4B)
   - DEX 魔数: `dex\n` (0x64 0x65 0x78 0x0A)

4. **错误处理**
   - 资源加载失败时使用 try-catch 捕获
   - 不影响应用正常启动
   - 记录详细日志便于调试

### 常见问题

#### Q1: 为什么补丁不生效？

**检查清单：**
1. ✅ 是否在 `attachBaseContext()` 中调用了 `loadAppliedPatch()`？
2. ✅ 是否在 `AndroidManifest.xml` 中注册了自定义 Application？
3. ✅ 是否成功应用了补丁？（检查日志）
4. ✅ 资源更新是否重启了应用？

#### Q2: 如何验证补丁是否加载？

```java
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    
    HotUpdateHelper helper = new HotUpdateHelper(this);
    helper.loadAppliedPatch();
    
    // 验证补丁是否加载
    if (helper.hasAppliedPatch()) {
        PatchInfo info = helper.getAppliedPatchInfo();
        Log.i(TAG, "✅ 补丁已加载");
        Log.i(TAG, "补丁ID: " + info.getPatchId());
        Log.i(TAG, "补丁版本: " + info.getPatchVersion());
    } else {
        Log.d(TAG, "没有已应用的补丁");
    }
}
```

#### Q3: 如何在多进程应用中使用？

如果你的应用有多个进程，需要在每个进程的 Application 中都加载补丁：

```java
public class MyApplication extends Application {
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 获取当前进程名
        String processName = getProcessName();
        Log.d(TAG, "Current process: " + processName);
        
        // 在所有进程中加载补丁
        HotUpdateHelper helper = new HotUpdateHelper(this);
        helper.loadAppliedPatch();
    }
    
    private String getProcessName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        
        // API < 28 的兼容方案
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentProcessName = activityThread.getDeclaredMethod("currentProcessName");
            return (String) currentProcessName.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }
}
```

#### Q4: 如何处理加载失败？

```java
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    
    try {
        HotUpdateHelper helper = new HotUpdateHelper(this);
        helper.loadAppliedPatch();
        
    } catch (Exception e) {
        Log.e(TAG, "补丁加载失败", e);
        
        // 选项1: 清除失败的补丁
        try {
            HotUpdateHelper helper = new HotUpdateHelper(this);
            helper.clearPatch();
            Log.i(TAG, "已清除失败的补丁");
        } catch (Exception ex) {
            Log.e(TAG, "清除补丁失败", ex);
        }
        
        // 选项2: 上报错误到服务器
        reportError("patch_load_failed", e.getMessage());
        
        // 不要抛出异常，让应用继续运行
    }
}
```

#### Q5: 如何在 Application 中显示补丁信息？

```java
public class MyApplication extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 在 onCreate 中可以安全地访问 UI
        HotUpdateHelper helper = new HotUpdateHelper(this);
        
        if (helper.hasAppliedPatch()) {
            PatchInfo info = helper.getAppliedPatchInfo();
            
            // 显示 Toast（可选）
            if (BuildConfig.DEBUG) {
                Toast.makeText(this, 
                    "已加载补丁: " + info.getPatchVersion(), 
                    Toast.LENGTH_SHORT).show();
            }
            
            // 或者发送广播通知 Activity
            Intent intent = new Intent("com.example.PATCH_LOADED");
            intent.putExtra("patch_version", info.getPatchVersion());
            sendBroadcast(intent);
        }
    }
}
```

### 性能优化

#### 1. 异步加载（不推荐）

补丁加载必须在 `attachBaseContext` 中**同步**完成，不能异步加载，否则会导致类加载冲突。

**错误示例（不要这样做）：**
```java
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    
    // ❌ 错误：异步加载会导致类加载冲突
    new Thread(() -> {
        HotUpdateHelper helper = new HotUpdateHelper(this);
        helper.loadAppliedPatch();
    }).start();
}
```

#### 2. 缓存优化

`HotUpdateHelper` 已经内置了缓存优化：
- 补丁文件缓存在 `/data/data/{package}/files/update/applied/`
- 资源合并结果缓存为 `merged_resources.apk`
- 避免重复合并和解密

#### 3. 启动时间优化

补丁加载通常只需要 50-200ms，对启动时间影响很小。如果需要进一步优化：

```java
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    
    long startTime = System.currentTimeMillis();
    
    HotUpdateHelper helper = new HotUpdateHelper(this);
    helper.loadAppliedPatch();
    
    long endTime = System.currentTimeMillis();
    Log.d(TAG, "补丁加载耗时: " + (endTime - startTime) + "ms");
}
```

### 完整示例

```java
package com.example.myapp;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import com.orange.update.HotUpdateHelper;
import com.orange.update.PatchInfo;

/**
 * 自定义 Application
 * 
 * 功能：
 * 1. 在 attachBaseContext 中加载补丁
 * 2. 记录补丁加载日志
 * 3. 处理加载失败情况
 */
public class MyApplication extends Application {
    
    private static final String TAG = "MyApplication";
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 加载补丁
        loadPatch();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化其他 SDK
        initSDKs();
        
        // 显示补丁信息（可选）
        showPatchInfo();
    }
    
    /**
     * 加载补丁
     */
    private void loadPatch() {
        try {
            long startTime = System.currentTimeMillis();
            
            HotUpdateHelper helper = new HotUpdateHelper(this);
            helper.loadAppliedPatch();
            
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "补丁加载耗时: " + (endTime - startTime) + "ms");
            
            // 验证补丁是否加载
            if (helper.hasAppliedPatch()) {
                PatchInfo info = helper.getAppliedPatchInfo();
                Log.i(TAG, "✅ 补丁已加载: " + info.getPatchVersion());
            } else {
                Log.d(TAG, "没有已应用的补丁");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "补丁加载失败", e);
            
            // 清除失败的补丁
            try {
                HotUpdateHelper helper = new HotUpdateHelper(this);
                helper.clearPatch();
                Log.i(TAG, "已清除失败的补丁");
            } catch (Exception ex) {
                Log.e(TAG, "清除补丁失败", ex);
            }
        }
    }
    
    /**
     * 初始化第三方 SDK
     */
    private void initSDKs() {
        // 初始化其他 SDK
        Log.d(TAG, "初始化 SDK");
    }
    
    /**
     * 显示补丁信息
     */
    private void showPatchInfo() {
        HotUpdateHelper helper = new HotUpdateHelper(this);
        
        if (helper.hasAppliedPatch()) {
            PatchInfo info = helper.getAppliedPatchInfo();
            
            Log.i(TAG, "=== 补丁信息 ===");
            Log.i(TAG, "补丁ID: " + info.getPatchId());
            Log.i(TAG, "补丁版本: " + info.getPatchVersion());
            Log.i(TAG, "应用时间: " + new java.util.Date(info.getCreateTime()));
            Log.i(TAG, "===============");
        }
    }
}
```

**AndroidManifest.xml：**
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myapp">
    
    <!-- 权限声明 -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    
    <application
        android:name=".MyApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyApp">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
    </application>
    
</manifest>
```

## Demo 应用使用

### 界面功能

1. **标题卡片** - 显示应用版本和状态
2. **文件选择卡片** - 选择基准 APK 和新 APK
3. **补丁操作卡片** - 生成、应用、清除补丁
4. **签名验证卡片** - 生成密钥、验证签名、配置密钥
5. **信息显示卡片** - 显示系统信息和结果

### 测试流程

#### 测试签名验证

1. **生成 RSA 密钥对**
   - 点击「🔑 生成密钥」按钮
   - 密钥自动保存到 `/sdcard/Download/`
   - 显示公钥和私钥信息

2. **加载已有密钥**
   - 点击「🔑 加载密钥」按钮
   - 自动从下载目录加载密钥文件
   - 支持手动编辑密钥文件

3. **配置自定义密钥**
   - 点击「⚙️ 配置密钥」按钮
   - 输入自己的公钥和私钥（Base64 格式）
   - 或点击「加载现有密钥」自动填充
   - 点击「保存」验证并保存密钥

4. **测试签名验证成功**
   - 点击「✅ 验证成功」按钮
   - 使用真实的 RSA 签名算法
   - 显示签名和验证结果

5. **测试签名验证失败**
   - 点击「❌ 验证失败」按钮
   - 模拟补丁被篡改的情况
   - 显示验证失败信息

#### 测试加密和签名

1. **生成带安全选项的补丁**
   - 选择基准 APK 和新 APK
   - 点击「生成补丁」
   - 在弹出的对话框中选择：
     - ✅ 🔒 对补丁进行签名（防止篡改）
     - ✅ 🔐 对补丁进行加密（保护内容）
     - 可选输入加密密码（留空使用默认密钥）
   - 点击「生成」

2. **查看生成的文件**
   - 无保护：`patch_[timestamp].zip`
   - 仅签名：`patch_[timestamp].zip` + `.sig`
   - 仅加密：`patch_[timestamp].zip.enc`
   - 签名+加密：`patch_[timestamp].zip.enc` + `.enc.sig`
   - 密码加密：额外生成 `.pwd` 密码提示文件

3. **应用加密补丁**
   - 点击「应用补丁」
   - Demo 应用会自动检测 `.enc` 扩展名
   - 如果使用了密码加密，Demo 会弹出密码输入对话框（仅用于演示）
   - 输入正确密码后进行解密
   - 显示「正在解密补丁...」进度
   - 解密成功后应用补丁
   - 显示应用结果
   - **注意**：实际应用中应通过 API 参数传入密码，而不是弹窗

4. **应用签名补丁**
   - 点击「应用补丁」
   - 自动检测 `.sig` 签名文件
   - 显示「正在验证补丁签名...」
   - 验证通过后继续应用
   - 验证失败则拒绝应用并显示详细错误

5. **配置安全策略**
   - 点击「🛡️ 安全策略设置」按钮
   - 配置以下选项：
     - 🔒 强制要求补丁签名
     - 🔐 强制要求补丁加密
   - 点击「保存」
   - 设置立即生效

6. **测试安全策略**
   - 开启「强制要求签名」后
   - 尝试应用未签名的补丁
   - 会显示拒绝提示和原因
   - 可以点击「安全设置」快速修改策略

#### 测试 DEX 和资源热更新

1. **安装基准版本**
   ```bash
   adb install test-apks/app-v1.0-dex-res.apk
   ```

2. **生成补丁**
   - 打开应用
   - 选择 `app-v1.0-dex-res.apk` 作为基准
   - 选择 `app-v1.2-dex-res.apk` 作为新版本
   - 点击「生成补丁」

3. **应用补丁**
   - 点击「应用补丁」
   - DEX 立即生效
   - 重启后资源生效

#### 测试 Assets 热更新

1. **安装基准版本**
   ```bash
   adb install test-apks/app-v1.0-assets.apk
   ```

2. **生成并应用补丁**
   - 选择两个 APK
   - 生成并应用补丁
   - 重启应用

3. **验证更新**
   - 点击「测试 Assets 文件」
   - 查看内容是否更新

### 输出目录

所有生成的补丁文件默认保存在：
```
/sdcard/Download/patch_<timestamp>.zip
```

## 命令行工具

### 编译

```bash
./gradlew :patch-cli:build
```

### 使用

```bash
java -jar patch-cli/build/libs/patch-cli.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip
```

### 参数说明

- `--base` - 基准 APK 路径
- `--new` - 新 APK 路径
- `--output` - 输出补丁路径

## Gradle 插件

### 配置

```groovy
// 项目根目录 build.gradle
buildscript {
    dependencies {
        classpath 'com.github.706412584.Android_hotupdate:patch-gradle-plugin:v1.2.9'
    }
}

// app/build.gradle
plugins {
    id 'com.orange.patch'
}

patchGenerator {
    baselineApk = file("baseline/app-release.apk")
    outputDir = file("build/patch")
}
```

### 使用

```bash
./gradlew generateReleasePatch
```

---

**返回**: [主文档](../README.md) | [常见问题](FAQ.md) | [补丁格式](PATCH_FORMAT.md)
