# Android 热更新补丁工具

中文 | [English](README_EN.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)


一套完整的 Android 热更新解决方案，支持 **DEX、资源、SO 库、Assets** 的热更新，无需重新安装 APK。

## ✨ 核心特性

- 🔥 **真正的热更新** - 无需重启应用，代码立即生效
- 📦 **完整支持** - DEX、资源、SO 库、Assets 全面支持
- 🚀 **高性能** - Native 引擎加速，补丁生成快 2-3 倍
- 📱 **设备端生成** - 支持在 Android 设备上直接生成补丁
- 🛠️ **多种方式** - 命令行、Gradle 插件、Android SDK
- 🔒 **安全可靠** - 支持签名验证和加密，防止篡改
- 🎯 **兼容性好** - 支持 Android 5.0+ (API 21+)
- ⚡ **自动降级** - Native 不可用时自动使用 Java 引擎
- 🌐 **管理后台** - 🆕 Web 管理后台，支持灰度发布、统计分析


## 📚 文档导航

- **[快速开始](#-快速开始)** - 5 分钟上手
- **[安全机制](#-安全机制)** - 签名验证和加密保护
- **[架构说明](docs/ARCHITECTURE.md)** - 核心算法统一性说明
- **[Demo 下载](https://github.com/706412584/Android_hotupdate/releases/tag/demo)** - 下载体验 APK
- **[详细使用文档](docs/USAGE.md)** - 完整的 API 使用说明
- **[常见问题](docs/FAQ.md)** - 问题排查指南
- **[补丁包格式](docs/PATCH_FORMAT.md)** - 补丁包结构详解
- **[发布指南](JITPACK_RELEASE.md)** - 如何发布新版本

## 🚀 快速开始

### 1. 添加依赖

**方式一：使用 Maven Central（推荐）**

```groovy
dependencies {
    // 热更新核心库（必须，包含完整功能）
    implementation 'io.github.706412584:update:1.3.3'
    
    // 如果需要在设备上生成补丁（不推荐，推荐直接使用官方demo的apk），添加：
    implementation 'io.github.706412584:patch-generator-android:1.3.3'
    
    // 如果需要 Native 高性能引擎（可选，自动降级）
    implementation 'io.github.706412584:patch-native:1.3.3'
    
    // 如果需要核心补丁引擎（可选，通常不需要单独引入）
    implementation 'io.github.706412584:patch-core:1.3.3'
}
```

**Maven Central 组件列表：**

| 组件 | Maven 坐标 | 说明 |
|------|-----------|------|
| **update** | `io.github.706412584:update:1.3.3` | 热更新核心库，必需 |
| **patch-generator-android** | `io.github.706412584:patch-generator-android:1.3.3` | 设备端补丁生成 |
| **patch-native** | `io.github.706412584:patch-native:1.3.3` | Native 高性能引擎（AAR） |
| **patch-core** | `io.github.706412584:patch-core:1.3.3` | 核心补丁引擎 |
| **patch-cli** | [下载 JAR](https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.3/patch-cli-1.3.3-all.jar) | 命令行工具（独立运行） |

> 💡 **提示**：
> - `update` 库已包含基本功能，大多数情况下只需要这一个依赖
> - `patch-native` 提供 2-3 倍性能提升，不可用时自动降级到 Java 引擎
> - `patch-cli` 是独立的命令行工具，不需要添加到项目依赖中

### 2. 生成补丁

**方式一：使用命令行工具（推荐用于 CI/CD）**

```bash
# 下载 patch-cli
wget https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.3/patch-cli-1.3.3-all.jar

# 生成带签名的补丁
java -jar patch-cli-1.3.3-all.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip \
  --keystore keystore.jks \
  --keystore-password <password> \
  --key-alias <alias> \
  --key-password <password>
```

**方式二：使用 Android SDK（设备端生成）**

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

**方式三：使用 Gradle 插件（构建时生成）**

```gradle
patchGenerator {
    baselineApk = file("baseline/app-v1.0.apk")
    outputDir = file("build/patch")
    
    signing {
        keystoreFile = file("keystore.jks")
        keystorePassword = "password"
        keyAlias = "alias"
        keyPassword = "password"
    }
}
```

> 📖 **详细说明**：[patch-cli 使用文档](patch-cli/README.md)

### 3. 应用补丁

使用 `HotUpdateHelper` 类（推荐）：

```java
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, "进度: " + percent + "% - " + message);
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        Log.i(TAG, "热更新成功！");
        Log.i(TAG, "补丁版本: " + result.patchVersion);
        // DEX 和 SO 立即生效，资源更新需要重启应用
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "热更新失败: " + message);
    }
});
```

> 💡 **更多应用方式**：
> - [使用 PatchApplier](docs/USAGE.md#使用-patchapplier) - 更灵活的控制
> - [使用底层 API](docs/USAGE.md#使用底层-api) - DexPatcher、SoPatcher、ResourcePatcher
> - [使用 UpdateManager](docs/USAGE.md#使用-updatemanager) - 服务器端更新流程

### 4. 在 Application 中集成

```java
public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 加载已应用的补丁（必须在 attachBaseContext 中调用）
        HotUpdateHelper helper = new HotUpdateHelper(this);
        helper.loadAppliedPatch();
    }
}
```

> 📖 **详细配置说明**：[Application 集成指南](docs/USAGE.md#application-集成)

## 🛡️ 安全机制

为了防止补丁被篡改和窃取，系统提供了 **APK 签名验证** 和 **AES 加密** 两种安全机制。

### APK 签名验证（推荐）

使用与 APK 相同的签名密钥对补丁进行签名，确保补丁来源可信且未被篡改。

**生成带签名的补丁：**

```groovy
// build.gradle
patchGenerator {
    baselineApk = file("baseline/app-v1.2.apk")
    outputDir = file("build/patch")
    
    // 配置签名（使用与 APK 相同的签名密钥）
    signing {
        keystoreFile = file("keystore/app.jks")
        keystorePassword = "your_keystore_password"
        keyAlias = "your_key_alias"
        keyPassword = "your_key_password"
    }
}
```

**启用签名验证：**

```java
HotUpdateHelper helper = new HotUpdateHelper(context);

// 强制要求补丁签名（推荐生产环境开启）
helper.setRequireSignature(true);

// 应用补丁时会自动验证签名
helper.applyPatch(patchFile, callback);
```

**签名验证原理：**
- 补丁生成时使用 JarSigner 生成完整的 JAR 签名（META-INF/MANIFEST.MF, .SF, .RSA）
- 应用补丁时使用 apksig 库验证签名与 APK 签名是否匹配
- 如果签名不匹配或被删除，补丁会被自动拒绝并清除

### AES 加密保护

使用 AES-256-GCM 加密补丁内容，防止补丁被窃取或逆向分析。

**使用自定义密码加密（推荐）：**

```java
// 加密补丁
SecurityManager securityManager = new SecurityManager(context);
String password = "your_secure_password";
File encryptedPatch = securityManager.encryptPatchWithPassword(patchFile, password);

// 应用加密补丁
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatchWithAesPassword(encryptedPatch, password, callback);
```

**使用 ZIP 密码保护（兼容性最好）：**

```java
// 应用带 ZIP 密码的补丁
HotUpdateHelper helper = new HotUpdateHelper(context);
String zipPassword = "your_zip_password";
helper.applyPatchWithZipPassword(patchFile, zipPassword, callback);
```

> 💡 **更多加密方式**：
> - [Android KeyStore 加密](docs/USAGE.md#使用-keystore-加密) - 设备绑定，最安全
> - [组合使用签名和加密](docs/USAGE.md#组合使用签名和加密) - 最高安全级别
> - [安全最佳实践](docs/USAGE.md#安全最佳实践) - 生产环境配置建议

### 防篡改保护

系统自动提供补丁完整性验证和自动恢复功能：

- ✅ **SHA-256 哈希验证**：应用补丁时计算并保存哈希值
- ✅ **启动时验证**：每次应用启动时验证补丁完整性
- ✅ **自动恢复**：从加密存储中自动恢复被篡改的补丁
- ✅ **篡改计数**：最多允许 3 次篡改尝试，超过后自动清除

**无需额外配置**，防篡改功能已自动集成到 `PatchApplication` 和 `HotUpdateHelper` 中。

> 📖 **详细说明**：[防篡改保护文档](docs/SECURITY.md)

## 🎯 Demo 应用

**下载 Demo APK：** https://github.com/706412584/Android_hotupdate/releases/tag/demo

或者自己编译：

```bash
./gradlew :app:installDebug
```

**Demo 功能：**
1. 📱 设备端生成补丁
2. 🔒 配置安全策略（签名验证、加密）
3. 🔐 支持多种加密方式（KeyStore、自定义密码、ZIP 密码）
4. 🛡️ 自动防篡改保护
5. 🔄 补丁回滚功能

## 🔄 补丁回滚

```java
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.clearPatch();
Toast.makeText(context, "补丁已清除，请重启应用", Toast.LENGTH_LONG).show();

// 自动重启应用
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
├── patch-cli/               # 命令行工具 - PC/服务器端（可独立下载）
├── patch-gradle-plugin/     # Gradle 插件 - 构建集成
├── patch-server/            # 🆕 补丁管理服务端 - Web 管理后台
├── update/                  # 热更新 SDK - 补丁应用
└── app/                     # Demo 应用
```

| 模块 | 说明 | 文档 |
|------|------|------|
| **patch-generator-android** | Android SDK，设备端补丁生成 | [README](patch-generator-android/README.md) |
| **update** | 热更新 SDK，补丁应用和加载 | - |
| **patch-core** | 核心引擎，APK 解析、差异比较 | [README](patch-core/README.md) |
| **patch-native** | Native SO 库，BsDiff 算法 | [README](patch-native/README.md) |
| **patch-cli** | 命令行工具，独立运行，[可直接下载](https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.3/patch-cli-1.3.3-all.jar) | [README](patch-cli/README.md) |
| **patch-gradle-plugin** | Gradle 插件，构建集成 | [README](patch-gradle-plugin/README.md) |
| **patch-server** | 🆕 补丁管理服务端，Web 管理后台 + RESTful API | [README](patch-server/README.md) |

## 🌐 补丁管理服务端（新增）

提供完整的补丁管理 Web 后台和 RESTful API，支持补丁上传、版本管理、灰度发布、统计分析等功能。

### 核心功能

- 📦 **补丁管理** - 上传、版本控制、状态管理、批量操作
- 🎯 **灰度发布** - 百分比控制、设备 ID 哈希、强制更新
- 📊 **统计分析** - 仪表板、下载趋势、版本分布、设备分布
- 👥 **用户管理** - 多用户支持、权限分级、应用审核
- 🔔 **通知系统** - 站内通知、实时更新、消息管理
- 🔍 **全局搜索** - 搜索应用、补丁、用户，关键词高亮
- ⚙️ **系统管理** - 定时任务、数据备份、操作日志

### 技术栈

- **后端**: Node.js + Express + SQLite/MySQL
- **前端**: Vue 3 + Element Plus + ECharts
- **认证**: JWT
- **文件处理**: Multer

### 快速启动

```bash
# 后端
cd patch-server/backend
npm install
npm run dev  # http://localhost:3000

# 前端
cd patch-server/frontend
npm install
npm run dev  # http://localhost:5173
```

**默认管理员账号**: admin / admin123
### 🌐 在线服务

我们提供了免费的补丁托管服务供测试和学习使用：

- **服务地址**: https://android-hotupdateserver.zeabur.app
- **管理后台**: https://android-hotupdateserver.zeabur.app/dashboard

**功能特性**:
- ✅ 应用管理 - 创建和管理多个应用
- ✅ 补丁上传 - 支持手动上传或自动生成补丁
- ✅ 版本控制 - 管理不同版本的补丁
- ✅ 更新检查 - 提供 RESTful API 供客户端检查更新
- ✅ 下载统计 - 查看补丁下载和应用情况

**服务端界面预览**:

![服务端管理后台](docs/server-dashboard.png)

**使用说明**:
1. 在 Demo App 中点击"🌐 服务端测试"按钮
2. 使用默认账号登录测试各项 API 功能
3. 参考 [服务端 API 文档](patch-server/README.md) 集成到您的应用
4. 查看 [客户端集成示例](app/src/main/java/com/orange/update/ServerTestActivity.java) 了解如何调用 API

> ⚠️ **注意**: 此服务仅供测试和学习使用，有存储和流量限制，不建议在生产环境使用。生产环境请参考 [部署指南](patch-server/README.md#部署) 自行部署。
### 客户端集成

```java
// 检查更新
UpdateManager updateManager = new UpdateManager(context, "http://your-server.com");
updateManager.checkUpdate("your-app-id", "1.0.0", new UpdateCallback() {
    @Override
    public void onUpdateAvailable(PatchInfo patchInfo) {
        // 有新补丁可用
        updateManager.downloadAndApply(patchInfo, callback);
    }
    
    @Override
    public void onNoUpdate() {
        // 已是最新版本
    }
});
```

> 📖 **详细文档**: [patch-server/README.md](patch-server/README.md)

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
**A:** 不可以，这是 Android 机制的限制，需要重新安装 APK

### Q: 资源更新为什么需要重启？
**A:** 资源需要重新加载到 AssetManager，需要重启 Activity 才能看到新界面

### Q: 如何回滚补丁？
**A:** 调用 `helper.clearPatch()` 然后重启应用

### Q: 如何启用签名验证？
**A:** 使用 `helper.setRequireSignature(true)` 启用签名验证，补丁生成时需要使用与 APK 相同的签名密钥。详见[安全机制](#-安全机制)章节

### Q: 调试模式下可以跳过签名验证吗？
**A:** 可以，使用 `helper.setRequireSignature(false)` 关闭签名验证，方便开发测试。生产环境建议开启

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
Copyright 2026 Orange Update

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
