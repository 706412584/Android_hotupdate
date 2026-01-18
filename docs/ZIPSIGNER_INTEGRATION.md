# ZipSigner 集成完成

## 概述

已成功集成 **kellinwood ZipSigner** 库，实现了对 JKS keystore 的原生支持，同时保持对 BKS 的完全兼容。

## 功能特性

### 1. 自动格式检测
系统会自动检测 keystore 文件格式并选择最佳加载方式：

- **JKS 文件** → 优先尝试 ZipSigner（原生支持）
- **BKS 文件** → 使用 BouncyCastle（推荐格式）
- **PKCS12 文件** → 使用标准 Java 或 BouncyCastle

### 2. 向下兼容
- ✅ 完全兼容现有的 BKS 方案
- ✅ 不影响已有功能
- ✅ 无需修改现有代码

### 3. 签名方案
- **JKS**: 使用 ZipSigner 的 v1 (JAR) 签名
- **BKS**: 使用 apksig 的 v1 + v2 签名
- **验证**: 统一使用 JAR 签名验证

## 使用方法

### 方法 1: 直接使用 JKS（新功能）

1. **配置签名**：
   - 点击「配置签名」
   - 选择 `.jks` 文件
   - 输入密码和别名

2. **生成补丁**：
   - 勾选「APK 签名验证」
   - 点击生成补丁
   - 系统自动使用 ZipSigner 签名

3. **日志输出**：
```
I/PatchSigner: 检测到 JKS 文件，尝试使用 ZipSigner...
I/PatchSigner: 使用 ZipSigner 对 JKS 进行签名
D/ZipSignerHelper: 使用 ZipSigner 签名: patch.zip
I/ZipSignerHelper: ✓ ZipSigner 签名成功
I/PatchSigner: ✓ 补丁签名成功 (via ZipSigner)
```

### 方法 2: 使用 BKS（推荐）

1. **转换 JKS 为 BKS**（一次性操作）：
```bash
keytool -importkeystore \
  -srckeystore app.jks \
  -destkeystore app.bks \
  -srcstoretype JKS \
  -deststoretype BKS \
  -provider org.bouncycastle.jce.provider.BouncyCastleProvider \
  -providerpath /path/to/bcprov.jar
```

2. **使用 BKS 文件**：
   - 配置时选择 `.bks` 文件
   - 系统自动使用 BouncyCastle + apksig

## 技术实现

### 架构设计

```
PatchSigner.loadKeyStore()
    ├─ 检测文件扩展名
    ├─ .jks → 尝试 ZipSigner
    │   └─ 失败 → 标记 "JKS_NEEDS_ZIPSIGNER"
    ├─ .bks → 使用 BouncyCastle
    └─ .p12 → 使用 PKCS12

PatchSigner.signPatch()
    ├─ 如果 lastError == "JKS_NEEDS_ZIPSIGNER"
    │   └─ 调用 ZipSignerHelper.signZipWithJks()
    └─ 否则使用 apksig 签名
```

### 关键类

1. **ZipSignerHelper.java**
   - 封装 ZipSigner 库
   - 提供 JKS 签名功能
   - 检查库可用性

2. **PatchSigner.java**
   - 统一的签名入口
   - 自动选择签名方式
   - 支持多种 keystore 格式

3. **kellinwood ZipSigner**
   - 第三方库（`update/libs/zipsigner.jar`）
   - 原生支持 JKS 格式
   - 提供 v1 (JAR) 签名

## 依赖配置

### update/build.gradle
```gradle
dependencies {
    // BouncyCastle for BKS support
    implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
    implementation 'org.bouncycastle:bcpkix-jdk18on:1.77'
    
    // ZipSigner for native JKS support (fallback)
    implementation files('libs/zipsigner.jar')
    
    // apksig for APK signing and verification
    implementation 'com.github.MuntashirAkon:apksig-android:4.4.0'
}
```

## 签名验证

### 验证流程
1. **JAR 签名验证** - 使用 `JarFile` 验证 v1 签名
2. **证书提取** - 从 JAR 签名中提取 X509 证书
3. **签名匹配** - 比较补丁证书和应用证书的公钥

### 兼容性
- ✅ JKS 签名的补丁可以正常验证
- ✅ BKS 签名的补丁可以正常验证
- ✅ 两种方式签名的补丁都与应用签名匹配

## 优势对比

| 特性 | JKS + ZipSigner | BKS + apksig |
|------|----------------|--------------|
| 用户体验 | ⭐⭐⭐⭐⭐ 直接使用 | ⭐⭐⭐⭐ 需要转换 |
| 签名方案 | v1 (JAR) | v1 + v2 |
| Android 兼容 | ⭐⭐⭐⭐ 第三方库 | ⭐⭐⭐⭐⭐ 官方格式 |
| 维护性 | ⭐⭐⭐ 第三方库 | ⭐⭐⭐⭐⭐ 官方库 |
| 性能 | ⭐⭐⭐⭐ 快速 | ⭐⭐⭐⭐⭐ 更快 |

## 推荐使用场景

### 使用 JKS + ZipSigner
- 已有 JKS 文件，不想转换
- 快速测试和开发
- 临时使用

### 使用 BKS + apksig（推荐）
- 生产环境
- 长期维护的项目
- 需要 v2 签名的场景
- 追求最佳性能

## 测试验证

### 测试步骤
1. ✅ 使用 JKS 文件配置签名
2. ✅ 生成补丁并签名
3. ✅ 验证补丁签名
4. ✅ 验证签名与应用匹配
5. ✅ 应用补丁成功

### 测试结果
```
✓ JKS keystore 加载成功
✓ ZipSigner 签名成功
✓ JAR 签名验证通过
✓ 补丁签名与应用签名匹配
✓ 补丁应用成功
```

## 故障排除

### 问题 1: ZipSigner 库不可用
**症状**: 日志显示 "✗ ZipSigner 库不可用"

**解决方案**:
1. 检查 `update/libs/zipsigner.jar` 是否存在
2. 检查 `update/build.gradle` 中的依赖配置
3. 清理并重新编译: `./gradlew clean :app:assembleDebug`

### 问题 2: JKS 签名失败
**症状**: "ZipSigner 签名失败"

**解决方案**:
1. 检查 keystore 密码是否正确
2. 检查密钥别名是否正确
3. 尝试转换为 BKS 格式

### 问题 3: 签名验证失败
**症状**: "补丁签名与应用签名不匹配"

**解决方案**:
1. 确保使用与应用相同的 keystore
2. 确保密钥别名正确
3. 检查应用是否使用了不同的签名

## 文件清单

### 新增文件
- `update/libs/zipsigner.jar` - ZipSigner 库
- `update/src/main/java/com/orange/update/ZipSignerHelper.java` - 包装类
- `docs/ZIPSIGNER_INTEGRATION.md` - 本文档
- `docs/ZIPSIGNER_JKS_SUPPORT.md` - 技术分析文档

### 修改文件
- `update/build.gradle` - 添加 ZipSigner 依赖
- `update/src/main/java/com/orange/update/PatchSigner.java` - 集成 ZipSigner

## 总结

✅ **集成完成**: ZipSigner 已成功集成，支持 JKS 原生签名

✅ **向下兼容**: 完全兼容现有 BKS 方案，不影响已有功能

✅ **用户友好**: 用户可以直接使用 JKS 文件，无需手动转换

✅ **灵活选择**: 支持 JKS 和 BKS 两种方案，用户可根据需求选择

🎉 **推荐**: 生产环境建议使用 BKS 格式，开发测试可直接使用 JKS
