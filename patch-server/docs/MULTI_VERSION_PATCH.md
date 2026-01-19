# 多版本补丁支持文档

## 概述

本文档说明如何使用多版本补丁功能，包括：
1. 客户端查看已应用的补丁信息
2. 服务端智能判断是否需要更新
3. 服务端合并补丁功能（跨版本更新）

## 1. 客户端查看已应用的补丁信息

### 1.1 使用 UpdateManager

```java
// 获取当前补丁信息
UpdateManager updateManager = UpdateManager.getInstance();
PatchInfo currentPatch = updateManager.getCurrentPatchInfo();

if (currentPatch != null) {
    Log.i(TAG, "当前补丁 ID: " + currentPatch.getPatchId());
    Log.i(TAG, "补丁版本: " + currentPatch.getPatchVersion());
    Log.i(TAG, "基础版本: " + currentPatch.getBaseVersion());
    Log.i(TAG, "目标版本: " + currentPatch.getTargetAppVersion());
} else {
    Log.i(TAG, "未应用任何补丁");
}

// 检查是否有已应用的补丁
boolean hasAppliedPatch = updateManager.hasAppliedPatch();
```

### 1.2 使用 HotUpdateHelper

```java
HotUpdateHelper helper = new HotUpdateHelper(context);

// 获取当前补丁 ID
String patchId = helper.getAppliedPatchId();

// 获取完整补丁信息
PatchInfo patchInfo = helper.getAppliedPatchInfo();
```

### 1.3 使用 PatchStorage（底层 API）

```java
PatchStorage storage = new PatchStorage(context);

// 获取当前应用的补丁 ID
String appliedPatchId = storage.getAppliedPatchId();

// 获取补丁信息
PatchInfo patchInfo = storage.getAppliedPatchInfo();

// 获取所有已下载的补丁
List<PatchInfo> downloadedPatches = storage.getDownloadedPatches();
```

## 2. 服务端智能判断更新

### 2.1 检查更新 API

**端点**: `GET /api/client/check-update`

**请求参数**:
```
version: 应用版本号（必需）
appId: 应用 ID（可选）
deviceId: 设备 ID（可选，用于灰度发布）
deviceModel: 设备型号（可选）
osVersion: 系统版本（可选）
currentPatchVersion: 当前补丁版本（可选，重要！）
```

**响应示例**:

有更新：
```json
{
  "hasUpdate": true,
  "patch": {
    "version": "1.2.0",
    "patchId": "patch_v1.1_to_v1.2",
    "baseVersion": "1.1.0",
    "downloadUrl": "http://server.com/api/client/download/123",
    "md5": "abc123...",
    "size": 1024000,
    "description": "修复若干问题",
    "forceUpdate": false
  },
  "securityConfig": {
    "requireSignature": true,
    "requireEncryption": false
  }
}
```

无更新：
```json
{
  "hasUpdate": false,
  "message": "当前已是最新版本",
  "securityConfig": {
    "requireSignature": true,
    "requireEncryption": false
  }
}
```

### 2.2 客户端使用示例

```java
UpdateConfig config = new UpdateConfig.Builder()
    .serverUrl("https://your-server.com")
    .appKey("your-app-id")
    .appVersion("1.0.0")
    .build();

UpdateManager.init(context, config);
UpdateManager updateManager = UpdateManager.getInstance();

// 检查更新（自动发送当前补丁版本）
updateManager.setCallback(new SimpleUpdateCallback() {
    @Override
    public void onCheckComplete(boolean hasUpdate, PatchInfo patchInfo) {
        if (hasUpdate) {
            Log.i(TAG, "发现新补丁: " + patchInfo.getPatchVersion());
            // 下载并应用补丁
            updateManager.downloadPatch(patchInfo);
        } else {
            Log.i(TAG, "当前已是最新版本");
        }
    }
});

updateManager.checkUpdate();
```

### 2.3 服务端判断逻辑

服务端会根据以下信息判断是否需要更新：

1. **优先使用补丁版本**：如果客户端提供了 `currentPatchVersion`，使用它作为当前版本
2. **否则使用应用版本**：如果没有补丁版本，使用 `version` 参数
3. **查找匹配的补丁**：查找 `base_version` 等于当前版本的补丁
4. **灰度发布检查**：如果补丁设置了灰度百分比，根据设备 ID 判断是否推送

**示例场景**：

| 应用版本 | 当前补丁版本 | 服务端判断 | 返回补丁 |
|---------|------------|----------|---------|
| 1.0.0 | null | 基于 1.0.0 查找 | 1.0.0 → 1.1.0 |
| 1.0.0 | 1.1.0 | 基于 1.1.0 查找 | 1.1.0 → 1.2.0 |
| 1.0.0 | 1.2.0 | 基于 1.2.0 查找 | 无更新 |

## 3. 获取当前补丁信息 API

### 3.1 API 端点

**端点**: `GET /api/client/current-patch`

**请求参数**:
```
appId: 应用 ID（必需）
deviceId: 设备 ID（必需）
```

**响应示例**:

有已应用的补丁：
```json
{
  "hasAppliedPatch": true,
  "patch": {
    "patchId": "patch_v1.0_to_v1.1",
    "patchVersion": "1.1.0",
    "baseVersion": "1.0.0",
    "appVersion": "1.0.0",
    "description": "修复若干问题",
    "fileSize": 1024000,
    "md5": "abc123...",
    "appliedAt": "2026-01-19T10:30:00.000Z"
  }
}
```

无已应用的补丁：
```json
{
  "hasAppliedPatch": false,
  "message": "未找到已应用的补丁"
}
```

### 3.2 使用场景

1. **服务端统计**：了解用户当前使用的补丁版本分布
2. **问题排查**：当用户报告问题时，查询其补丁版本
3. **灰度发布监控**：监控补丁应用情况

## 4. 补丁合并功能（跨版本更新）

### 4.1 问题场景

用户从 v1.0 跳到 v1.3，但服务端只有：
- v1.0 → v1.1 的补丁
- v1.1 → v1.2 的补丁
- v1.2 → v1.3 的补丁

### 4.2 解决方案

**方案 A：服务端合并补丁（推荐）**

服务端根据用户当前版本，自动生成完整补丁。

**优点**：
- 客户端简单，只需应用一个补丁
- 补丁大小可能更小（直接 diff）
- 更可靠，减少多次应用的风险

**缺点**：
- 需要保存原始 APK 文件
- 服务端需要 patch-cli 工具
- 首次生成需要时间

**实现步骤**：

1. 上传原始 APK 到服务端：
```bash
# 上传基础版本 APK
curl -X POST http://server.com/api/admin/upload-apk \
  -F "file=@app-v1.0.apk" \
  -F "version=1.0.0"

# 上传目标版本 APK
curl -X POST http://server.com/api/admin/upload-apk \
  -F "file=@app-v1.3.apk" \
  -F "version=1.3.0"
```

2. 服务端自动生成合并补丁：
```javascript
const PatchMerger = require('./utils/patchMerger');
const merger = new PatchMerger('/path/to/patch-cli.jar');

// 生成 v1.0 → v1.3 的完整补丁
const result = await merger.generateMergedPatch(
  '/path/to/app-v1.0.apk',
  '/path/to/app-v1.3.apk',
  '/path/to/output/patch_v1.0_to_v1.3.zip',
  {
    keystore: '/path/to/keystore.jks',
    keystorePassword: 'password',
    keyAlias: 'alias',
    keyPassword: 'password'
  }
);

console.log('补丁生成成功:', result.outputPath);
console.log('文件大小:', result.fileSize);
console.log('MD5:', result.md5);
```

3. 客户端检查更新时，服务端返回合并后的补丁

**方案 B：客户端链式应用（不推荐）**

客户端依次下载并应用多个补丁。

**优点**：
- 服务端简单，不需要额外处理
- 可以复用已有补丁

**缺点**：
- 客户端复杂，需要管理补丁链
- 下载时间长（多个补丁）
- 应用失败风险高（任何一步失败都会导致整体失败）
- 用户体验差

### 4.3 补丁合并工具使用

```javascript
const PatchMerger = require('./utils/patchMerger');
const db = require('./models/database');

const merger = new PatchMerger();

// 查找补丁链
const patchChain = await merger.findPatchChain(
  db,
  'your-app-id',
  '1.0.0',  // 起始版本
  '1.3.0'   // 目标版本
);

if (patchChain) {
  console.log('找到补丁链:', patchChain.length, '个补丁');
  
  // 生成合并补丁
  const result = await merger.generateMergedPatch(
    '/path/to/app-v1.0.apk',
    '/path/to/app-v1.3.apk',
    '/path/to/output/merged_patch.zip'
  );
  
  console.log('合并补丁生成成功');
} else {
  console.log('未找到补丁链');
}
```

## 5. 最佳实践

### 5.1 版本管理

1. **保存原始 APK**：每个版本的 APK 都应该保存，用于生成合并补丁
2. **版本号规范**：使用语义化版本号（如 1.2.3）
3. **补丁命名**：使用 `patch_v{base}_to_v{target}` 格式

### 5.2 补丁策略

1. **增量补丁**：为相邻版本生成增量补丁（如 v1.0→v1.1）
2. **完整补丁**：为常见跳跃版本生成完整补丁（如 v1.0→v1.3）
3. **按需生成**：首次请求时生成，然后缓存

### 5.3 客户端集成

```java
// Application.onCreate()
@Override
public void onCreate() {
    super.onCreate();
    
    // 初始化 UpdateManager
    UpdateConfig config = new UpdateConfig.Builder()
        .serverUrl("https://your-server.com")
        .appKey("your-app-id")
        .appVersion(BuildConfig.VERSION_NAME)
        .build();
    
    UpdateManager.init(this, config);
    
    // 加载已应用的补丁
    UpdateManager.getInstance().loadAppliedPatch();
}

// 检查更新
private void checkForUpdates() {
    UpdateManager updateManager = UpdateManager.getInstance();
    
    // 获取当前补丁信息（用于日志）
    PatchInfo currentPatch = updateManager.getCurrentPatchInfo();
    if (currentPatch != null) {
        Log.i(TAG, "当前补丁版本: " + currentPatch.getPatchVersion());
    }
    
    // 检查更新（自动发送当前补丁版本）
    updateManager.setCallback(new SimpleUpdateCallback() {
        @Override
        public void onCheckComplete(boolean hasUpdate, PatchInfo patchInfo) {
            if (hasUpdate) {
                // 显示更新对话框
                showUpdateDialog(patchInfo);
            }
        }
    });
    
    updateManager.checkUpdate();
}
```

## 6. 故障排查

### 6.1 客户端无法获取补丁信息

**问题**：`getCurrentPatchInfo()` 返回 null

**可能原因**：
1. 补丁未成功应用
2. SharedPreferences 数据丢失
3. 补丁文件被删除

**解决方法**：
```java
// 检查补丁文件是否存在
PatchStorage storage = new PatchStorage(context);
String patchId = storage.getAppliedPatchId();
if (patchId != null) {
    boolean fileExists = storage.hasPatchFile(patchId);
    Log.d(TAG, "补丁文件存在: " + fileExists);
}
```

### 6.2 服务端返回错误的补丁

**问题**：服务端返回的补丁 base_version 与客户端不匹配

**可能原因**：
1. 客户端未发送 `currentPatchVersion` 参数
2. 服务端数据库中补丁配置错误

**解决方法**：
1. 确认客户端发送了正确的参数
2. 检查服务端日志
3. 验证数据库中的补丁配置

### 6.3 补丁合并失败

**问题**：`generateMergedPatch` 抛出异常

**可能原因**：
1. patch-cli.jar 不存在
2. APK 文件路径错误
3. 磁盘空间不足
4. Java 未安装或版本不兼容

**解决方法**：
```bash
# 检查 Java 版本
java -version

# 检查 patch-cli
java -jar patch-cli.jar --version

# 手动测试补丁生成
java -jar patch-cli.jar \
  --base app-v1.0.apk \
  --new app-v1.3.apk \
  --output test_patch.zip
```

## 7. API 参考

### 7.1 客户端 API

| 方法 | 说明 | 返回值 |
|------|------|--------|
| `UpdateManager.getCurrentPatchInfo()` | 获取当前补丁信息 | PatchInfo 或 null |
| `UpdateManager.getCurrentPatchId()` | 获取当前补丁 ID | String 或 null |
| `UpdateManager.hasAppliedPatch()` | 检查是否有已应用的补丁 | boolean |
| `HotUpdateHelper.getAppliedPatchInfo()` | 获取当前补丁信息 | PatchInfo 或 null |
| `PatchStorage.getAppliedPatchInfo()` | 获取当前补丁信息 | PatchInfo 或 null |

### 7.2 服务端 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/client/check-update` | GET | 检查更新 |
| `/api/client/current-patch` | GET | 获取当前补丁信息 |
| `/api/client/download/:id` | GET | 下载补丁 |
| `/api/client/report` | POST | 上报应用结果 |

## 8. 总结

多版本补丁支持提供了以下能力：

1. ✅ 客户端可以查询当前已应用的补丁信息
2. ✅ 服务端可以根据补丁版本智能判断是否需要更新
3. ✅ 服务端可以生成合并补丁支持跨版本更新
4. ✅ 完整的 API 支持和文档

**推荐使用方式**：
- 客户端：使用 `UpdateManager` 进行更新检查和补丁应用
- 服务端：为常见跳跃版本预生成合并补丁，提升用户体验
