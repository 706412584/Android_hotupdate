# 补丁自动恢复功能测试指南

## 功能说明

当检测到补丁被篡改时，系统会自动从加密存储中恢复补丁，无需用户手动操作。

## 工作流程

```
应用启动 (attachBaseContext)
    ↓
检测到补丁被篡改
    ↓
删除被篡改的文件
    ↓
标记需要恢复 (need_patch_recovery = true)
    ↓
继续启动（使用原始代码）
    ↓
Application.onCreate()
    ↓
检查恢复标记
    ↓
从加密存储恢复补丁
    ↓
验证恢复的补丁哈希
    ↓
恢复成功 → 提示用户重启
    ↓
用户重启应用
    ↓
加载恢复的补丁 ✅
```

## 测试步骤

### 前提条件
- 设备 ID: `9c18cb30`
- 已安装最新版本的应用
- 有可用的补丁文件

### 步骤 1: 应用补丁

```bash
# 1. 启动应用
adb -s 9c18cb30 shell am start -n com.orange.update/.MainActivity

# 2. 在应用中点击"应用补丁"按钮
# 3. 选择补丁文件（例如：patch_1768678370576.zip）
# 4. 等待补丁应用成功
# 5. 重启应用查看补丁效果（火焰图标 🔥）
```

### 步骤 2: 篡改补丁文件

```bash
# 篡改已应用的补丁文件
adb -s 9c18cb30 shell "echo 'tampered' >> /data/data/com.orange.update/files/update/applied/current_patch.zip"

# 验证文件已被修改
adb -s 9c18cb30 shell "ls -lh /data/data/com.orange.update/files/update/applied/current_patch.zip"
```

### 步骤 3: 重启应用并观察自动恢复

```bash
# 清除日志
adb -s 9c18cb30 logcat -c

# 重启应用
adb -s 9c18cb30 shell am force-stop com.orange.update
adb -s 9c18cb30 shell am start -n com.orange.update/.MainActivity

# 等待 3 秒
timeout /t 3

# 查看日志
adb -s 9c18cb30 logcat -d -s PatchApplication:* PatchStorage:*
```

### 预期日志输出

#### attachBaseContext 阶段（检测篡改）
```
D PatchApplication: Loading applied patch: patch_1768678370576
E PatchApplication: ⚠️ PATCH INTEGRITY CHECK FAILED!
E PatchApplication: Expected: 4f2db21b813322904e7136432a804f6540ccb5cbb90470ea2c0ccd3bc6e47663
E PatchApplication: Actual:   [不同的哈希值]
E PatchApplication: ⚠️ Patch integrity verification failed
E PatchApplication: ⚠️ Patch tampered! Attempt: 1/3
D PatchApplication: Deleted tampered patch file
W PatchApplication: ⚠️ Patch cleared. Will attempt recovery in onCreate()
```

#### onCreate 阶段（自动恢复）
```
I PatchApplication: 🔄 Attempting to recover patch from encrypted storage: patch_1768678370576
D PatchStorage: Prepared patch to applied directory: patch_1768678370576
D PatchStorage: Saved patch hash: 4f2db21b81332290...
I PatchApplication: ✅ Patch recovered successfully from encrypted storage
I PatchApplication: ✅ Hash verified: 4f2db21b81332290...
I PatchApplication: ⚠️ Please restart the app to load the recovered patch
```

#### Toast 提示
应用会显示 Toast 消息：
```
补丁已恢复，请重启应用
```

### 步骤 4: 再次重启应用

```bash
# 重启应用加载恢复的补丁
adb -s 9c18cb30 shell am force-stop com.orange.update
adb -s 9c18cb30 shell am start -n com.orange.update/.MainActivity

# 查看日志
adb -s 9c18cb30 logcat -d -s PatchApplication:*
```

### 预期结果
```
D PatchApplication: Loading applied patch: patch_1768678370576
D PatchApplication: ✅ Patch integrity verified: 4f2db21b81332290...
D PatchApplication: Patch contains resources, merging with original APK
I PatchApplication: Resources merged successfully, size: 1440680
D PatchApplication: Dex patch loaded successfully
D PatchApplication: Resource patch loaded successfully
I PatchApplication: ✅ Patch loading completed with integrity verification
```

- ✅ 补丁完整性验证通过
- ✅ 补丁正常加载
- ✅ 火焰图标 🔥 显示

## 测试场景

### 场景 1: 第一次篡改（自动恢复）
- ⚠️ 检测到篡改
- 🔄 自动从加密存储恢复
- ✅ 恢复成功
- 📊 篡改计数重置为 0
- 🔄 提示用户重启

### 场景 2: 多次篡改（3 次以内）
- ⚠️ 每次检测到篡改
- 🔄 每次自动恢复
- 📊 篡改计数：1/3, 2/3
- ✅ 恢复成功后重置计数

### 场景 3: 超过 3 次篡改
- ⚠️ 检测到第 3 次篡改
- 🚫 超过安全阈值
- 🗑️ 清除所有补丁元数据
- ❌ 不再尝试恢复
- 📡 可选：上报安全事件

### 场景 4: 恢复失败
- ⚠️ 检测到篡改
- 🔄 尝试恢复
- ❌ 恢复的补丁哈希不匹配
- 📊 篡改计数 +1
- 🔄 下次启动再次尝试

## 验证要点

### ✅ 自动恢复
- 无需用户手动操作
- 从加密存储恢复
- 验证恢复的补丁哈希

### ✅ 用户体验
- Toast 提示恢复成功
- 提示用户重启应用
- 重启后补丁正常工作

### ✅ 安全保障
- 篡改计数机制
- 超过限制后清除数据
- 恢复失败时增加计数

### ✅ 日志记录
- 详细的恢复过程日志
- 哈希值验证日志
- 错误和警告日志

## 故障排查

### 问题 1: 恢复失败 - 加密文件不存在
```
E PatchApplication: ❌ Failed to recover patch from encrypted storage
```
**原因**: 加密的补丁文件 `.enc` 已被删除

**解决**: 用户需要重新下载补丁

### 问题 2: 恢复失败 - 哈希不匹配
```
E PatchApplication: ❌ Recovered patch hash mismatch
```
**原因**: 加密存储中的补丁也被篡改（极少见）

**解决**: 清除所有补丁数据，重新下载

### 问题 3: Toast 不显示
**原因**: 主线程繁忙或应用在后台

**解决**: 检查日志确认恢复成功，手动重启应用

## 性能影响

- **检测篡改**: ~10ms（SHA-256 计算）
- **自动恢复**: ~100-200ms（解密 + 验证）
- **用户感知**: 几乎无感知（在后台执行）

## 安全优势

1. **自动化**: 无需用户干预
2. **快速**: 在 onCreate 中完成
3. **安全**: 从加密存储恢复
4. **可靠**: 验证恢复的补丁哈希
5. **友好**: Toast 提示用户

## 总结

补丁自动恢复功能为热更新系统提供了强大的自愈能力：
- ✅ 自动检测篡改
- ✅ 自动从加密存储恢复
- ✅ 自动验证恢复结果
- ✅ 友好的用户提示

这大大提升了系统的安全性和可靠性！🎉
