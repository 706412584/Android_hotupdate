# Android 热更新补丁工具

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)

一套完整的 Android 热更新解决方案，支持 **DEX、资源、SO 库、Assets** 的热更新，无需重新安装 APK。

## ✨ 核心特性

- 🔥 **真正的热更新** - 无需重启应用，代码立即生效
- 📦 **完整支持** - DEX、资源、SO 库、Assets 全面支持
- 🚀 **高性能** - Native 引擎加速，补丁生成快速
- 📱 **设备端生成** - 支持在 Android 设备上直接生成补丁
- 🛠️ **多种方式** - 命令行、Gradle 插件、Android SDK
- 🔒 **安全可靠** - 支持签名验证，防止篡改
- 📊 **详细统计** - 提供差异分析和补丁信息
- 🎯 **兼容性好** - 支持 Android 5.0+ (API 21+)

## 📋 目录

- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [详细使用说明](#详细使用说明)
- [Demo 应用使用](#demo-应用使用)
- [补丁包格式](#补丁包格式)
- [常见问题](#常见问题)
- [系统要求](#系统要求)
- [许可证](#许可证)

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


### 模块说明

| 模块 | 说明 | 文档 |
|------|------|------|
| **patch-core** | 核心引擎，提供 APK 解析、差异比较、打包签名 | [README](patch-core/README.md) |
| **patch-native** | Native SO 库，BsDiff 算法和快速哈希 | [README](patch-native/README.md) |
| **patch-generator-android** | Android SDK，设备端补丁生成 | [README](patch-generator-android/README.md) |
| **patch-cli** | 命令行工具，独立运行 | [README](patch-cli/README.md) |
| **patch-gradle-plugin** | Gradle 插件，构建集成 | [README](patch-gradle-plugin/README.md) |
| **update** | 热更新 SDK，补丁应用和加载 | - |
| **app** | Demo 应用，演示完整流程 | - |

## 🚀 快速开始

### 方式一：使用 Demo 应用（推荐新手）

1. **安装 Demo 应用**
   ```bash
   # 编译并安装
   ./gradlew :app:installDebug
   
   # 或直接安装测试 APK
   adb install test-apks/app-v1.0-dex-res.apk
   ```

2. **生成补丁**
   - 打开应用
   - 点击「选择基准 APK」选择旧版本
   - 点击「选择新 APK」选择新版本
   - 点击「生成补丁」
   - 补丁将保存到 `/sdcard/Download/` 目录

3. **应用补丁**
   - 点击「应用补丁」
   - 热更新立即生效，无需重启

### 方式二：命令行生成补丁

```bash
# 编译命令行工具
./gradlew :patch-cli:build

# 生成补丁
java -jar patch-cli/build/libs/patch-cli.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip
```


### 方式三：Gradle 插件集成

```groovy
// 项目根目录 build.gradle
buildscript {
    dependencies {
        classpath 'com.orange.patch:patch-gradle-plugin:1.0.0'
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

```bash
# 生成补丁
./gradlew generateReleasePatch
```

### 方式四：Android SDK 集成

```java
// 1. 添加依赖
dependencies {
    implementation project(':patch-generator-android')
    implementation project(':update')
}

// 2. 生成补丁
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .callback(new SimpleAndroidGeneratorCallback() {
        @Override
        public void onComplete(PatchResult result) {
            if (result.isSuccess()) {
                Log.i(TAG, "补丁生成成功: " + result.getPatchFile());
            }
        }
    })
    .build();

generator.generateInBackground();

// 3. 应用补丁
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(patchFile, new RealHotUpdate.ApplyCallback() {
    @Override
    public void onSuccess(RealHotUpdate.PatchResult result) {
        Log.i(TAG, "热更新成功!");
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "热更新失败: " + message);
    }
});
```


## 📖 详细使用说明

### 1. 补丁生成流程

#### 1.1 准备 APK 文件

需要准备两个 APK 文件：
- **基准 APK (旧版本)** - 当前线上运行的版本
- **新 APK (新版本)** - 包含修复或新功能的版本

**注意事项：**
- 两个 APK 必须是同一个应用（包名相同）
- 建议使用 Release 版本的 APK
- 确保 APK 文件完整且未损坏

#### 1.2 生成补丁

**使用 Demo 应用：**
1. 打开应用，点击「选择基准 APK」
2. 从文件管理器选择旧版本 APK
3. 点击「选择新 APK」
4. 从文件管理器选择新版本 APK
5. 点击「生成补丁」按钮
6. 等待生成完成（显示进度）
7. 补丁文件保存在 `/sdcard/Download/patch_xxxxx.zip`

**使用命令行：**
```bash
java -jar patch-cli.jar \
  --base /path/to/app-v1.0.apk \
  --new /path/to/app-v1.1.apk \
  --output /path/to/patch.zip
```

#### 1.3 补丁内容

补丁包会自动包含以下变更：
- ✅ **DEX 文件** - 修改、新增、删除的类
- ✅ **资源文件** - 修改的布局、图片、字符串等
- ✅ **SO 库** - 修改的 Native 库
- ✅ **Assets 文件** - 修改的 Assets 资源
- ✅ **元数据** - 版本信息、变更统计

### 2. 补丁应用流程

#### 2.1 应用补丁

**使用 Demo 应用：**
1. 确保已生成补丁或选择了补丁文件
2. 点击「应用补丁」按钮
3. 等待应用完成
4. 热更新立即生效（DEX 和 SO）
5. 资源更新需要重启应用

**使用代码：**
```java
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(patchFile, new RealHotUpdate.ApplyCallback() {
    @Override
    public void onProgress(int percent, String message) {
        // 更新进度
    }
    
    @Override
    public void onSuccess(RealHotUpdate.PatchResult result) {
        // 热更新成功
        if (result.needsRestart) {
            // 提示用户重启应用（仅资源更新需要）
        }
    }
    
    @Override
    public void onError(String message) {
        // 处理错误
    }
});
```


#### 2.2 热更新原理

**DEX 热更新：**
- 通过反射修改 `ClassLoader` 的 `dexElements` 数组
- 将补丁 DEX 插入到数组最前面
- 类加载时优先从补丁 DEX 查找
- **立即生效，无需重启**

**资源热更新：**
- 创建新的 `AssetManager` 并加载补丁资源
- 替换所有 `Resources` 对象的 `AssetManager`
- 清空 `ResourcesManager` 缓存
- **需要重启 Activity 才能看到新界面**

**SO 库热更新：**
- 提取补丁中的 SO 文件到应用目录
- 通过反射修改 `ClassLoader` 的 `nativeLibraryPathElements`
- 将补丁 SO 路径插入到最前面
- **立即生效，无需重启**

**Assets 热更新：**
- Assets 文件作为资源的一部分
- 通过 `AssetManager` 加载
- 随资源热更新一起生效

#### 2.3 回滚补丁

如果需要回滚到原始版本：

**使用 Demo 应用：**
1. 点击「清除补丁」按钮
2. 确认清除操作
3. 重启应用完全回滚

**使用代码：**

**方式一：简单回滚（推荐）**
```java
// 清除补丁
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.clearPatch();

// 提示用户重启应用
Toast.makeText(context, "补丁已清除，请重启应用", Toast.LENGTH_LONG).show();
```

**方式二：清除并自动重启**
```java
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.clearPatch();

// 重启应用
Intent intent = context.getPackageManager()
    .getLaunchIntentForPackage(context.getPackageName());
if (intent != null) {
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    
    // 结束当前进程
    if (context instanceof Activity) {
        ((Activity) context).finish();
    }
    android.os.Process.killProcess(android.os.Process.myPid());
}
```

**方式三：带确认对话框的回滚**
```java
new AlertDialog.Builder(context)
    .setTitle("清除补丁")
    .setMessage("确定要清除已应用的补丁吗？\n\n注意：清除后需要重启应用才能完全回滚到原版本。")
    .setPositiveButton("确定", (dialog, which) -> {
        RealHotUpdate hotUpdate = new RealHotUpdate(context);
        hotUpdate.clearPatch();
        
        // 询问是否立即重启
        new AlertDialog.Builder(context)
            .setTitle("重启应用")
            .setMessage("补丁已清除，是否立即重启应用？")
            .setPositiveButton("立即重启", (d, w) -> {
                Intent intent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    if (context instanceof Activity) {
                        ((Activity) context).finish();
                    }
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            })
            .setNegativeButton("稍后重启", null)
            .show();
    })
    .setNegativeButton("取消", null)
    .show();
```

**clearPatch() 方法说明：**
- 清除 DEX 目录 (`hotupdate/dex/`)
- 清除合并的资源目录 (`hotupdate/merged/`)
- 清除补丁目录 (`hotupdate/patches/`)
- 清除补丁状态信息
- **不会删除原始 APK，只删除补丁文件**

### 3. 在 Application 中集成

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
}
```

**AndroidManifest.xml：**
```xml
<application
    android:name=".MyApplication"
    ...>
</application>
```


## 📱 Demo 应用使用

### 界面说明

Demo 应用提供了完整的热更新演示功能：

#### 主界面功能

1. **标题卡片**
   - 显示应用版本
   - 显示当前状态
   - 显示进度条

2. **文件选择卡片**
   - 「选择基准 APK」- 选择旧版本 APK
   - 「选择新 APK」- 选择新版本 APK

3. **补丁操作卡片**
   - 「生成补丁」- 生成补丁文件
   - 「应用补丁」- 应用热更新
   - 「检查引擎」- 查看 Native 引擎状态
   - 「检查存储」- 查看存储空间
   - 「选择补丁」- 选择已有补丁文件
   - 「清除补丁」- 回滚到原版本
   - 「测试 Assets」- 测试 Assets 文件读取

4. **信息显示卡片**
   - 显示系统信息
   - 显示补丁生成结果
   - 显示热更新结果

### 测试流程

#### 测试 DEX 和资源热更新

1. **安装基准版本**
   ```bash
   adb install test-apks/app-v1.0-dex-res.apk
   ```
   - 标题：「热更新补丁工具」（无火焰）
   - 测试信息：「热更新测试 v1.0 - 原始版本」

2. **生成补丁**
   - 打开应用
   - 选择 `test-apks/app-v1.0-dex-res.apk` 作为基准
   - 选择 `test-apks/app-v1.2-dex-res.apk` 作为新版本
   - 点击「生成补丁」
   - 等待生成完成

3. **应用补丁**
   - 点击「应用补丁」
   - 查看热更新结果
   - **DEX 立即生效** - 测试信息变为「🔥 热更新测试 v1.2 - 补丁已生效！代码已更新！」
   - **资源需要重启** - 重启后标题变为「🔥 热更新补丁工具 1.2」

#### 测试 Assets 热更新

1. **安装基准版本**
   ```bash
   adb install test-apks/app-v1.0-assets.apk
   ```

2. **测试原始 Assets**
   - 点击「测试 Assets 文件」
   - 显示：「配置文件版本: 1.0」

3. **生成并应用补丁**
   - 选择 `app-v1.0-assets.apk` 和 `app-v1.2-assets.apk`
   - 生成并应用补丁
   - 重启应用

4. **验证更新**
   - 点击「测试 Assets 文件」
   - 显示：「🔥 配置文件版本: 1.2 - 热更新成功！」


### 输出目录

所有生成的补丁文件默认保存在：
```
/sdcard/Download/patch_<timestamp>.zip
```

可以通过以下方式访问：
```bash
# 查看补丁文件
adb shell ls -lh /sdcard/Download/patch_*.zip

# 拉取到电脑
adb pull /sdcard/Download/patch_xxxxx.zip ./
```

## 📦 补丁包格式

### 目录结构

```
patch.zip
├── patch.json              # 补丁元数据
├── classes.dex             # 修改的 DEX 文件
├── resources.arsc          # 完整的资源索引（STORED 格式）
├── res/                    # 修改的资源文件
│   ├── layout/
│   │   └── activity_main.xml
│   ├── drawable/
│   │   └── icon.png
│   └── values/
│       └── strings.xml
├── assets/                 # 修改的 Assets 文件
│   └── config.txt
└── lib/                    # 修改的 SO 库
    ├── armeabi-v7a/
    │   └── libxxx.so
    ├── arm64-v8a/
    │   └── libxxx.so
    ├── x86/
    │   └── libxxx.so
    └── x86_64/
        └── libxxx.so
```

### patch.json 格式

```json
{
    "patchId": "patch_20260117_001",
    "patchVersion": "1.0.1",
    "baseVersion": "1.0.0",
    "targetVersion": "1.0.1",
    "baseVersionCode": 1,
    "targetVersionCode": 2,
    "md5": "d41d8cd98f00b204e9800998ecf8427e",
    "createTime": 1705420800000,
    "patchSize": 1024000,
    "changes": {
        "dex": {
            "modified": ["com.example.MainActivity", "com.example.Utils"],
            "added": ["com.example.NewFeature"],
            "deleted": ["com.example.OldFeature"]
        },
        "resources": {
            "modified": ["res/layout/activity_main.xml", "res/values/strings.xml"],
            "added": ["res/drawable/new_icon.png"],
            "deleted": []
        },
        "assets": {
            "modified": ["assets/config.txt"],
            "added": [],
            "deleted": []
        },
        "so": {
            "modified": ["lib/arm64-v8a/libxxx.so"],
            "added": [],
            "deleted": []
        }
    }
}
```

### 重要说明

1. **resources.arsc 必须使用 STORED 格式**
   - 不能压缩（DEFLATED）
   - 必须以 STORED 方式存储
   - 否则 AssetManager 无法加载

2. **补丁包是完整的资源包**
   - 包含所有资源文件（不仅是变更的）
   - 包含完整的 resources.arsc
   - 这是 Tinker 的方式，确保兼容性


## ❓ 常见问题

### 1. 补丁生成相关

#### Q: 生成补丁时提示"权限被拒绝"？
**A:** 需要授予存储权限：
- Android 11+ (API 30+): 需要「所有文件访问权限」
- Android 10 及以下: 需要「存储权限」
- 在应用设置中手动授予权限

#### Q: 生成补丁失败，提示"APK 解析失败"？
**A:** 检查以下几点：
1. APK 文件是否完整（未损坏）
2. APK 文件是否可读
3. 两个 APK 的包名是否相同
4. 存储空间是否充足

#### Q: 生成的补丁文件很大？
**A:** 这是正常的，因为：
- 补丁包含完整的资源文件（Tinker 方式）
- 如果修改了大量资源，补丁会较大
- DEX 文件使用差分算法，通常较小
- 可以通过减少资源变更来减小补丁大小

#### Q: 生成补丁时出现 .tmp 文件？
**A:** 这是临时文件，正常情况下会自动删除：
- 如果生成失败，可能残留 .tmp 文件
- 可以手动删除这些文件
- 不影响正常使用

### 2. 补丁应用相关

#### Q: 应用补丁后代码没有生效？
**A:** 检查以下几点：
1. 确认补丁应用成功（查看日志）
2. 确认修改的类已经被加载到补丁 DEX
3. 某些类可能被 ART 提前编译，需要重启应用
4. 查看「系统信息」确认 DEX 注入状态

#### Q: 应用补丁后资源没有更新？
**A:** 资源更新需要重启应用：
- DEX 和 SO 立即生效
- 资源需要重启 Activity 或应用
- 应用补丁后会提示是否重启

#### Q: 应用补丁后闪退？
**A:** 可能的原因：
1. 补丁与当前版本不匹配
2. 补丁文件损坏
3. 资源文件格式错误（resources.arsc 必须是 STORED）
4. 点击「清除补丁」回滚，然后重新生成

#### Q: 如何回滚补丁？
**A:** 两种方式：
1. 使用「清除补丁」按钮，然后重启应用
2. 重新安装原始 APK

### 3. 兼容性相关

#### Q: 支持哪些 Android 版本？
**A:** 
- **最低版本**: Android 5.0 (API 21)
- **最高版本**: Android 14 (API 34)
- **推荐版本**: Android 7.0+ (API 24+)

**为什么不支持 Android 4.x？**
- DEX 注入需要 `BaseDexClassLoader` 内部字段（API 21+）
- 资源加载需要特定的 `AssetManager` API（API 21+）
- SO 加载需要 ClassLoader 路径修改（API 21+）


#### Q: 在不同 Android 版本上有什么区别？
**A:**
- **Android 5.0-6.0 (API 21-23)**: 标准方案，完全支持
- **Android 7.0-7.1 (API 24-25)**: 需要处理混合编译模式
- **Android 8.0-9.0 (API 26-28)**: 需要处理 ResourcesImpl
- **Android 10+ (API 29+)**: 需要处理非 SDK 接口限制，部分功能受限

#### Q: 支持哪些 ABI？
**A:** 支持所有主流 ABI：
- armeabi-v7a (32位 ARM)
- arm64-v8a (64位 ARM)
- x86 (32位 x86)
- x86_64 (64位 x86)

#### Q: 在 MIUI 等定制 ROM 上能用吗？
**A:** 可以，已针对定制 ROM 做了优化：
- 处理了 MIUI 的 `MiuiResourcesImpl`
- 清除了 TypedArray 缓存
- 兼容各种定制系统

### 4. 性能相关

#### Q: Native 引擎和 Java 引擎有什么区别？
**A:**
- **Native 引擎**: 使用 C/C++ 实现，性能更高，速度快 2-3 倍
- **Java 引擎**: 纯 Java 实现，兼容性好，功能完整
- 优先使用 Native 引擎，不可用时自动降级到 Java 引擎

#### Q: 生成补丁需要多长时间？
**A:** 取决于 APK 大小和变更量：
- 小型应用 (< 10MB): 5-15 秒
- 中型应用 (10-50MB): 15-60 秒
- 大型应用 (> 50MB): 1-3 分钟
- Native 引擎可以显著加快速度

#### Q: 补丁应用需要多长时间？
**A:** 通常很快：
- DEX 注入: < 1 秒
- 资源加载: 1-3 秒
- SO 加载: < 1 秒
- 总计: 2-5 秒

### 5. 安全相关

#### Q: 补丁包是否安全？
**A:** 
- 支持签名验证（可选）
- 建议在生产环境启用签名验证
- 补丁包应通过 HTTPS 下发
- 可以添加自定义校验逻辑

#### Q: 如何防止补丁被篡改？
**A:**
1. 启用签名验证
2. 使用 HTTPS 传输
3. 验证补丁 MD5
4. 添加服务端校验

#### Q: 热更新会影响应用稳定性吗？
**A:**
- 正确使用不会影响稳定性
- 建议充分测试补丁
- 提供回滚机制
- 监控补丁应用成功率


### 6. 开发相关

#### Q: 如何在项目中集成？
**A:** 参考「快速开始」章节，主要步骤：
1. 添加依赖模块
2. 在 Application 中初始化
3. 实现补丁下载和应用逻辑
4. 添加错误处理和回滚机制

#### Q: 可以热更新 AndroidManifest.xml 吗？
**A:** 不可以，这是 Tinker 的限制：
- AndroidManifest.xml 在应用安装时解析
- 无法通过热更新修改
- 需要重新安装 APK

#### Q: 可以热更新四大组件吗？
**A:**
- **Activity/Service/Receiver**: 可以修改已有组件的代码
- **新增组件**: 不可以，需要在 Manifest 中声明
- **删除组件**: 不可以

#### Q: 如何调试热更新？
**A:**
1. 查看 Logcat 日志（TAG: DexPatcher, ResourcePatcher, SoPatcher）
2. 使用 Demo 应用的「系统信息」查看状态
3. 使用 `printDexElements()` 打印 DEX 信息
4. 检查补丁文件是否正确生成

#### Q: 如何测试热更新？
**A:**
1. 使用提供的测试 APK（test-apks 目录）
2. 修改代码或资源后生成新 APK
3. 生成补丁并应用
4. 验证修改是否生效
5. 测试回滚功能

### 7. 其他问题

#### Q: 补丁包可以增量更新吗？
**A:** 
- 当前版本是完整补丁包
- 未来可能支持增量补丁
- 建议服务端做差分下发优化

#### Q: 支持多个补丁叠加吗？
**A:**
- 当前不支持
- 每次只能应用一个补丁
- 新补丁会覆盖旧补丁

#### Q: 如何获取技术支持？
**A:**
1. 查看本文档的常见问题
2. 查看各模块的 README
3. 提交 GitHub Issue
4. 查看源码和注释

#### Q: 可以商用吗？
**A:**
- 采用 Apache License 2.0
- 可以免费商用
- 需要保留版权声明
- 欢迎贡献代码


## 🔧 编译和构建

### 编译所有模块

```bash
# 清理
./gradlew clean

# 编译所有模块
./gradlew build

# 编译并安装 Demo 应用
./gradlew :app:installDebug
```

### 编译特定模块

```bash
# 核心库
./gradlew :patch-core:build

# Native 库
./gradlew :patch-native:build

# Android SDK
./gradlew :patch-generator-android:build

# 命令行工具
./gradlew :patch-cli:build

# Gradle 插件
./gradlew :patch-gradle-plugin:build

# 热更新 SDK
./gradlew :update:build
```

### 运行测试

```bash
# 运行所有测试
./gradlew test

# 运行特定模块测试
./gradlew :patch-core:test
```

### 生成文档

```bash
# 生成 Javadoc
./gradlew javadoc
```

## 📋 系统要求

### 开发环境

- **Java**: JDK 11 或更高版本
- **Android SDK**: API 21+ (Android 5.0+)
- **Gradle**: 8.9 或更高版本
- **Android Gradle Plugin**: 8.0+ 
- **NDK**: 27.0+ (仅编译 Native 模块需要)

### 运行环境

- **最低版本**: Android 5.0 (API 21)
- **推荐版本**: Android 7.0+ (API 24+)
- **目标版本**: Android 14 (API 34)

### 存储要求

- **生成补丁**: 至少 100MB 可用空间
- **应用补丁**: 至少 50MB 可用空间
- **临时文件**: 根据 APK 大小，通常需要 2-3 倍空间

## 📚 相关文档

- [补丁生成核心库文档](patch-core/README.md)
- [Native 引擎文档](patch-native/README.md)
- [Android SDK 文档](patch-generator-android/README.md)
- [命令行工具文档](patch-cli/README.md)
- [Gradle 插件文档](patch-gradle-plugin/README.md)
- [SO 和 Assets 补丁支持文档](SO_ASSETS_PATCH_SUPPORT.md)

## 🤝 贡献

欢迎贡献代码、报告问题或提出建议！

### 如何贡献

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

### 代码规范

- 遵循 Java 代码规范
- 添加必要的注释
- 编写单元测试
- 更新相关文档


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
- [Sophix](https://help.aliyun.com/product/51340.html) - 阿里的热修复方案

感谢这些项目为 Android 热更新技术做出的贡献！

## 📞 联系方式

- **GitHub Issues**: [提交问题](https://github.com/706412584/Android_hotupdate/issues)
- **Email**: 706412584@qq.com

## 🔖 版本历史

### v1.2.0 (2024-01-17)
- ✨ 新增 SO 库热更新支持
- ✨ 新增 Assets 文件热更新支持
- 🐛 修复资源热更新在 Android 10+ 上的问题
- 🐛 修复 .tmp 文件残留问题
- 🐛 修复补丁清除功能
- 📝 完善文档和使用说明

### v1.1.0 (2024-01-16)
- ✨ 新增资源热更新支持
- ✨ 新增 DEX 热更新支持
- ✨ 新增 Demo 应用
- 📝 添加详细文档

### v1.0.0 (2024-01-15)
- 🎉 首次发布
- ✨ 补丁生成核心功能
- ✨ 命令行工具
- ✨ Android SDK

---

**⭐ 如果这个项目对你有帮助，请给个 Star！**
