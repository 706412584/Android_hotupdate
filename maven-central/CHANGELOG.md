# Maven Central 发布记录

## v1.5.3 (2026-10-16)

### 新增（资源热更新的安全网）

- ✨ **资源 ID 稳定性检测**（生成侧）：补丁整体替换 `resources.arsc`，而资源 ID
  被内联进 dex 与二进制 XML。新版增删资源导致既有 ID 重排时，未被差分 dex
  覆盖的旧代码会**静默**引用错误资源（实测 ACS r11→r12：10308 个共有资源里
  981 个漂移，表现为界面显示无关文字、无任何报错）。
  现在 `PatchGenerator` 在生成含资源的补丁时自动比对 base/new 的资源映射，
  判定结果写入 `patch.json` 的 `resourceIdsStable` 字段——对齐写 `true`，
  漂移/删除/解析失败写 `false`（失败按不稳定处理，偏保守）。
  消费侧据此拒绝未对齐的资源补丁。
  - `ArscParser`：纯 JVM 解析 `resources.arsc`（UTF-8/UTF-16 字符串池、
    u32/OFFSET16/SPARSE 三种条目编码），经 aapt2 真实产物（含 `--stable-ids`
    与 `--enable-compact-entries`）及真实 APK 对验证。
- ✨ **R 字段内联告警**（`RFieldAnalyzer`）：dex 层检测 R 类静态 int 字段的
  `ACC_FINAL`——带 final 即资源 ID 被编译期内联（旧版 AGP 或
  `android.nonFinalResIds=false`），是漂移的放大器。检测结果写入 `patch.json`
  的 `rFieldsFinal`，CLI 与 Gradle 插件输出明确告警与修复指引。
- 📄 文档：`docs/RESOURCE_ID_STABILITY.md`（原理、检测、修复路径）。

### 兼容性说明

- `PatchInfo` 新增 `resourceIdsStable` / `rFieldsFinal` 两个**可选**字段，
  向后兼容：旧消费侧读不到字段时行为不变（缺失安全）。

### 发布的模块

1. **update:1.5.3**
2. **patch-core:1.5.3**
3. **patch-native:1.5.3**
4. **patch-generator-android:1.5.3**
5. **patch-cli:1.5.3**
6. **patch-gradle-plugin:1.5.3**

> 注：1.5.2 仅 patch-core / patch-cli 上架（其余 4 个模块当时未随 bundle 提交），
> 本版本一并补齐全部 6 个模块。

## v1.5.2 (2026-10-13)

### 修复

- 🐛 **dex 版本 038 解析/写入**（`patch-core`）：支持 `INVOKE_POLYMORPHIC` 的
  解析与写入，兼容 Android 14+ 的 dex 038 产物。
- 🐛 **`--exclude` 排除噪音资产**：补丁生成时可排除每次构建都变的资产
  （如内嵌版本号的 jar），实测 ACS r11→r12 补丁从 20.5MB 降到 4.37MB。

## v1.5.1 (2026-10-12)

### 修复（重要）

- 🐛 **补丁 dex 连续编号**：修复补丁中的类全部无法加载的问题。
  此前生成侧直接沿用 base APK 的 dex 条目名（如 `classes8.dex`、
  `classes12.dex`），而 ART 只按 `classes.dex` → `classes2.dex` → …
  连续查找，遇到跳号即停止，导致补丁 dex 中的类全部失效。
  现在 `PatchGenerator` 会先按自然序排序 dex，再重编号为连续序列，
  并保证同一对 APK 每次生成的映射一致。
- 🐛 **确定性 dex 顺序**：`compareDex` 原使用 `HashSet`，遍历顺序不稳定，
  导致补丁 dex 命名不可复现。改为按 dex 自然序排序。

### 说明

- 本次修复仅涉及 `patch-core`，三条产出路径（patch-cli / Gradle 插件 /
  Android SDK）共用，均受益。
- 若你此前生成过跳号的补丁包，需用本版本重新生成。

### 发布的模块

1. **update:1.5.1**
2. **patch-core:1.5.1**
3. **patch-native:1.5.1**
4. **patch-generator-android:1.5.1**
5. **patch-cli:1.5.1**
6. **patch-gradle-plugin:1.5.1**

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

