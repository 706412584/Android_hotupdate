# Update 模块依赖库

本目录包含 `update` 模块所需的所有第三方依赖库（不包括 Android 标准库）。

## 依赖列表

### 1. ZIP4J - ZIP 文件处理
- **文件**: `zip4j-2.11.5.jar`
- **坐标**: `net.lingala.zip4j:zip4j:2.11.5`
- **大小**: 205.10 KB
- **用途**: 支持密码保护的 ZIP 文件处理
- **许可证**: Apache License 2.0
- **官网**: https://github.com/srikanth-lingala/zip4j

### 2. apksig-android - APK 签名验证
- **文件**: `apksig-android-4.4.0.aar`
- **坐标**: `com.github.MuntashirAkon:apksig-android:4.4.0`
- **大小**: 421.63 KB
- **用途**: APK 签名和验证（Android 移植版）
- **许可证**: Apache License 2.0
- **官网**: https://github.com/MuntashirAkon/apksig-android

### 3. BouncyCastle Provider - 加密库
- **文件**: `bcprov-jdk18on-1.77.jar`
- **坐标**: `org.bouncycastle:bcprov-jdk18on:1.77`
- **大小**: 8176.13 KB
- **用途**: JKS keystore 支持、加密算法
- **许可证**: MIT License
- **官网**: https://www.bouncycastle.org/

### 4. BouncyCastle PKIX - 公钥基础设施
- **文件**: `bcpkix-jdk18on-1.77.jar`
- **坐标**: `org.bouncycastle:bcpkix-jdk18on:1.77`
- **大小**: 1087.54 KB
- **用途**: X.509 证书处理、签名验证
- **许可证**: MIT License
- **官网**: https://www.bouncycastle.org/

### 5. BouncyCastle Util - 工具库
- **文件**: `bcutil-jdk18on-1.77.jar`
- **坐标**: `org.bouncycastle:bcutil-jdk18on:1.77`
- **大小**: 665.04 KB
- **用途**: BouncyCastle 通用工具（bcpkix 的传递依赖）
- **许可证**: MIT License
- **官网**: https://www.bouncycastle.org/

## 总大小

约 10.5 MB

## 使用方式

### 方式一：通过 Gradle 依赖（推荐）

```groovy
dependencies {
    implementation 'net.lingala.zip4j:zip4j:2.11.5'
    implementation 'com.github.MuntashirAkon:apksig-android:4.4.0'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.77'
    implementation 'org.bouncycastle:bcpkix-jdk18on:1.77'
}
```

### 方式二：本地 JAR/AAR 文件

如果无法访问 Maven 仓库，可以使用本目录中的文件：

```groovy
dependencies {
    implementation files('test_assets/dependencies/zip4j-2.11.5.jar')
    implementation files('test_assets/dependencies/apksig-android-4.4.0.aar')
    implementation files('test_assets/dependencies/bcprov-jdk18on-1.77.jar')
    implementation files('test_assets/dependencies/bcpkix-jdk18on-1.77.jar')
    implementation files('test_assets/dependencies/bcutil-jdk18on-1.77.jar')
}
```

## 更新依赖

如果需要更新依赖版本，请执行：

```bash
./gradlew :update:downloadDependencies
```

然后将 `update/test_assets/dependencies/` 中的文件移动到本目录。

## 注意事项

1. **不包含的依赖**：
   - `androidx.appcompat:appcompat` - Android 标准库
   - `com.google.android.material:material` - Android Material 组件
   - 测试依赖（JUnit、Mockito 等）

2. **本地依赖**：
   - `zipsigner.jar` - 位于 `update/libs/` 目录
   - `patch-core` - 项目内部模块

3. **许可证合规**：
   - 所有依赖均为开源软件
   - 使用 Apache License 2.0 或 MIT License
   - 可以免费用于商业项目

## 依赖关系图

```
update
├── zip4j (ZIP 文件处理)
├── apksig-android (APK 签名验证)
├── bcprov-jdk18on (加密算法)
└── bcpkix-jdk18on (证书处理)
    └── bcutil-jdk18on (工具库，传递依赖)
```

## 版本历史

- **2026-01-20**: 初始版本
  - zip4j: 2.11.5
  - apksig-android: 4.4.0
  - BouncyCastle: 1.77

## 相关文档

- [update 模块 README](../update/README.md)
- [Maven Central 发布指南](../JITPACK_RELEASE.md)
- [项目主文档](../README.md)
