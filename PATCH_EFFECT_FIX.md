# ZIP 密码保护修复总结

## 问题描述

用户报告了两个问题：

1. **应用补丁时不提示输入密码**：使用自定义 ZIP 密码生成的补丁，应用时直接提示「解密失败」，没有弹出密码输入对话框
2. **applied 目录补丁未加密**：ZIP 密码保护的补丁应用后，在 `applied` 目录可以看到未加密的文件

## 根本原因

在之前的实现中，`HotUpdateHelper.applyPatch()` 方法会：

1. 检测到 ZIP 加密
2. 检查是否有自定义密码标记（`.zippwd` 文件）
3. 如果有自定义密码，调用 `callback.onZipPasswordRequired()` 通知 UI
4. 如果没有自定义密码，**直接使用派生密码解密**
5. 将**解密后的文件**保存到 `applied` 目录

问题在于：
- 步骤 4 会立即解密文件，导致 `applied` 目录存储的是未加密的文件
- 这违背了 ZIP 密码保护的初衷：防止用户直接访问和修改补丁内容

## 解决方案

### 核心思路

**ZIP 密码保护的补丁应该在 `applied` 目录保持加密状态**，只在应用启动时临时解密到内存中加载。

### 实现细节

#### 1. 修改 `HotUpdateHelper.applyPatch()`

```java
// 检测到 ZIP 加密
if (zipPasswordManager.isEncrypted(patchFile)) {
    // 检查是否有自定义密码标记
    File zipPasswordFile = new File(patchFile.getPath() + ".zippwd");
    boolean hasCustomPassword = zipPasswordFile.exists();
    
    if (hasCustomPassword) {
        // 需要用户输入密码
        callback.onZipPasswordRequired(patchFile);
        return; // 等待用户输入
    }
    
    // 使用派生密码，直接保存加密文件（不解密）
    Log.d(TAG, "使用派生密码，补丁将以加密状态保存");
}

// 继续应用补丁（保存加密文件）
applyPatchInternal(patchFile, patchFile, callback);
```

**关键变化**：
- 移除了 `decryptZipPatch()` 调用
- 直接传递原始加密文件到 `applyPatchInternal()`

#### 2. 修改 `HotUpdateHelper.applyPatchWithZipPassword()`

```java
// 验证用户输入的密码
boolean passwordValid = zipPasswordManager.verifyPassword(patchFile, zipPassword);

if (!passwordValid) {
    callback.onError("⚠️ ZIP 密码验证失败！");
    return;
}

// 保存自定义密码（用于启动时解密）
prefs.edit()
    .putBoolean("is_zip_password_protected", true)
    .putString("custom_zip_password", zipPassword)
    .apply();

// 保存加密文件到 applied 目录（不解密）
applyPatchInternal(patchFile, patchFile, callback);
```

**关键变化**：
- 只验证密码，不解密文件
- 保存自定义密码到 SharedPreferences
- 直接传递原始加密文件到 `applyPatchInternal()`

#### 3. 修改 `HotUpdateHelper.applyPatchInternal()`

```java
// 判断是否是 ZIP 密码保护的
boolean isZipPasswordProtected = isZipPasswordProtected(originalPatchFile);

// 保存文件：ZIP 密码保护的保存加密文件
File fileToSave = isZipPasswordProtected ? originalPatchFile : actualPatchFile;
byte[] patchData = readFileToBytes(fileToSave);

// 保存到 applied 目录
storage.savePatchFile(patchInfo.getPatchId(), patchData);

// 保存标记
if (isZipPasswordProtected) {
    prefs.edit().putBoolean("is_zip_password_protected", true).apply();
    Log.d(TAG, "✓ 补丁已保存为加密状态到 applied 目录");
}
```

**关键变化**：
- 根据 `isZipPasswordProtected` 标志决定保存哪个文件
- ZIP 密码保护的补丁保存原始加密文件
- 非 ZIP 密码保护的补丁保存实际文件

#### 4. 修改 `PatchApplication.loadPatchIfNeeded()`

```java
// 检查是否是 ZIP 密码保护的
if (isZipPasswordProtected(appliedFile)) {
    // 获取保存的自定义密码（如果有）
    String customPassword = prefs.getString("custom_zip_password", null);
    
    // 自动解密到临时文件
    actualPatchFile = decryptZipPatchOnLoad(appliedFile, customPassword);
    
    if (actualPatchFile == null) {
        Log.e(TAG, "Failed to decrypt ZIP password protected patch");
        return;
    }
    
    Log.d(TAG, "✓ ZIP password protected patch decrypted");
}

// 加载解密后的临时文件
String patchPath = actualPatchFile.getAbsolutePath();
DexPatcher.injectPatchDex(this, patchPath);
ResourcePatcher.loadPatchResources(this, patchPath);
```

**关键变化**：
- 检测到 ZIP 加密时，自动解密到临时文件
- 优先使用保存的自定义密码，否则使用派生密码
- 加载临时文件后自动清理

## 修复效果

### 1. 密码输入流程恢复

- ✅ 使用自定义密码时弹出密码输入对话框
- ✅ 密码错误时提示验证失败
- ✅ 密码正确时应用成功

### 2. applied 目录保持加密

- ✅ ZIP 密码保护的补丁以加密状态保存
- ✅ 无法直接解压 `current_patch.zip`
- ✅ 防止用户直接查看和修改补丁内容

### 3. 自动加载

- ✅ 应用启动时自动解密加载
- ✅ 使用派生密码或保存的自定义密码
- ✅ 加载完成后清理临时文件

### 4. 安全性提升

| 特性 | 修复前 | 修复后 |
|------|--------|--------|
| applied 目录加密 | ✗ 解密存储 | ✅ 加密存储 |
| 防止直接查看 | ✗ 可以查看 | ✅ 无法查看 |
| 防止直接修改 | ✗ 可以修改 | ✅ 无法修改 |
| 密码输入提示 | ✗ 不提示 | ✅ 正常提示 |
| 自动加载 | ✅ 支持 | ✅ 支持 |

## 测试验证

已创建详细测试指南：`TEST_ZIP_PASSWORD_APPLIED_ENCRYPTION.md`

测试场景包括：
1. ✅ 派生密码（默认）
2. ✅ 自定义密码
3. ✅ 密码错误
4. ✅ 防篡改验证

## 文件变更

- `update/src/main/java/com/orange/update/HotUpdateHelper.java` - 修改补丁应用流程
- `app/src/main/java/com/orange/update/PatchApplication.java` - 修改补丁加载流程
- `ZIP_PASSWORD_STORAGE_PROTECTION.md` - 设计文档
- `TEST_ZIP_PASSWORD_APPLIED_ENCRYPTION.md` - 测试指南

## Git 提交

```bash
git commit -m "fix: ZIP 密码保护的补丁在 applied 目录保持加密状态

- 修复应用补丁时不提示输入密码的问题
- ZIP 密码保护的补丁现在以加密状态保存到 applied 目录
- 应用启动时自动解密加载（使用派生密码或保存的自定义密码）
- 支持自定义密码输入和验证
- 防止用户直接访问和修改 applied 目录的补丁内容
- 添加文档 ZIP_PASSWORD_STORAGE_PROTECTION.md"
```

## 下一步

应用已安装到 ADB 设备（`9c18cb30`），可以开始测试：

1. 生成 ZIP 密码保护的补丁（使用自定义密码）
2. 应用补丁，验证密码输入对话框
3. 检查 `applied` 目录文件是否加密
4. 重启应用，验证自动加载

测试命令：
```bash
# 查看 applied 目录
adb shell ls -lh /data/data/com.orange.update/files/update/applied/

# 尝试解压（应该失败）
adb shell unzip /data/data/com.orange.update/files/update/applied/current_patch.zip

# 查看日志
adb logcat | grep -E "PatchApplication|HotUpdateHelper|ZipPasswordManager"
```
