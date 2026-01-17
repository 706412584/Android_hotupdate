# 🔥 开源了！一套完整的 Android 热更新解决方案

## 前言

大家好！今天给大家分享一个我开源的 Android 热更新框架，支持 **DEX、资源、SO 库、Assets** 的完整热更新，无需重新安装 APK，代码立即生效！

**GitHub 地址：** https://github.com/706412584/Android_hotupdate

**JitPack 地址：** https://jitpack.io/#706412584/Android_hotupdate

**Demo 下载：** https://github.com/706412584/Android_hotupdate/releases/tag/demo

如果觉得有用，欢迎 Star ⭐ 支持一下！

---

## 💡 为什么要做这个项目？

在 Android 开发中，我们经常遇到这些痛点：

1. **线上 Bug 修复慢** - 发现 Bug 后需要重新打包、发布、等用户更新
2. **用户更新率低** - 很多用户不愿意下载新版本
3. **紧急修复成本高** - 半夜发现严重 Bug，需要紧急发版
4. **资源更新困难** - 修改一张图片都要发新版本

市面上虽然有 Tinker、Robust 等方案，但它们要么配置复杂，要么功能不全。于是我决定自己做一个**简单易用、功能完整**的热更新框架。

---

## ✨ 核心特性

### 1. 🔥 真正的热更新
- **DEX 热更新**：代码修改立即生效，无需重启
- **资源热更新**：图片、布局、字符串等资源实时更新
- **SO 库热更新**：Native 库也能热更新
- **Assets 热更新**：配置文件、数据文件随时更新

### 2. 🚀 高性能
- 使用 **Native C/C++ 引擎**，补丁生成速度提升 2-3 倍
- 基于 **BsDiff 算法**，补丁包体积小
- 自动降级机制，Native 不可用时使用 Java 引擎

### 3. 📱 设备端生成
- 支持在 **Android 设备上直接生成补丁**
- 无需 PC 或服务器，手机就能完成
- 适合测试、演示、紧急修复场景

### 4. 🛠️ 多种使用方式
- **Android SDK** - 集成到应用中
- **命令行工具** - 服务器端批量生成
- **Gradle 插件** - 构建时自动生成
- **Demo 应用** - 可视化操作界面

### 5. 🔒 安全可靠
- 支持签名验证，防止补丁被篡改
- MD5 校验，确保补丁完整性
- 回滚机制，出问题可快速恢复

### 6. 🎯 兼容性好
- 支持 **Android 5.0+** (API 21+)
- 兼容主流机型和 ROM
- 部分支持加固 APK（360、腾讯乐固等）

---

## 🚀 快速开始

### 第一步：添加依赖

在 `settings.gradle` 中添加 JitPack 仓库：

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

在 `build.gradle` 中添加依赖：

```groovy
dependencies {
    // 补丁生成 SDK
    implementation 'com.github.706412584.Android_hotupdate:patch-generator-android:1.2.4'
    
    // 热更新 SDK
    implementation 'com.github.706412584.Android_hotupdate:update:1.2.4'
}
```

### 第二步：生成补丁

```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)      // 旧版本 APK
    .newApk(newApkFile)        // 新版本 APK
    .output(patchFile)         // 输出补丁文件
    .callback(new SimpleAndroidGeneratorCallback() {
        @Override
        public void onComplete(PatchResult result) {
            if (result.isSuccess()) {
                Log.i(TAG, "补丁生成成功！");
                Log.i(TAG, "补丁大小: " + result.getPatchSize() + " bytes");
            }
        }
        
        @Override
        public void onError(String message) {
            Log.e(TAG, "生成失败: " + message);
        }
    })
    .build();

// 后台生成（推荐）
generator.generateInBackground();
```

### 第三步：应用补丁

```java
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(patchFile, new RealHotUpdate.ApplyCallback() {
    @Override
    public void onSuccess(RealHotUpdate.PatchResult result) {
        Log.i(TAG, "热更新成功！");
        Toast.makeText(context, "更新成功，代码已生效", Toast.LENGTH_SHORT).show();
        
        // DEX 和 SO 立即生效，无需重启
        // 资源更新需要重启 Activity
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "热更新失败: " + message);
        Toast.makeText(context, "更新失败: " + message, Toast.LENGTH_SHORT).show();
    }
});
```

### 第四步：在 Application 中加载补丁

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

就这么简单！**4 步完成热更新集成**。

---

## 📱 体验 Demo

想快速体验热更新效果？直接下载 Demo APK：

**👉 [点击下载 Demo APK](https://github.com/706412584/Android_hotupdate/releases/tag/demo)**

Demo 功能：
- ✅ 可视化生成补丁
- ✅ 一键应用热更新
- ✅ 实时查看更新效果
- ✅ 支持补丁回滚
- ✅ 完整的使用示例

---

## 📊 实际效果演示

### 场景一：修复线上 Bug

**问题：** 线上发现一个计算错误的 Bug

```java
// 旧代码（有 Bug）
public int calculate(int a, int b) {
    return a + b;  // 应该是 a * b
}
```

**修复步骤：**
1. 修改代码：`return a * b;`
2. 打包新 APK
3. 生成补丁（只需几秒）
4. 推送补丁到用户设备
5. 用户应用补丁，**立即生效**，无需重启！

**补丁大小：** 通常只有几 KB 到几十 KB

### 场景二：更新资源文件

**需求：** 修改应用图标和启动页

**步骤：**
1. 替换 `res/drawable/` 中的图片
2. 打包新 APK
3. 生成补丁
4. 用户应用补丁
5. 重启应用，新图标生效

**补丁大小：** 取决于图片大小，通常几百 KB

### 场景三：更新 Native 库

**需求：** 修复 SO 库中的崩溃问题

**步骤：**
1. 修改 C/C++ 代码
2. 重新编译 SO 库
3. 打包新 APK
4. 生成补丁
5. 用户应用补丁，**立即生效**

---

## 🔧 高级功能

### 1. 补丁回滚

如果补丁有问题，可以快速回滚：

```java
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.clearPatch();

// 重启应用
Intent intent = context.getPackageManager()
    .getLaunchIntentForPackage(context.getPackageName());
intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(intent);
android.os.Process.killProcess(android.os.Process.myPid());
```

### 2. 签名验证

确保补丁来源可信：

```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .enableSignatureVerification(true)  // 启用签名验证
    .build();
```

### 3. 进度监听

实时显示生成进度：

```java
generator.setCallback(new AndroidGeneratorCallback() {
    @Override
    public void onProgress(int progress, String message) {
        progressBar.setProgress(progress);
        statusText.setText(message);
    }
    
    @Override
    public void onComplete(PatchResult result) {
        // 生成完成
    }
});
```

---

## 📁 项目架构

```
Android_hotupdate/
├── patch-core/              # 核心库 - 补丁生成引擎
├── patch-native/            # Native 库 - C/C++ 高性能引擎
├── patch-generator-android/ # Android SDK - 设备端生成
├── patch-cli/               # 命令行工具 - PC/服务器端
├── patch-gradle-plugin/     # Gradle 插件 - 构建集成
├── update/                  # 热更新 SDK - 补丁应用
└── app/                     # Demo 应用
```

**模块说明：**

| 模块 | 功能 | 使用场景 |
|------|------|----------|
| **patch-generator-android** | 设备端补丁生成 | 集成到应用中，手机上生成补丁 |
| **update** | 补丁应用和加载 | 集成到应用中，应用热更新 |
| **patch-core** | 核心引擎 | 被其他模块依赖 |
| **patch-native** | Native 加速 | 提升性能 |
| **patch-cli** | 命令行工具 | 服务器端批量生成 |
| **patch-gradle-plugin** | Gradle 插件 | 构建时自动生成 |

---

## 💡 技术原理

### DEX 热更新原理

Android 的类加载机制基于 `ClassLoader`，其中 `PathClassLoader` 负责加载 DEX 文件。通过反射修改 `dexElements` 数组，可以插入新的 DEX 文件：

```java
// 简化版原理代码
Object dexPathList = ReflectUtil.getField(classLoader, "pathList");
Object[] oldDexElements = (Object[]) ReflectUtil.getField(dexPathList, "dexElements");
Object[] newDexElements = makeDexElements(patchDexFiles);

// 合并数组，新 DEX 在前
Object[] combinedElements = combineArrays(newDexElements, oldDexElements);
ReflectUtil.setField(dexPathList, "dexElements", combinedElements);
```

### 资源热更新原理

Android 的资源加载由 `AssetManager` 管理。通过创建新的 `AssetManager` 并添加补丁资源路径：

```java
AssetManager newAssetManager = AssetManager.class.newInstance();
Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
addAssetPath.invoke(newAssetManager, patchResourcePath);

// 替换 Resources 中的 AssetManager
ReflectUtil.setField(resources, "mAssets", newAssetManager);
```

### SO 库热更新原理

类似 DEX 热更新，通过修改 `nativeLibraryPathElements`：

```java
Object nativeLibraryPathList = ReflectUtil.getField(classLoader, "pathList");
Object[] oldElements = (Object[]) ReflectUtil.getField(nativeLibraryPathList, "nativeLibraryPathElements");
Object[] newElements = makeNativeLibraryElements(patchSoFiles);

Object[] combinedElements = combineArrays(newElements, oldElements);
ReflectUtil.setField(nativeLibraryPathList, "nativeLibraryPathElements", combinedElements);
```

---

## 🎯 适用场景

### ✅ 适合使用热更新的场景

1. **紧急 Bug 修复** - 线上发现严重 Bug，需要快速修复
2. **小功能迭代** - 修改业务逻辑、UI 样式等
3. **资源更新** - 更换图片、文案、配置文件
4. **A/B 测试** - 灰度发布新功能
5. **运营活动** - 快速上线活动页面

### ❌ 不适合使用热更新的场景

1. **四大组件变更** - 新增 Activity、Service 等（需要修改 Manifest）
2. **权限变更** - 新增或删除权限
3. **大版本升级** - 架构调整、重构等
4. **初次安装** - 用户首次安装应用

---

## 📊 性能对比

| 对比项 | 传统发版 | 热更新 |
|--------|----------|--------|
| **修复时间** | 1-3 天 | 几分钟 |
| **用户更新率** | 30-50% | 90%+ |
| **下载大小** | 10-50 MB | 几 KB - 几 MB |
| **生效时间** | 需要用户手动更新 | 自动更新，立即生效 |
| **回滚成本** | 需要重新发版 | 一键回滚 |

---

## ❓ 常见问题

### Q1: 支持哪些 Android 版本？
**A:** 支持 Android 5.0+ (API 21+)，推荐 Android 7.0+ (API 24+)

### Q2: 可以热更新 AndroidManifest.xml 吗？
**A:** 不可以，这是 Android 系统限制，需要重新安装 APK

### Q3: 资源更新为什么需要重启？
**A:** 资源需要重新加载到 AssetManager，需要重启 Activity 才能看到新界面

### Q4: 补丁包有多大？
**A:** 取决于修改内容：
- 纯代码修改：几 KB 到几十 KB
- 资源修改：几百 KB 到几 MB
- SO 库修改：几百 KB 到几 MB

### Q5: 支持加固的 APK 吗？
**A:** 部分支持，建议在加固前生成补丁，加固后充分测试

### Q6: 如何保证补丁安全？
**A:** 
- 启用签名验证
- 使用 HTTPS 传输补丁
- 服务端控制补丁下发
- MD5 校验补丁完整性

---

## 🔮 未来规划

- [ ] 支持增量更新（只下载变化部分）
- [ ] 支持多补丁管理
- [ ] 提供云端补丁管理平台
- [ ] 支持更多加固方案
- [ ] 优化补丁生成速度
- [ ] 提供可视化管理后台

---

## 🤝 参与贡献

欢迎大家参与贡献！无论是：

- 🐛 报告 Bug
- 💡 提出新功能建议
- 📝 改进文档
- 🔧 提交代码

都非常欢迎！

**贡献步骤：**
1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📞 联系方式

- **GitHub**: https://github.com/706412584/Android_hotupdate
- **Issues**: https://github.com/706412584/Android_hotupdate/issues
- **Email**: 706412584@qq.com

---

## 🙏 致谢

本项目参考了以下优秀的开源项目：
- [Tinker](https://github.com/Tencent/tinker) - 腾讯的 Android 热修复方案
- [Robust](https://github.com/Meituan-Dianping/Robust) - 美团的热修复方案

感谢这些优秀的开源项目为我提供了灵感和参考！

---

## 📄 开源协议

本项目采用 **Apache License 2.0** 开源协议。

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

---

## 🌟 最后

如果这个项目对你有帮助，请给个 **Star ⭐** 支持一下！

你的 Star 是我持续更新的动力！

**GitHub 地址：** https://github.com/706412584/Android_hotupdate

**JitPack 地址：** https://jitpack.io/#706412584/Android_hotupdate

---

**关键词：** Android热更新、热修复、补丁、DEX、资源更新、SO库、BsDiff、Tinker、开源框架

**标签：** #Android #热更新 #热修复 #开源 #补丁 #DEX #资源更新 #Native
