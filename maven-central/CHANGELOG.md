# Maven Central 发布记录

## v1.5.0 (2026-10-06)

### 安全修复（重要）

- 🔒 **签名校验默认开启**：`requireSignature` 默认值由 `false` 改为 `true`。
  未签名或签名不匹配的补丁默认会被拒绝。如需恢复旧行为，调用
  `setRequireSignature(false)`（会输出显著告警）。
- 🔒 **移除 debugMode 验签旁路**：`SecurityManager.verifySignature` 与
  `PatchManager.verifySignature` 在无公钥时不再返回 true，改为 fail-closed。
- 🔒 **修复完整性基线自我满足**：`recoverPatch` 不再用恢复内容回写
  `applied_patch_hash`，改为用已知良好基线校验恢复结果。
- 🔒 **补丁缺少 packageName 时拒绝应用**（此前仅告警放行）。
- 🔒 **生成侧签名算法由 SHA1withRSA 改为 SHA256withRSA**，与应用侧一致。

### 修复

- 修正 README/FAQ 中「使用 apksig 验证」的错误表述（实际使用标准
  `java.util.jar.JarFile`），移除指向不存在的 `docs/SECURITY.md` 的链接。
- 修正英文 README 中不存在的 `setSecurityPolicy()` 示例。

### 说明

- 防篡改基线仍为明文 SHA-256，与补丁同处应用私有目录；能检测意外损坏与
  低权限篡改，但无法抵御可写入应用私有目录的攻击者。
- 「从加密存储自动恢复补丁」在当前调用路径下不可用（补丁加载发生在
  `Application.attachBaseContext()`，此时 KeyStore 不可用）。

### 发布的模块

1. **update:1.5.0**
2. **patch-core:1.5.0**
3. **patch-native:1.5.0**
4. **patch-generator-android:1.5.0**
5. **patch-cli:1.5.0**
6. **patch-gradle-plugin:1.5.0**

### 升级提示

从 1.4.0 升级后，**未签名的补丁将默认被拒绝**。请确保补丁使用与应用 APK
相同的密钥签名，或显式调用 `setRequireSignature(false)`（不推荐）。



## v1.3.0 (2026-01-18)

### 新增功能

- �?**新增 HotUpdateHelper �?* - 提供简单易用的热更�?API
  - 带回调接口（onProgress, onSuccess, onError�?
  - 自动处理 PatchInfo 创建�?MD5 计算
  - 支持异步和同步两种方�?
  - 提供完整的补丁管理功能（应用、加载、清除、查询）

### 改进

- 📝 更新�?README.md，推荐使�?HotUpdateHelper 作为首选方�?
- 📝 更新�?MODULES.md，添加了 HotUpdateHelper 的详细说�?
- 🔧 优化�?update 模块�?API 设计

### 发布的模�?

1. **patch-core:1.3.0** - 核心补丁算法
2. **patch-generator-android:1.3.0** - Android 补丁生成�?
3. **update:1.3.0** - 热更新核心库（推荐）

### 使用方法

```groovy
dependencies {
    // 热更新核心库（推�?- 包含完整功能�?
    implementation 'io.github.706412584:update:1.3.0'
}
```

### 升级指南

�?v1.2.9 升级�?v1.3.0�?

1. 更新依赖版本号为 `1.3.0`
2. （可选）使用新的 HotUpdateHelper API 简化代码：

```java
// 旧方式（仍然支持�?
PatchApplier patchApplier = new PatchApplier(context, new PatchStorage(context));
patchApplier.apply(patchInfo);

// 新方式（推荐�?
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        // 显示进度
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        // 热更新成�?
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

- �?成功发布 `patch-core:1.2.9` �?Maven Central
- �?成功发布 `patch-generator-android:1.2.9` �?Maven Central
- �?成功发布 `update:1.2.9` �?Maven Central（热更新核心库）
- �?配置 GPG 签名（密�?ID: 94CEE4A6C60913C4�?
- �?验证命名空间 `io.github.706412584`
- �?上传公钥�?keys.openpgp.org �?keyserver.ubuntu.com

### 发布的模�?

1. **patch-core** - 核心补丁算法
   - JAR 文件
   - Sources JAR
   - Javadoc JAR
   - 所�?GPG 签名和校验和

2. **patch-generator-android** - Android 补丁生成�?
   - AAR 文件
   - Sources JAR
   - Javadoc JAR
   - 所�?GPG 签名和校验和

3. **update** - 热更新核心库（推荐）
   - AAR 文件
   - Sources JAR
   - Javadoc JAR
   - 所�?GPG 签名和校验和
   - 包含完整的热更新功能实现

### 问题解决

1. **Bundle 路径问题** - 修复�?Bundle 创建时的路径错误
2. **密钥不匹�?* - 发现并修复了配置密钥与实际使用密钥不一致的问题
3. **公钥验证** - 上传正确的公钥到密钥服务�?

### 配置信息

- Group ID: `io.github.706412584`
- Artifacts: 
  - `patch-core` - 核心补丁算法
  - `patch-generator-android` - Android 补丁生成�?
  - `update` - 热更新核心库（推荐）
- Version: `1.2.9`
- GPG Key: `94CEE4A6C60913C4`
- 发布时间: 2026-01-18

### 使用方法

```groovy
dependencies {
    // 热更新核心库（推�?- 包含完整功能�?
    implementation 'io.github.706412584:update:1.2.9'
}
```

---

## 下次发布

更新版本号后运行�?

```bash
publish-maven.bat
```

�?

```bash
cd maven-central
publish.bat
```

