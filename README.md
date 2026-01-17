# Android 热更新补丁工具

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![JitPack](https://jitpack.io/v/706412584/Android_hotupdate.svg)](https://jitpack.io/#706412584/Android_hotupdate)

一套完整的 Android 热更新解决方案，支持 **DEX、资源、SO 库、Assets** 的热更新，无需重新安装 APK。

## ✨ 核心特性

- 🔥 **真正的热更新** - 无需重启应用，代码立即生效
- 📦 **完整支持** - DEX、资源、SO 库、Assets 全面支持
- 🚀 **高性能** - Native 引擎加速，补丁生成快 2-3 倍
- 📱 **设备端生成** - 支持在 Android 设备上直接生成补丁
- 🛠️ **多种方式** - 命令行、Gradle 插件、Android SDK
- 🔒 **安全可靠** - 支持签名验证，防止篡改
- 🎯 **兼容性好** - 支持 Android 5.0+ (API 21+)
- ⚡ **自动降级** - Native 不可用时自动使用 Java 引擎

## 📚 文档导航

- **[快速开始](#-快速开始)** - 5 分钟上手
- **[签名验证](#6-使用签名验证可选推荐生产环境使用)** - 保护补丁安全
- **[Demo 下载](https://github.com/706412584/Android_hotupdate/releases/tag/demo)** - 下载体验 APK
- **[详细使用文档](docs/USAGE.md)** - 完整的使用说明
- **[常见问题](docs/FAQ.md)** - 问题排查指南
- **[JitPack 发布指南](JITPACK_RELEASE.md)** - 如何发布新版本
- **[补丁包格式说明](docs/PATCH_FORMAT.md)** - 补丁包结构详解

## 🚀 快速开始

### 方式一：使用 JitPack（推荐）

**1. 添加 JitPack 仓库**

在 `settings.gradle` 中添加：

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**2. 添加依赖**

```groovy
dependencies {
    // 补丁生成 SDK
    implementation 'com.github.706412584.Android_hotupdate:patch-generator-android:v1.2.4'
    
    // 热更新 SDK
    implementation 'com.github.706412584.Android_hotupdate:update:v1.2.4'
}
```

**3. 生成补丁**

```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .callback(new SimpleAndroidGeneratorCallback() {
        @Override
        public void onComplete(PatchResult result) {
            if (result.isSuccess()) {
                Log.i(TAG, "补丁生成成功");
            }
        }
    })
    .build();

generator.generateInBackground();
```

**4. 应用补丁**

```java
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(patchFile, new RealHotUpdate.ApplyCallback() {
    @Override
    public void onSuccess(RealHotUpdate.PatchResult result) {
        Log.i(TAG, "热更新成功！");
        // DEX 和 SO 立即生效
        // 资源更新需要重启应用
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "热更新失败: " + message);
    }
});
```

**5. 在 Application 中集成**

```java
public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 加载已应用的补丁
        RealHotUpdate hotUpdate = new RealHotUpdate(this);
        hotUpdate.loadAppliedPatch();
    }
}
```

**6. 使用签名验证（可选，推荐生产环境使用）**

为了防止补丁被篡改，可以启用签名验证：

```java
// 步骤 1: 生成 RSA 密钥对（在开发机器上执行一次）
// 使用 keytool 或 openssl 生成密钥对
// keytool -genkeypair -alias patch_key -keyalg RSA -keysize 2048 -validity 10000 -keystore patch.keystore

// 步骤 2: 导出公钥（Base64 格式）
// keytool -exportcert -alias patch_key -keystore patch.keystore -rfc -file public_key.pem
// 然后将 PEM 文件转换为 Base64 字符串

// 步骤 3: 在应用中配置公钥
SecurityManager securityManager = new SecurityManager(context);
String publicKeyBase64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."; // 你的公钥
securityManager.setSignaturePublicKey(publicKeyBase64);

// 步骤 4: 生成补丁时签名（在服务器端）
// 使用私钥对补丁文件进行签名
// openssl dgst -sha256 -sign private_key.pem -out patch.sig patch.zip
// base64 patch.sig > patch.sig.base64

// 步骤 5: 应用补丁时验证签名
String patchSignature = "从服务器获取的 Base64 签名"; // 从服务器下载的签名
File patchFile = new File("/path/to/patch.zip");

// 验证签名
if (securityManager.verifySignature(patchFile, patchSignature)) {
    Log.i(TAG, "签名验证通过，可以安全应用补丁");
    // 应用补丁
    hotUpdate.applyPatch(patchFile, callback);
} else {
    Log.e(TAG, "签名验证失败，补丁可能被篡改！");
    // 拒绝应用补丁
}
```

**完整的签名验证流程示例：**

```java
// 在 UpdateManager 中集成签名验证
UpdateConfig config = new UpdateConfig.Builder()
    .serverUrl("https://example.com")
    .appKey("your-app-key")
    .appVersion("1.0.0")
    .debugMode(false)  // 生产环境必须关闭调试模式
    .build();

UpdateManager.init(context, config);

// 设置公钥
SecurityManager securityManager = new SecurityManager(context);
securityManager.setSignaturePublicKey("你的公钥Base64字符串");

// 检查更新并验证签名
UpdateManager.getInstance().setCallback(new SimpleUpdateCallback() {
    @Override
    public void onCheckComplete(boolean hasUpdate, PatchInfo patchInfo) {
        if (hasUpdate) {
            // 下载补丁
            UpdateManager.getInstance().downloadPatch(patchInfo, new DownloadCallback() {
                @Override
                public void onComplete(File patchFile) {
                    // 验证签名
                    String signature = patchInfo.getSignature(); // 从服务器返回的签名
                    if (securityManager.verifySignature(patchFile, signature)) {
                        // 签名验证通过，应用补丁
                        UpdateManager.getInstance().applyPatch(patchInfo);
                    } else {
                        Log.e(TAG, "签名验证失败！");
                    }
                }
            });
        }
    }
});

UpdateManager.getInstance().checkUpdate();
```

**注意事项：**
- 🔒 **生产环境必须启用签名验证**，防止恶意补丁
- 🔑 **私钥必须妥善保管**，只在服务器端使用
- 📱 **公钥可以打包到 APK 中**，用于客户端验证
- 🐛 **调试模式下可以跳过签名验证**，方便开发测试
- ✅ **签名算法使用 SHA256withRSA**，安全可靠

### 方式二：使用 Demo 应用

**下载 Demo APK：** https://github.com/706412584/Android_hotupdate/releases/tag/demo

或者自己编译：

```bash
# 安装 Demo
./gradlew :app:installDebug

# 或使用测试 APK
adb install test-apks/app-v1.0-dex-res.apk
```

在 Demo 应用中：
1. 选择基准 APK 和新 APK
2. 点击「生成补丁」
3. 点击「应用补丁」
4. 热更新立即生效

## 🔄 补丁回滚

如果需要回滚到原始版本：

```java
// 方式一：简单回滚
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.clearPatch();
Toast.makeText(context, "补丁已清除，请重启应用", Toast.LENGTH_LONG).show();

// 方式二：清除并自动重启
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.clearPatch();

Intent intent = context.getPackageManager()
    .getLaunchIntentForPackage(context.getPackageName());
if (intent != null) {
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    android.os.Process.killProcess(android.os.Process.myPid());
}
```

## 📁 项目结构

```
├── patch-core/              # 核心库 - 补丁生成引擎
├── patch-native/            # Native 库 - C/C++ 高性能引擎
├── patch-generator-android/ # Android SDK - 设备端生成
├── patch-cli/               # 命令行工具 - PC/服务器端
├── patch-gradle-plugin/     # Gradle 插件 - 构建集成
├── update/                  # 热更新 SDK - 补丁应用
└── app/                     # Demo 应用
```

| 模块 | 说明 | 文档 |
|------|------|------|
| **patch-generator-android** | Android SDK，设备端补丁生成 | [README](patch-generator-android/README.md) |
| **update** | 热更新 SDK，补丁应用和加载 | - |
| **patch-core** | 核心引擎，APK 解析、差异比较 | [README](patch-core/README.md) |
| **patch-native** | Native SO 库，BsDiff 算法 | [README](patch-native/README.md) |
| **patch-cli** | 命令行工具，独立运行 | [README](patch-cli/README.md) |
| **patch-gradle-plugin** | Gradle 插件，构建集成 | [README](patch-gradle-plugin/README.md) |

## 💡 热更新原理

- **DEX 热更新**：通过反射修改 ClassLoader 的 dexElements，立即生效
- **资源热更新**：替换 AssetManager，需要重启 Activity
- **SO 库热更新**：修改 nativeLibraryPathElements，立即生效
- **Assets 热更新**：随资源一起加载，需要重启

详细原理说明请查看 [使用文档](docs/USAGE.md#热更新原理)

## ❓ 常见问题

### Q: 支持哪些 Android 版本？
**A:** 支持 Android 5.0+ (API 21+)，推荐 Android 7.0+ (API 24+)

### Q: 可以热更新 AndroidManifest.xml 吗？
**A:** 不可以，这是 安卓机制 的限制，需要重新安装 APK

### Q: 资源更新为什么需要重启？
**A:** 资源需要重新加载到 AssetManager，需要重启 Activity 才能看到新界面

### Q: 如何回滚补丁？
**A:** 调用 `hotUpdate.clearPatch()` 然后重启应用

### Q: 如何启用签名验证？
**A:** 使用 `SecurityManager.setSignaturePublicKey()` 设置公钥，然后在应用补丁前调用 `verifySignature()` 验证。详见[签名验证](#6-使用签名验证可选推荐生产环境使用)章节

### Q: 调试模式下可以跳过签名验证吗？
**A:** 可以，在 `UpdateConfig` 中设置 `debugMode(true)` 即可跳过签名验证，但生产环境必须关闭

### Q: 支持加固的APK吗（360加固等）？
**A:** 部分支持，建议在加固前生成补丁，加固后充分测试。详见 [常见问题 - 加固相关](docs/FAQ.md#加固相关)

更多问题请查看 [常见问题文档](docs/FAQ.md)

## 📋 系统要求

### 开发环境
- Java 11+
- Android SDK 21+
- Gradle 8.9+
- NDK 27.0+ (仅编译 Native 模块)

### 运行环境
- 最低版本：Android 5.0 (API 21)
- 推荐版本：Android 7.0+ (API 24+)
- 目标版本：Android 14 (API 34)

## 🔧 编译

```bash
# 编译所有模块
./gradlew build

# 编译并安装 Demo
./gradlew :app:installDebug

# 运行测试
./gradlew test
```

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

```
Copyright 2024 Orange Update

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🙏 致谢

本项目参考了以下优秀的开源项目：
- [Tinker](https://github.com/Tencent/tinker) - 腾讯的 Android 热修复方案
- [Robust](https://github.com/Meituan-Dianping/Robust) - 美团的热修复方案

## 📞 联系方式

- **GitHub Issues**: [提交问题](https://github.com/706412584/Android_hotupdate/issues)
- **Email**: 706412584@qq.com

---

**⭐ 如果这个项目对你有帮助，请给个 Star！**
