# Patch CLI 命令行工具

补丁生成器的命令行工具，可在 PC/服务器上独立运行。

## 功能特性

- **独立运行**: 无需 Android 环境，可在任何 Java 环境运行
- **完整功能**: 支持所有补丁生成功能
- **签名支持**: 支持使用 JKS/PKCS12 密钥库对补丁进行签名
- **灵活配置**: 通过命令行参数配置各种选项
- **进度显示**: 控制台实时显示生成进度

## 安装

### 方式一：从 Maven Central 下载（推荐）

```bash
# 下载最新版本的 fat JAR
wget https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.2/patch-cli-1.3.2-all.jar

# 或使用 curl
curl -O https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.2/patch-cli-1.3.2-all.jar
```

### 方式二：从 Release 页面下载

从 [GitHub Releases](https://github.com/706412584/Android_hotupdate/releases) 下载 `patch-cli-all.jar`。

### 方式三：自己编译

```bash
./gradlew :patch-cli:fatJar
```

生成的 JAR 文件位于 `patch-cli/build/libs/patch-cli-1.3.2-all.jar`。

## 使用方法

### 基本用法

```bash
java -jar patch-cli.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch-v1.1.zip \
  --keystore keystore.jks \
  --keystore-password password \
  --key-alias patch \
  --key-password password
```

**注意**: 建议在生产环境中始终使用签名，以确保补丁的安全性和完整性。

### 完整参数

```bash
java -jar patch-cli.jar \
  --base <基准APK路径> \
  --new <新版本APK路径> \
  --output <输出补丁路径> \
  --keystore <keystore文件路径> \
  --keystore-password <keystore密码> \
  --key-alias <密钥别名> \
  --key-password <密钥密码> \
  --engine <引擎类型: auto|java|native> \
  --mode <补丁模式: full_dex|bsdiff> \
  --verbose
```

### 参数说明

| 参数 | 简写 | 说明 | 必填 |
|------|------|------|------|
| `--base` | `-b` | 基准 APK 文件路径 | 是 |
| `--new` | `-n` | 新版本 APK 文件路径 | 是 |
| `--output` | `-o` | 输出补丁文件路径 | 是 |
| `--keystore` | `-k` | Keystore 文件路径 | 否 |
| `--keystore-password` | `-kp` | Keystore 密码 | 否 |
| `--key-alias` | `-ka` | 密钥别名 | 否 |
| `--key-password` | `-p` | 密钥密码 | 否 |
| `--engine` | `-e` | 引擎类型 (auto/java/native) | 否 |
| `--mode` | `-m` | 补丁模式 (full_dex/bsdiff) | 否 |
| `--verbose` | `-v` | 显示详细日志 | 否 |
| `--help` | `-h` | 显示帮助信息 | 否 |

### 示例

#### 生成带签名的补丁（推荐）

```bash
java -jar patch-cli.jar \
  --base old.apk \
  --new new.apk \
  --output patch.zip \
  --keystore app.jks \
  --keystore-password 123456 \
  --key-alias myapp \
  --key-password 123456
```

#### 使用 Java 引擎生成补丁

```bash
java -jar patch-cli.jar \
  --base old.apk \
  --new new.apk \
  --output patch.zip \
  --engine java
```

#### 使用 BsDiff 模式

```bash
java -jar patch-cli.jar \
  --base old.apk \
  --new new.apk \
  --output patch.zip \
  --mode bsdiff
```

#### 不签名生成补丁（仅用于测试）

```bash
java -jar patch-cli.jar \
  --base old.apk \
  --new new.apk \
  --output patch.zip
```

**警告**: 未签名的补丁在启用签名验证的生产环境中会被拒绝。

#### 显示详细日志

```bash
java -jar patch-cli.jar \
  --base old.apk \
  --new new.apk \
  --output patch.zip \
  --verbose
```

## 输出示例

```
Patch Generator CLI v1.0.0
==========================

Base APK: app-v1.0.apk (15.2 MB)
New APK:  app-v1.1.apk (15.5 MB)
Output:   patch-v1.1.patch

[10%] Parsing base APK...
[20%] Parsing new APK...
[30%] Comparing dex files...
[50%] Comparing resources...
[70%] Packing patch...
[90%] Signing patch...
[100%] Complete!

Patch Generation Summary
------------------------
Status:           SUCCESS
Patch Size:       256 KB
Compression:      98.3%
Generation Time:  2.5s

Modified Classes: 5
Added Classes:    2
Deleted Classes:  0
Modified Resources: 3
Added Resources:  1
```

## 退出码

| 退出码 | 说明 |
|--------|------|
| 0 | 成功 |
| 1 | 参数错误 |
| 2 | 文件未找到 |
| 3 | 生成失败 |
| 4 | 签名失败 |

## 系统要求

- Java 11 或更高版本
- 支持 Windows、macOS、Linux

## 许可证

Apache License 2.0
