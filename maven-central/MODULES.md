# 发布的模块

## patch-core

核心补丁库，提供补丁生成和应用的基础算法。

### Maven 坐标

```groovy
implementation 'io.github.706412584:patch-core:1.3.0'
```

### 包含内容

- 补丁生成算法
- 文件差异计算
- 二进制差分算法

### 文件类型

- JAR - Java 库文件
- Sources JAR - 源代码
- Javadoc JAR - API 文档

---

## patch-generator-android

Android 补丁生成器，专门为 Android 应用提供补丁生成功能。

### Maven 坐标

```groovy
implementation 'io.github.706412584:patch-generator-android:1.3.0'
```

### 包含内容

- Android 特定的补丁生成
- APK 差异分析
- 资源文件处理
- Native 库集成

### 依赖关系

```groovy
dependencies {
    api 'io.github.706412584:patch-core:1.3.0'
    api 'io.github.706412584:patch-native:1.3.0'
}
```

### 文件类型

- AAR - Android 库文件
- Sources JAR - 源代码
- Javadoc JAR - API 文档

---

## update

**热更新核心库**，提供完整的热更新功能实现。

### Maven 坐标

```groovy
implementation 'io.github.706412584:update:1.3.0'
```

### 包含内容

- **HotUpdateHelper** - 热更新辅助类（推荐使用）
- **UpdateManager** - 热更新管理器
- **PatchApplier** - 补丁应用器
- **PatchDownloader** - 补丁下载器
- **SecurityManager** - 签名验证和加密
- **DexPatcher** - DEX 文件补丁
- **SoPatcher** - Native 库补丁
- **ResourcePatcher** - 资源文件补丁
- **VersionChecker** - 版本检查
- **PatchStorage** - 补丁存储管理

### 核心功能

1. **简单易用的 API** - HotUpdateHelper 提供最简单的使用方式
2. **补丁下载** - 从服务器下载补丁文件
3. **签名验证** - 验证补丁文件的签名
4. **补丁应用** - 应用补丁到应用
5. **版本管理** - 管理补丁版本
6. **回滚支持** - 支持补丁回滚

### 使用示例

```java
// 最简单的使用方式
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

### 文件类型

- AAR - Android 库文件
- Sources JAR - 源代码
- Javadoc JAR - API 文档

---

## 使用建议

### 仅需要补丁生成（开发工具）

如果你只需要生成补丁文件：

```groovy
implementation 'io.github.706412584:patch-core:1.3.0'
implementation 'io.github.706412584:patch-generator-android:1.3.0'
```

### Android 应用热更新（推荐）

如果你在开发 Android 应用并需要完整的热更新功能：

```groovy
implementation 'io.github.706412584:update:1.3.0'
```

这是最常用的场景，包含了所有热更新需要的功能。

---

## 版本兼容性

- **最低 Android SDK**: 21 (Android 5.0)
- **目标 Android SDK**: 36
- **Java 版本**: 11+
- **Gradle 版本**: 7.0+

---

## 查看详情

- **Central Portal**: https://central.sonatype.com/namespace/io.github.706412584
- **Maven Central**: https://repo1.maven.org/maven2/io/github/706412584/
- **GitHub**: https://github.com/706412584/Android_hotupdate
