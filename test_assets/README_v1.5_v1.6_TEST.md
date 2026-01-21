# v1.5 和 v1.6 测试版本说明

## 📦 测试文件

- `app-v1.5.apk` - 基准版本 (versionCode=4, versionName="1.5")
- `app-v1.6.apk` - 更新版本 (versionCode=5, versionName="1.6")

## 🔄 版本差异

两个版本之间的主要差异在 `SystemInfoFragment.java` 的 `getHotUpdateTestInfo()` 方法：

### v1.5 (基准版本)
```java
private String getHotUpdateTestInfo() {
    return "🔥 热更新测试 v1.5 - 这是基准版本！";
}
```

### v1.6 (更新版本)
```java
private String getHotUpdateTestInfo() {
    return "🔥 热更新测试 v1.6 - 这是更新版本！DEX 已变化！";
}
```

这个差异会导致：
- **DEX 文件变化** - 方法字符串常量不同
- **补丁包含 DEX 差异** - 可以测试 DEX 热更新功能

## 🧪 测试步骤

### 1. 安装基准版本
```bash
adb install test_assets/app-v1.5.apk
```

打开应用，在「系统信息」页面应该看到：
```
🔥 热更新测试 v1.5 - 这是基准版本！
```

### 2. 生成补丁
在应用中：
1. 进入「补丁生成」页面
2. 选择基准 APK: `/sdcard/Download/app-v1.5.apk`
3. 选择新版 APK: `/sdcard/Download/app-v1.6.apk`
4. 点击「生成补丁」

预期结果：
- ✅ 补丁生成成功
- ✅ 补丁大小 > 1KB（包含 DEX 差异）
- ✅ 补丁包含 `classes.dex` 文件

### 3. 应用补丁
1. 进入「补丁应用」页面
2. 选择刚生成的补丁文件
3. 点击「应用补丁」

预期结果：
- ✅ 补丁应用成功
- ✅ DEX 注入状态显示 ✓（如果设备支持）

### 4. 验证热更新
返回「系统信息」页面，应该看到：
```
🔥 热更新测试 v1.6 - 这是更新版本！DEX 已变化！
```

**无需重启应用**，内容已经通过热更新改变！

## ⚠️ 0KB 补丁检测

如果选择了两个相同的 APK 文件（或差异极小），会触发 0KB 补丁检测：

```
⚠️ 两个 APK 无差异，无需生成补丁

补丁大小: 0 bytes
请确保选择了不同版本的 APK 文件
```

检测逻辑：
- 补丁文件大小 < 1KB 时触发
- 自动删除无效的补丁文件
- 提示用户选择不同版本

## 📍 文件位置

### 本地测试资源
- `test_assets/app-v1.5.apk`
- `test_assets/app-v1.6.apk`

### 设备下载目录
- `/sdcard/Download/app-v1.5.apk`
- `/sdcard/Download/app-v1.6.apk`

## 🔐 签名信息

两个版本使用相同的签名配置：
- Keystore: `smlieapp.jks`
- Store Password: `123123`
- Key Alias: `smlieapp`
- Key Password: `123123`

这确保了补丁签名验证能够正常工作。

## 📝 代码修改

### 1. SystemInfoFragment.java
添加了 `getHotUpdateTestInfo()` 方法，用于显示版本特定的测试信息。

### 2. PatchGenerateViewModel.java
在 `onComplete()` 回调中添加了 0KB 补丁检测：
```java
long patchSize = result.getPatchFile().length();
if (patchSize < 1024) {  // 小于 1KB
    // 提示无差异，删除无效补丁
}
```

### 3. build.gradle
版本配置：
- v1.5: `versionCode 4, versionName "1.5"`
- v1.6: `versionCode 5, versionName "1.6"`

## ✅ 完成状态

- [x] 生成 v1.5 release APK
- [x] 生成 v1.6 release APK
- [x] 复制到 test_assets 目录
- [x] 推送到设备 /sdcard/Download 目录
- [x] 添加 0KB 补丁检测逻辑
- [x] 安装最新版本到设备
- [x] 创建测试说明文档

## 🎯 测试目标

1. ✅ 验证 DEX 热更新功能
2. ✅ 验证补丁生成流程
3. ✅ 验证补丁应用流程
4. ✅ 验证 0KB 补丁检测
5. ✅ 验证签名验证功能
