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

### 2. 回滚补丁

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
4. **信息显示卡片** - 显示系统信息和结果

### 测试流程

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
        classpath 'com.github.706412584.Android_hotupdate:patch-gradle-plugin:v1.2.4'
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
