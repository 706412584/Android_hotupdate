# ZipSigner 库 JKS 支持方案

## 概述

发现了 **kellinwood ZipSigner** 库，这是一个著名的 Android ZIP/APK 签名工具，**原生支持 JKS 和 BKS 格式**。

## 库信息

- **名称**: kellinwood ZipSigner
- **作者**: Ken Ellinwood
- **GitHub**: https://github.com/kellinwood/zip-signer
- **功能**: 
  - 支持 JKS 和 BKS keystore
  - 支持 v1 (JAR) 签名
  - 可以创建 keystore、密钥和自签名证书
  - 提供 MD5、SHA1 指纹和 SHA1 key hash

## 关键类

从 `sign_extracted/classes.jar` 中发现的关键类：

```
kellinwood/security/zipsigner/ZipSigner.class
kellinwood/security/zipsigner/optional/JksKeyStore.class
kellinwood/security/zipsigner/optional/CustomKeySigner.class
kellinwood/security/zipsigner/optional/JKS.class
kellinwood/security/zipsigner/optional/KeyStoreFileManager.class
kellinwood/security/zipsigner/optional/SignatureBlockGenerator.class
```

## 优势

### vs 当前 BKS 方案

| 特性 | BKS 方案 | ZipSigner 方案 |
|------|---------|---------------|
| JKS 支持 | ❌ 需要转换 | ✅ 原生支持 |
| 用户体验 | 需要手动转换 | 直接使用 JKS |
| 依赖 | apksig + BouncyCastle | ZipSigner (单一库) |
| 签名方案 | v1 + v2 | v1 (JAR) |
| 代码复杂度 | 中等 | 可能更简单 |

### vs apksig

- **ZipSigner** 专门为 ZIP/JAR 签名设计，不需要 AndroidManifest.xml
- **apksig** 主要为 APK 设计，需要特殊处理才能用于 ZIP

## 集成方案

### 方案 A: 作为备选方案（推荐）

保留当前的 BKS + apksig 方案，添加 ZipSigner 作为 JKS 的备选：

```java
// 在 PatchSigner.loadKeyStore() 中
if (keystoreFile.getName().endsWith(".jks")) {
    // 尝试使用 ZipSigner 的 JKS 支持
    return loadJksWithZipSigner(keystoreFile, password);
} else if (keystoreFile.getName().endsWith(".bks")) {
    // 使用 BouncyCastle BKS
    return loadBksKeyStore(keystoreFile, password);
}
```

### 方案 B: 完全替换

用 ZipSigner 替换当前的签名实现：

```java
// 使用 ZipSigner 进行签名
ZipSigner zipSigner = new ZipSigner();
zipSigner.setKeymode("custom");
zipSigner.signZip(inputFile, outputFile, keystoreFile, 
                  keystorePassword, keyAlias, keyPassword);
```

## 下一步

1. **反编译分析**: 使用 jadx 或 jd-gui 反编译 `classes.jar`，查看 JKS 实现细节
2. **API 测试**: 创建测试代码，验证 ZipSigner 的 JKS 支持
3. **集成决策**: 
   - 如果 ZipSigner 的 JKS 支持稳定，可以作为备选方案
   - 如果当前 BKS 方案已经满足需求，可以保持现状
4. **文档更新**: 在用户文档中说明两种方案的选择

## 参考资料

- [ZipSigner APK Mirror](https://www.apkmirror.com/apk/ken-ellinwood/zipsigner/)
- [ZipSigner Archive.org](https://archive.org/details/kellinwood.zipsigner)
- [GitHub - kellinwood/zip-signer](https://github.com/kellinwood/zip-signer)

## 当前状态

- ✅ BKS 方案已完成并测试通过
- ✅ ZipSigner JAR 文件已提取到 `sign_extracted/classes.jar`
- ⏳ 待分析 ZipSigner 的 JKS 实现
- ⏳ 待决定是否集成

## 建议

**保持当前 BKS 方案**，原因：
1. BKS 方案已经完全工作
2. BouncyCastle 是官方推荐的 Android keystore 格式
3. 用户只需一次性转换 JKS → BKS
4. apksig 是 Google 官方库，更新和维护更好

**ZipSigner 作为参考**：
- 了解其 JKS 实现原理
- 如果未来需要更好的 JKS 支持，可以借鉴其实现
