# Maven Central 发布

## 快速发布

在项目根目录运行：

```bash
publish-maven.bat
```

或进入 maven-central 目录：

```bash
cd maven-central
publish.bat
```

## 目录结构

```
maven-central/
├── README.md           # 完整发布指南
├── CHANGELOG.md        # 发布记录
├── publish.bat         # 统一发布工具
├── secring.gpg         # GPG 私钥（不在 Git 中）
└── public_key.asc      # GPG 公钥
```

## 当前版本

- **版本**: 1.2.9
- **Group ID**: io.github.706412584
- **Artifacts**:
  - patch-core - 核心补丁算法
  - patch-generator-android - Android 补丁生成器
  - update - 热更新核心库（推荐）

## 使用方法

### Gradle

```groovy
dependencies {
    // 热更新核心库（推荐 - 包含完整功能）
    implementation 'io.github.706412584:update:1.2.9'
}
```

### Maven

```xml
<!-- 热更新核心库（推荐） -->
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>update</artifactId>
    <version>1.2.9</version>
</dependency>
```

## 查看库

- **Central Portal**: 
  - https://central.sonatype.com/artifact/io.github.706412584/patch-core
  - https://central.sonatype.com/artifact/io.github.706412584/patch-generator-android
- **Maven Central**: 
  - https://repo1.maven.org/maven2/io/github/706412584/patch-core/
  - https://repo1.maven.org/maven2/io/github/706412584/patch-generator-android/

## 详细文档

查看 `maven-central/README.md` 了解：
- 完整发布流程
- 配置说明
- 故障排除
- 技术细节
