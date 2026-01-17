# Android 热更新补丁工具

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![JitPack](https://jitpack.io/v/706412584/Android_hotupdate.svg)](https://jitpack.io/#706412584/Android_hotupdate)

一套完整的 Android 热更新解决方案，支持 **DEX、资源、SO 库、Assets** 的热更新，无需重新安装 APK。

## ✨ 核心特性

- 🔥 **真正的热更新** - 无需重启应用，代码立即生效
- 📦 **完整支持** - DEX、资源、SO 库、Assets 全面支持
- 🚀 **高性能** - Native 引擎加速，补丁生成快速
- 📱 **设备端生成** - 支持在 Android 设备上直接生成补丁
- 🛠️ **多种方式** - 命令行、Gradle 插件、Android SDK
- 🔒 **安全可靠** - 支持签名验证，防止篡改
- 🎯 **兼容性好** - 支持 Android 5.0+ (API 21+)

## 📚 文档导航

- **[快速开始](#-快速开始)** - 5 分钟上手
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
    implementation 'com.github.706412584.Android_hotupdate:patch-generator-android:1.2.0'
    
    // 热更新 SDK
    implementation 'com.github.706412584.Android_hotupdate:update:1.2.0'
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

### 方式二：使用 Demo 应用

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
