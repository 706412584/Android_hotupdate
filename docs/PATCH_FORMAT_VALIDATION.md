# 补丁文件格式检测

## 功能说明

从 1.3.9 版本开始，热更新系统会在应用补丁前自动验证补丁文件格式和包名，确保补丁文件的有效性和安全性。

## 验证规则

### 1. 文件格式检测

**必须是 ZIP 或加密格式：**
- ✅ `.zip` - 标准 ZIP 格式补丁
- ✅ `.enc` - AES 加密补丁
- ❌ 其他格式 - 拒绝应用

**ZIP 魔数验证：**
- 检查文件头是否为 `PK` (0x50 0x4B 0x03 0x04)
- 确保文件未损坏或被篡改

### 2. patch.json 验证

**必须包含 patch.json：**
- 补丁文件中必须包含 `patch.json` 元数据文件
- 必须能够成功解析 JSON 格式

**必需字段：**
```json
{
  "patchId": "patch_xxx",
  "patchVersion": "1.2",
  "targetVersion": "1.2",
  "packageName": "com.your.app"
}
```

### 3. 包名验证

**必须同包名：**
- 补丁的 `packageName` 必须与当前应用包名一致
- 防止误用其他应用的补丁

**向后兼容：**
- 旧版本补丁（无 `packageName` 字段）仍可使用
- 会显示警告日志，但允许继续应用

## 错误提示

### 格式错误

```
⚠️ 补丁文件格式错误！

补丁文件必须是 ZIP 格式（.zip）或加密格式（.enc）
当前文件: patch.apk
```

### 文件损坏

```
⚠️ 补丁文件损坏！

文件不是有效的 ZIP 格式，可能已损坏或被篡改。
```

### 缺少 patch.json

```
⚠️ 补丁文件格式错误！

无法读取补丁信息（patch.json）
请确保补丁文件是通过官方工具生成的。
```

### 包名不匹配

```
⚠️ 补丁包名不匹配！

补丁包名: com.other.app
当前应用: com.your.app

此补丁不适用于当前应用，请使用正确的补丁文件。
```

## 使用示例

### 正常流程

```java
HotUpdateHelper helper = HotUpdateHelper.getInstance();
helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, "进度: " + percent + "% - " + message);
    }
    
    @Override
    public void onSuccess(PatchResult result) {
        Log.i(TAG, "补丁应用成功！");
    }
    
    @Override
    public void onError(String message) {
        // 格式验证失败会在这里回调
        Log.e(TAG, "错误: " + message);
        
        // 显示友好的错误提示
        showErrorDialog(message);
    }
});
```

### 日志输出

**验证通过：**
```
I HotUpdateHelper: ✅ 补丁格式验证通过
I HotUpdateHelper:    - 文件格式: ZIP
I HotUpdateHelper:    - 补丁版本: 1.2
I HotUpdateHelper:    - 目标版本: 1.2
I HotUpdateHelper:    - 包名: com.orange.update
```

**包名不匹配：**
```
E HotUpdateHelper: ⚠️ 补丁包名不匹配！
E HotUpdateHelper: 补丁包名: com.other.app
E HotUpdateHelper: 当前应用: com.orange.update
```

**向后兼容（旧补丁）：**
```
W HotUpdateHelper: ⚠️ 补丁未包含包名信息（向后兼容旧版本补丁）
I HotUpdateHelper: ✅ 补丁格式验证通过
I HotUpdateHelper:    - 包名: 未指定（兼容模式）
```

## 补丁生成

### 自动包含包名

从 1.3.9 版本开始，补丁生成工具会自动在 `patch.json` 中包含包名信息：

**命令行工具：**
```bash
java -jar patch-cli-1.3.9-all.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip
```

生成的 `patch.json`：
```json
{
  "patchId": "patch_1768588193317_bb6aff07",
  "patchVersion": "1.2",
  "packageName": "com.orange.update",
  "baseVersion": "1.0",
  "targetVersion": "1.2",
  "patchMode": "full_dex",
  "createTime": 1768588193349
}
```

**Android SDK：**
```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .build();

generator.generateInBackground();
// 自动从 APK 中提取包名并写入 patch.json
```

## 技术细节

### 验证流程

```
1. 检查文件扩展名（.zip 或 .enc）
   ↓
2. 验证 ZIP 魔数（0x50 0x4B 0x03 0x04）
   ↓
3. 读取 patch.json
   ↓
4. 解析 JSON 字段
   ↓
5. 验证包名匹配
   ↓
6. 验证通过 ✅
```

### 实现代码

```java
private String validatePatchFormat(File patchFile) {
    // 1. 检查文件扩展名
    String fileName = patchFile.getName().toLowerCase();
    if (!fileName.endsWith(".zip") && !fileName.endsWith(".enc")) {
        return "补丁文件格式错误！";
    }
    
    // 2. 验证 ZIP 魔数
    if (!isValidZipFile(patchFile)) {
        return "补丁文件损坏！";
    }
    
    // 3. 读取 patch.json
    PatchInfo patchInfo = readPatchInfoFromZip(patchFile);
    if (patchInfo == null) {
        return "无法读取补丁信息！";
    }
    
    // 4. 验证包名
    String patchPackageName = patchInfo.getPackageName();
    String currentPackageName = context.getPackageName();
    
    if (patchPackageName != null && !patchPackageName.equals(currentPackageName)) {
        return "补丁包名不匹配！";
    }
    
    return null; // 验证通过
}
```

## 安全性

### 防止误用

- ✅ **包名验证** - 防止使用其他应用的补丁
- ✅ **格式检测** - 防止使用非法文件
- ✅ **完整性检查** - 防止文件损坏或篡改

### 攻击防护

- ✅ **魔数验证** - 防止伪造文件扩展名
- ✅ **JSON 解析** - 防止恶意 JSON 注入
- ✅ **异常处理** - 防止崩溃攻击

## 常见问题

### Q1: 旧版本补丁还能用吗？

**A:** 可以。旧版本补丁（无 `packageName` 字段）仍然可以使用，系统会显示警告日志但允许继续应用。

### Q2: 如何生成包含包名的补丁？

**A:** 使用 1.3.9 或更高版本的补丁生成工具，会自动从 APK 中提取包名并写入 `patch.json`。

### Q3: 可以跳过包名验证吗？

**A:** 不建议。包名验证是重要的安全机制，防止误用其他应用的补丁。如果确实需要，可以修改源码移除验证逻辑。

### Q4: 加密补丁如何验证？

**A:** 
- AES 加密补丁（.enc）：先解密，再验证
- ZIP 密码保护：直接读取 ZIP 内容验证

### Q5: 验证失败会影响应用运行吗？

**A:** 不会。验证失败只是拒绝应用补丁，不会影响应用正常运行。

## 最佳实践

1. **使用最新工具**
   - 使用 1.3.9+ 版本的补丁生成工具
   - 确保生成的补丁包含包名信息

2. **测试验证**
   - 生成补丁后先在测试环境验证
   - 检查 patch.json 是否包含正确的包名

3. **错误处理**
   - 捕获 `onError` 回调
   - 向用户显示友好的错误提示
   - 记录错误日志便于排查

4. **日志监控**
   - 关注格式验证日志
   - 监控包名不匹配情况
   - 及时发现问题补丁

## 总结

- ✅ **自动验证**：应用补丁前自动验证格式和包名
- ✅ **安全防护**：防止误用、损坏、篡改
- ✅ **友好提示**：清晰的错误消息，便于排查
- ✅ **向后兼容**：旧版本补丁仍可使用
- ✅ **零配置**：无需额外配置，自动生效

