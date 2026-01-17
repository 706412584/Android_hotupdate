# Maven Central 发布记录

## v1.3.0 (2026-01-18)

### 新增功能

- ✅ **新增 HotUpdateHelper 类** - 提供简单易用的热更新 API
  - 带回调接口（onProgress, onSuccess, onError）
  - 自动处理 PatchInfo 创建和 MD5 计算
  - 支持异步和同步两种方式
  - 提供完整的补丁管理功能（应用、加载、清除、查询）

### 改进

- 📝 更新了 README.md，推荐使用 HotUpdateHelper 作为首选方式
- 📝 更新了 MODULES.md，添加了 HotUpdateHelper 的详细说明
- 🔧 优化了 update 模块的 API 设计

### 发布的模块

1. **patch-core:1.3.0** - 核心补丁算法
2. **patch-generator-android:1.3.0** - Android 补丁生成器
3. **update:1.3.0** - 热更新核心库（推荐）

### 使用方法

```groovy
dependencies {
    // 热更新核心库（推荐 - 包含完整功能）
    implementation 'io.github.706412584:update:1.3.0'
}
```

### 升级指南

从 v1.2.9 升级到 v1.3.0：

1. 更新依赖版本号为 `1.3.0`
2. （可选）使用新的 HotUpdateHelper API 简化代码：

```java
// 旧方式（仍然支持）
PatchApplier patchApplier = new PatchApplier(context, new PatchStorage(context));
patchApplier.apply(patchInfo);

// 新方式（推荐）
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        // 显示进度
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        // 热更新成功
    }
    
    @Override
    public void onError(String message) {
        // 处理错误
    }
});
```

---

## v1.2.9 (2026-01-18)

### 首次发布

- ✅ 成功发布 `patch-core:1.2.9` 到 Maven Central
- ✅ 成功发布 `patch-generator-android:1.2.9` 到 Maven Central
- ✅ 成功发布 `update:1.2.9` 到 Maven Central（热更新核心库）
- ✅ 配置 GPG 签名（密钥 ID: 94CEE4A6C60913C4）
- ✅ 验证命名空间 `io.github.706412584`
- ✅ 上传公钥到 keys.openpgp.org 和 keyserver.ubuntu.com

### 发布的模块

1. **patch-core** - 核心补丁算法
   - JAR 文件
   - Sources JAR
   - Javadoc JAR
   - 所有 GPG 签名和校验和

2. **patch-generator-android** - Android 补丁生成器
   - AAR 文件
   - Sources JAR
   - Javadoc JAR
   - 所有 GPG 签名和校验和

3. **update** - 热更新核心库（推荐）
   - AAR 文件
   - Sources JAR
   - Javadoc JAR
   - 所有 GPG 签名和校验和
   - 包含完整的热更新功能实现

### 问题解决

1. **Bundle 路径问题** - 修复了 Bundle 创建时的路径错误
2. **密钥不匹配** - 发现并修复了配置密钥与实际使用密钥不一致的问题
3. **公钥验证** - 上传正确的公钥到密钥服务器

### 配置信息

- Group ID: `io.github.706412584`
- Artifacts: 
  - `patch-core` - 核心补丁算法
  - `patch-generator-android` - Android 补丁生成器
  - `update` - 热更新核心库（推荐）
- Version: `1.2.9`
- GPG Key: `94CEE4A6C60913C4`
- 发布时间: 2026-01-18

### 使用方法

```groovy
dependencies {
    // 热更新核心库（推荐 - 包含完整功能）
    implementation 'io.github.706412584:update:1.2.9'
}
```

---

## 下次发布

更新版本号后运行：

```bash
publish-maven.bat
```

或

```bash
cd maven-central
publish.bat
```
