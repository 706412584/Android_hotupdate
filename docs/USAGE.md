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
- 自动解密：应用补丁时自动检测并解密

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

RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(decryptedPatch, callback);
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
    RealHotUpdate hotUpdate = new RealHotUpdate(context);
    hotUpdate.applyPatch(decryptedPatch, new RealHotUpdate.ApplyCallback() {
        @Override
        public void onProgress(int percent, String message) {
            Log.d(TAG, message + ": " + percent + "%");
        }
        
        @Override
        public void onSuccess(RealHotUpdate.PatchResult result) {
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

## 补丁应用流程

### 1. 应用补丁

```java
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(patchFile, new RealHotUpdate.ApplyCallback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, message + ": " + percent + "%");
    }
    
    @Override
    public void onSuccess(RealHotUpdate.PatchResult result) {
        Log.i(TAG, "热更新成功！");
        Log.i(TAG, "新版本: " + result.newVersion);
        Log.i(TAG, "DEX 注入: " + result.dexInjected);
        Log.i(TAG, "SO 加载: " + result.soLoaded);
        Log.i(TAG, "资源加载: " + result.resourcesLoaded);
        
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
    RealHotUpdate hotUpdate = new RealHotUpdate(context);
    hotUpdate.applyPatch(decryptedPatch, callback);
    
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
RealHotUpdate hotUpdate = new RealHotUpdate(context);

// Demo 应用会自动检测 .enc 扩展名
File encryptedPatch = new File("/path/to/patch.zip.enc");
hotUpdate.applyPatch(encryptedPatch, new RealHotUpdate.ApplyCallback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, message + ": " + percent + "%");
    }
    
    @Override
    public void onSuccess(RealHotUpdate.PatchResult result) {
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
    hotUpdate.applyPatch(decryptedPatch, callback);
    
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
    RealHotUpdate hotUpdate = new RealHotUpdate(context);
    hotUpdate.applyPatch(patchFile, callback);
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
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(decryptedPatch, new RealHotUpdate.ApplyCallback() {
    @Override
    public void onSuccess(RealHotUpdate.PatchResult result) {
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
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.clearPatch();

// 清除并重启
hotUpdate.clearPatch();
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
// 配置安全策略
SharedPreferences securityPrefs = context.getSharedPreferences("security_policy", MODE_PRIVATE);
securityPrefs.edit()
    .putBoolean("require_signature", true)  // 强制要求签名
    .putBoolean("require_encryption", true) // 强制要求加密
    .apply();

// 应用补丁时会自动检查安全策略
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(patchFile, new RealHotUpdate.ApplyCallback() {
    @Override
    public void onSuccess(RealHotUpdate.PatchResult result) {
        Log.i(TAG, "补丁应用成功");
    }
    
    @Override
    public void onError(String message) {
        // 如果补丁不符合安全策略，会返回错误
        // 例如："当前安全策略要求补丁必须签名"
        Log.e(TAG, "应用失败: " + message);
    }
});
```

**安全策略说明：**
- `require_signature`: 开启后只能应用已签名的补丁
- `require_encryption`: 开启后只能应用已加密的补丁
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

为了让补丁在应用启动时自动加载，需要在 `Application` 中集成：

```java
public class MyApplication extends Application {
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 加载已应用的补丁
        RealHotUpdate hotUpdate = new RealHotUpdate(this);
        hotUpdate.loadAppliedPatch();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 其他初始化代码
    }
}
```

**AndroidManifest.xml：**
```xml
<application
    android:name=".MyApplication"
    ...>
</application>
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
   - 自动检测 `.enc` 扩展名
   - 如果使用了密码加密，会弹出密码输入对话框
   - 输入正确密码后自动解密
   - 显示「正在解密补丁...」进度
   - 解密成功后自动应用
   - 显示应用结果

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
        classpath 'com.github.706412584.Android_hotupdate:patch-gradle-plugin:vv1.2.5'
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
