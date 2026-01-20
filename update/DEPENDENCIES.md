# Update模块依赖说明

## 核心依赖（必需）

update模块只包含1个必需的运行时依赖：

```gradle
implementation 'io.github.706412584:update:1.3.6'
```

自动引入的依赖：
- `net.lingala.zip4j:zip4j:2.11.5` (~0.5MB) - ZIP文件处理和加密支持

**总大小：约0.5MB**

---

## 签名验证说明

update模块使用**标准Java的JarFile API**进行签名验证，无需额外依赖。

### 支持的签名格式
- ✅ **JAR签名（v1签名方案）** - 标准Java支持，无需额外依赖
- ✅ 所有使用JAR签名的补丁文件

### 不支持的功能
- ❌ 补丁签名生成（请使用patch-generator-android模块）
- ❌ BKS keystore加载（签名验证不需要keystore）

---

## 使用场景

### 场景1：应用补丁（推荐）

大多数应用只需要应用补丁功能：

```gradle
// 最小依赖
implementation 'io.github.706412584:update:1.3.6'
```

**功能：**
- ✅ 应用Dex补丁
- ✅ 应用资源补丁
- ✅ 应用Native库补丁
- ✅ 补丁加密/解密
- ✅ JAR签名验证（标准Java）

---

### 场景2：生成补丁

如果需要生成补丁，请使用patch-generator-android模块：

```gradle
implementation 'io.github.706412584:patch-generator-android:1.3.6'
```

**功能：**
- ✅ 生成Dex差分补丁
- ✅ 生成资源补丁
- ✅ 生成Native库补丁
- ✅ 补丁签名（支持JKS/BKS/PKCS12）

---

## 依赖大小对比

| 配置 | 依赖数量 | 总大小 | 适用场景 |
|------|---------|--------|---------|
| update模块 | 1个 | ~0.5MB | 应用补丁 + 签名验证 |
| patch-generator-android | 3个+ | ~2MB+ | 生成补丁 + 签名 |

---

## 常见问题

### Q: update模块可以验证签名吗？

**A:** 可以！update模块使用标准Java的JarFile API验证JAR签名（v1签名方案），无需额外依赖。

### Q: 我需要添加BouncyCastle吗？

**A:** 不需要。update模块的签名验证功能使用标准Java API，不需要BouncyCastle。

### Q: 如何生成带签名的补丁？

**A:** 使用patch-generator-android模块生成补丁时配置SigningConfig即可。update模块只负责验证签名，不负责生成签名。

### Q: 支持哪些签名格式？

**A:** update模块支持所有使用JAR签名（v1签名方案）的补丁文件。这是最通用的签名格式，兼容所有Android版本。

---

## 版本历史

### v1.3.6
- ✨ 将BouncyCastle改为可选依赖
- 🗑️ 移除apksig-android依赖（签名功能移至patch-generator-android）
- 🎯 核心依赖从4个减少到1个
- 📦 最小配置体积减少约2.6MB（从3.1MB降至0.5MB）
- ✅ 使用标准Java JAR签名验证，支持JKS/PKCS12

### v1.3.5
- 包含4个必需依赖（包括BouncyCastle和apksig-android）
