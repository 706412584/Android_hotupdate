# 常见问题

本文档收集了使用过程中的常见问题和解决方案。

## 目录

- [补丁生成相关](#补丁生成相关)
- [补丁应用相关](#补丁应用相关)
- [兼容性相关](#兼容性相关)
- [性能相关](#性能相关)
- [安全相关](#安全相关)
- [开发相关](#开发相关)

## 补丁生成相关

### Q: 生成补丁时提示"权限被拒绝"？
**A:** 需要授予存储权限：
- Android 11+ (API 30+): 需要「所有文件访问权限」
- Android 10 及以下: 需要「存储权限」
- 在应用设置中手动授予权限

### Q: 生成补丁失败，提示"APK 解析失败"？
**A:** 检查以下几点：
1. APK 文件是否完整（未损坏）
2. APK 文件是否可读
3. 两个 APK 的包名是否相同
4. 存储空间是否充足

### Q: 生成的补丁文件很大？
**A:** 这是正常的，因为：
- 补丁包含完整的资源文件（Tinker 方式）
- 如果修改了大量资源，补丁会较大
- DEX 文件使用差分算法，通常较小
- 可以通过减少资源变更来减小补丁大小

### Q: 生成补丁时出现 .tmp 文件？
**A:** 这是临时文件，正常情况下会自动删除：
- 如果生成失败，可能残留 .tmp 文件
- 可以手动删除这些文件
- 不影响正常使用

## 补丁应用相关

### Q: 应用补丁后代码没有生效？
**A:** 检查以下几点：
1. 确认补丁应用成功（查看日志）
2. 确认修改的类已经被加载到补丁 DEX
3. 某些类可能被 ART 提前编译，需要重启应用
4. 查看「系统信息」确认 DEX 注入状态

### Q: 应用补丁后资源没有更新？
**A:** 资源更新需要重启应用：
- DEX 和 SO 立即生效
- 资源需要重启 Activity 或应用
- 应用补丁后会提示是否重启

### Q: 应用补丁后闪退？
**A:** 可能的原因：
1. 补丁与当前版本不匹配
2. 补丁文件损坏
3. 资源文件格式错误（resources.arsc 必须是 STORED）
4. 点击「清除补丁」回滚，然后重新生成

### Q: 如何回滚补丁？
**A:** 两种方式：
1. 使用「清除补丁」按钮，然后重启应用
2. 重新安装原始 APK

## 兼容性相关

### Q: 支持哪些 Android 版本？
**A:** 
- **最低版本**: Android 5.0 (API 21)
- **最高版本**: Android 14 (API 34)
- **推荐版本**: Android 7.0+ (API 24+)

**为什么不支持 Android 4.x？**
- DEX 注入需要 `BaseDexClassLoader` 内部字段（API 21+）
- 资源加载需要特定的 `AssetManager` API（API 21+）
- SO 加载需要 ClassLoader 路径修改（API 21+）

### Q: 在不同 Android 版本上有什么区别？
**A:**
- **Android 5.0-6.0 (API 21-23)**: 标准方案，完全支持
- **Android 7.0-7.1 (API 24-25)**: 需要处理混合编译模式
- **Android 8.0-9.0 (API 26-28)**: 需要处理 ResourcesImpl
- **Android 10+ (API 29+)**: 需要处理非 SDK 接口限制，部分功能受限

### Q: 支持哪些 ABI？
**A:** 支持所有主流 ABI：
- armeabi-v7a (32位 ARM)
- arm64-v8a (64位 ARM)
- x86 (32位 x86)
- x86_64 (64位 x86)

### Q: 在 MIUI 等定制 ROM 上能用吗？
**A:** 可以，已针对定制 ROM 做了优化：
- 处理了 MIUI 的 `MiuiResourcesImpl`
- 清除了 TypedArray 缓存
- 兼容各种定制系统

## 性能相关

### Q: JitPack版本包含Native加速吗？
**A:** 是的！JitPack版本包含预编译的Native SO库：
- ✅ 自动包含4个ABI的SO库（arm64-v8a, armeabi-v7a, x86, x86_64）
- ✅ 补丁生成速度提升2-3倍
- ✅ 如果Native库加载失败，自动降级到Java引擎
- ✅ 无需额外配置，开箱即用

**验证Native引擎是否可用：**
```java
if (NativePatchEngine.isAvailable()) {
    Log.i(TAG, "Native引擎可用，性能最佳");
} else {
    Log.i(TAG, "使用Java引擎，功能完整");
}
```

### Q: Native 引擎和 Java 引擎有什么区别？
**A:**
- **Native 引擎**: 使用 C/C++ 实现，性能更高，速度快 2-3 倍
- **Java 引擎**: 纯 Java 实现，兼容性好，功能完整
- 优先使用 Native 引擎，不可用时自动降级到 Java 引擎

### Q: 生成补丁需要多长时间？
**A:** 取决于 APK 大小和变更量：
- 小型应用 (< 10MB): 5-15 秒
- 中型应用 (10-50MB): 15-60 秒
- 大型应用 (> 50MB): 1-3 分钟
- Native 引擎可以显著加快速度

### Q: 补丁应用需要多长时间？
**A:** 通常很快：
- DEX 注入: < 1 秒
- 资源加载: 1-3 秒
- SO 加载: < 1 秒
- 总计: 2-5 秒

## 安全相关

### Q: 补丁包是否安全？
**A:** 
- 支持签名验证（可选）
- 支持 AES-256-GCM 加密（可选）
- 建议在生产环境启用签名验证
- 敏感内容建议启用加密
- 补丁包应通过 HTTPS 下发
- 可以添加自定义校验逻辑

### Q: 如何防止补丁被篡改？
**A:**
1. 启用签名验证（SHA256withRSA）
2. 使用 HTTPS 传输
3. 验证补丁 MD5
4. 添加服务端校验
5. 组合使用签名和加密（推荐）

### Q: 如何保护补丁内容不被窃取？
**A:** 使用 AES-256-GCM 加密：
```java
SecurityManager securityManager = new SecurityManager(context);
File encryptedPatch = securityManager.encryptPatch(patchFile);
```
- 加密后的补丁文件扩展名为 `.enc`
- 使用 Android KeyStore 存储密钥
- 需要 Android 6.0+ (API 23+)
- 客户端应用补丁时自动解密

### Q: 签名和加密有什么区别？
**A:**
- **签名验证**: 防止补丁被篡改，确保完整性
  - 使用 RSA-2048 + SHA256
  - 公钥打包在 APK 中
  - 私钥只在服务器端使用
  
- **加密保护**: 防止补丁内容被窃取，保护机密性
  - 使用 AES-256-GCM
  - 密钥存储在 Android KeyStore
  - 需要 Android 6.0+

- **推荐做法**: 同时使用签名和加密
  - 先加密补丁（保护内容）
  - 再对加密文件签名（防止篡改）
  - 客户端先验证签名，再解密应用

### Q: 加密补丁需要密码吗？
**A:** 
- 支持两种加密方式：
  1. **Android KeyStore**（默认）：无需密码，密钥与设备绑定
  2. **密码加密**：使用自定义密码，客户端需要相同密码才能解密
- 推荐使用 KeyStore 方案（更安全，无需管理密码）
- 密码加密适合需要跨设备使用的场景

### Q: 如何使用密码加密补丁？
**A:** 使用 `encryptPatchWithPassword` 和 `decryptPatchWithPassword` 方法：

**加密：**
```java
SecurityManager securityManager = new SecurityManager(context);
String password = "your_secure_password";
File encryptedPatch = securityManager.encryptPatchWithPassword(patchFile, password);
```

**解密：**
```java
SecurityManager securityManager = new SecurityManager(context);
String password = getPasswordFromConfig(); // 从配置或安全存储获取
File decryptedPatch = securityManager.decryptPatchWithPassword(encryptedPatch, password);

// 然后应用解密后的补丁
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(decryptedPatch, callback);
```

**注意：**
- 密码作为参数传入，不应该弹窗让用户输入
- Demo 应用的密码输入对话框只是为了演示方便
- 在实际应用中，密码应该从配置文件、服务器或安全存储中获取
- 建议使用强密码（至少 8 位，包含字母数字）

### Q: 密码加密和 KeyStore 加密有什么区别？
**A:**
- **KeyStore 加密**：
  - 密钥存储在 Android KeyStore
  - 密钥与设备绑定，无法导出
  - 无需管理密码
  - 更安全，但不能跨设备使用
  
- **密码加密**：
  - 使用 PBKDF2 从密码派生密钥
  - 可以跨设备使用
  - 需要管理和保护密码
  - 密码强度影响安全性

### Q: 如何使用密码加密补丁？
**A:** 使用 `encryptPatchWithPassword` 和 `decryptPatchWithPassword` 方法：

**加密：**
```java
SecurityManager securityManager = new SecurityManager(context);
String password = "your_secure_password";
File encryptedPatch = securityManager.encryptPatchWithPassword(patchFile, password);
```

**解密：**
```java
SecurityManager securityManager = new SecurityManager(context);
String password = getPasswordFromConfig(); // 从配置或安全存储获取
File decryptedPatch = securityManager.decryptPatchWithPassword(encryptedPatch, password);

// 然后应用解密后的补丁
RealHotUpdate hotUpdate = new RealHotUpdate(context);
hotUpdate.applyPatch(decryptedPatch, callback);
```

**注意：**
- 密码作为参数传入，不应该弹窗让用户输入
- Demo 应用的密码输入对话框只是为了演示方便
- 在实际应用中，密码应该从配置文件、服务器或安全存储中获取
- 建议使用强密码（至少 8 位，包含字母数字）

### Q: 密码加密和 KeyStore 加密有什么区别？
**A:**
- **KeyStore 加密**：
  - 密钥存储在 Android KeyStore
  - 密钥与设备绑定，无法导出
  - 无需管理密码
  - 更安全，但不能跨设备使用
  
- **密码加密**：
  - 使用 PBKDF2 从密码派生密钥
  - 可以跨设备使用
  - 需要管理和保护密码
  - 密码强度影响安全性

### Q: 如何配置安全策略？
**A:** 使用 SharedPreferences 配置：
```java
SharedPreferences securityPrefs = context.getSharedPreferences("security_policy", MODE_PRIVATE);
securityPrefs.edit()
    .putBoolean("require_signature", true)  // 强制签名
    .putBoolean("require_encryption", true) // 强制加密
    .apply();
```
- 开启后，只能应用符合策略的补丁
- 不符合策略的补丁会被拒绝
- Demo 应用提供了可视化配置界面

### Q: 安全策略在什么时候检查？
**A:** 在应用补丁时自动检查：
- 检查时机：调用 `applyPatch()` 时
- 检查内容：
  - 如果要求签名，检查是否有 `.sig` 文件
  - 如果要求加密，检查文件是否以 `.enc` 结尾
- 不符合策略会立即拒绝，并显示详细错误信息
- 适合在生产环境中强制执行安全规范

### Q: 签名验证在什么时候进行？
**A:** 自动检测并验证：
- 如果补丁文件旁边有 `.sig` 签名文件，会自动验证
- 验证流程：
  1. 检测到 `.sig` 文件
  2. 读取签名内容
  3. 使用公钥验证签名
  4. 验证通过后继续应用补丁
  5. 验证失败则拒绝应用
- 无需手动调用验证方法
- Demo 应用会显示验证过程和结果

### Q: 加密补丁可以在不同设备上使用吗？
**A:**
- 使用 Android KeyStore 时，密钥与设备绑定
- 每个设备需要独立加密补丁（不推荐）
- 推荐方案：
  - 服务器端不加密，只签名
  - 客户端下载后本地加密存储
  - 或使用密码加密（可跨设备）

### Q: 解密失败怎么办？
**A:** 常见原因：
1. Android 版本低于 6.0（KeyStore 加密）
2. KeyStore 密钥丢失（设备重置）
3. 密码错误（密码加密）
4. 补丁文件损坏
5. 加密算法不匹配

解决方法：
- 检查 Android 版本
- 重新下载补丁
- 确认密码正确
- 使用未加密的补丁
- 查看 Logcat 日志

### Q: 热更新会影响应用稳定性吗？
**A:**
- 正确使用不会影响稳定性
- 建议充分测试补丁
- 提供回滚机制
- 监控补丁应用成功率
- 使用签名验证防止恶意补丁

## 加固相关

### Q: 支持加固的APK吗（如360加固、腾讯乐固）？
**A:** 部分支持，具体取决于加固方案：

**理论上可行的情况：**
- 补丁生成：可以从加固APK生成补丁
- DEX热更新：如果加固方案不修改ClassLoader结构
- 资源热更新：如果加固方案不hook AssetManager

**可能遇到的问题：**
1. **ClassLoader冲突**
   - 加固方案通常会修改ClassLoader
   - 可能与热更新的DEX注入冲突
   
2. **DEX加密**
   - 原始DEX被加密，无法直接提取
   - 需要在加固前生成补丁
   
3. **签名校验**
   - 加固方案可能有额外的签名校验
   - 补丁可能被识别为篡改

**推荐做法：**
1. **在加固前生成补丁**
   - 使用未加固的APK生成补丁
   - 补丁应用到加固后的APK
   
2. **测试验证**
   - 在加固后的APK上充分测试
   - 验证DEX、资源、SO是否正常加载
   
3. **联系加固厂商**
   - 咨询是否支持热更新
   - 获取兼容性指导

**已知兼容性：**
- ✅ 未加固APK：完全支持
- ⚠️ 360加固：需要测试验证
- ⚠️ 腾讯乐固：需要测试验证
- ⚠️ 梆梆加固：需要测试验证

### Q: 如何测试加固APK的热更新？
**A:** 测试步骤：
1. 准备两个版本的未加固APK
2. 使用加固工具加固两个APK
3. 从加固后的APK生成补丁
4. 安装加固后的旧版本APK
5. 应用补丁并测试功能
6. 检查日志确认DEX/资源是否加载成功

### Q: 加固后热更新失败怎么办？
**A:** 排查步骤：
1. 查看Logcat日志（TAG: DexPatcher, ResourcePatcher）
2. 检查是否有ClassLoader相关错误
3. 尝试只更新资源（不更新DEX）
4. 联系加固厂商获取技术支持
5. 考虑使用其他热更新方案（如Tinker）

## 开发相关

### Q: 如何在项目中集成？
**A:** 参考 [使用文档](USAGE.md)，主要步骤：
1. 添加依赖模块
2. 在 Application 中初始化
3. 实现补丁下载和应用逻辑
4. 添加错误处理和回滚机制

### Q: 可以热更新 AndroidManifest.xml 吗？
**A:** 不可以，这是 Tinker 的限制：
- AndroidManifest.xml 在应用安装时解析
- 无法通过热更新修改
- 需要重新安装 APK

### Q: 可以热更新四大组件吗？
**A:**
- **Activity/Service/Receiver**: 可以修改已有组件的代码
- **新增组件**: 不可以，需要在 Manifest 中声明
- **删除组件**: 不可以

### Q: 如何调试热更新？
**A:**
1. 查看 Logcat 日志（TAG: DexPatcher, ResourcePatcher, SoPatcher）
2. 使用 Demo 应用的「系统信息」查看状态
3. 使用 `printDexElements()` 打印 DEX 信息
4. 检查补丁文件是否正确生成

### Q: 如何测试热更新？
**A:**
1. 使用提供的测试 APK（test-apks 目录）
2. 修改代码或资源后生成新 APK
3. 生成补丁并应用
4. 验证修改是否生效
5. 测试回滚功能

### Q: 补丁包可以增量更新吗？
**A:** 
- 当前版本是完整补丁包
- 未来可能支持增量补丁
- 建议服务端做差分下发优化

### Q: 支持多个补丁叠加吗？
**A:**
- 当前不支持
- 每次只能应用一个补丁
- 新补丁会覆盖旧补丁

### Q: 如何获取技术支持？
**A:**
1. 查看本文档的常见问题
2. 查看各模块的 README
3. 提交 GitHub Issue
4. 查看源码和注释

### Q: 可以商用吗？
**A:**
- 采用 Apache License 2.0
- 可以免费商用
- 需要保留版权声明
- 欢迎贡献代码

---

**返回**: [主文档](../README.md) | [使用文档](USAGE.md) | [补丁格式](PATCH_FORMAT.md)
