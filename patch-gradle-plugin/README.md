# Patch Gradle Plugin

Gradle 插件，集成到 Android 构建流程自动生成补丁。

## 功能特性

- **自动集成**: 自动注册补丁生成任务
- **构建变体支持**: 支持 debug 和 release 构建变体
- **DSL 配置**: 通过 Gradle DSL 配置各种选项
- **增量构建**: 支持 Gradle 增量构建

## 安装

### 方式一：使用 JitPack（推荐）

在项目根目录的 `settings.gradle` 中添加 JitPack 仓库：

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

在项目根目录的 `build.gradle` 中添加插件依赖：

```groovy
buildscript {
    dependencies {
        classpath 'com.github.706412584.Android_hotupdate:patch-gradle-plugin:v1.2.4'
    }
}
```

### 方式二：使用本地项目依赖

在项目根目录的 `build.gradle` 中添加：

```groovy
buildscript {
    dependencies {
        classpath project(':patch-gradle-plugin')
    }
}
```

### 在 app 模块的 build.gradle 中应用插件

```groovy
plugins {
    id 'com.android.application'
    id 'com.orange.patch'
}
```

## 配置

```groovy
patchGenerator {
    // 基线 APK 路径
    baselineApk = file("baseline/app-release.apk")
    
    // 输出目录
    outputDir = file("build/patch")
    
    // 签名配置
    signing {
        keystoreFile = file("keystore/patch.jks")
        keystorePassword = "password"
        keyAlias = "patch"
        keyPassword = "password"
    }
    
    // 引擎配置 (auto, java, native)
    engine = "auto"
    
    // 补丁模式 (full_dex, bsdiff)
    patchMode = "full_dex"
    
    // 是否启用
    enabled = true
}
```

## 使用

### 生成补丁

```bash
# 生成 debug 变体的补丁
./gradlew generateDebugPatch

# 生成 release 变体的补丁
./gradlew generateReleasePatch
```

### 查看任务

```bash
./gradlew tasks --group=patch
```

## 配置选项

| 选项 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| `baselineApk` | File | 基线 APK 文件 | 必填 |
| `outputDir` | File | 输出目录 | build/patch |
| `signing.keystoreFile` | File | Keystore 文件 | null |
| `signing.keystorePassword` | String | Keystore 密码 | null |
| `signing.keyAlias` | String | 密钥别名 | null |
| `signing.keyPassword` | String | 密钥密码 | null |
| `engine` | String | 引擎类型 | "auto" |
| `patchMode` | String | 补丁模式 | "full_dex" |
| `enabled` | boolean | 是否启用 | true |

## 完整示例

```groovy
// app/build.gradle
plugins {
    id 'com.android.application'
    id 'com.orange.patch'
}

android {
    // ... Android 配置
}

patchGenerator {
    // 基线 APK（上一个发布版本）
    baselineApk = file("${rootDir}/baseline/app-v1.0-release.apk")
    
    // 补丁输出目录
    outputDir = file("${buildDir}/outputs/patch")
    
    // 签名配置
    signing {
        keystoreFile = file("${rootDir}/keystore/patch-key.jks")
        keystorePassword = System.getenv("PATCH_KEYSTORE_PASSWORD") ?: "default"
        keyAlias = "patch"
        keyPassword = System.getenv("PATCH_KEY_PASSWORD") ?: "default"
    }
    
    // 使用自动引擎选择
    engine = "auto"
    
    // 使用完整 dex 模式
    patchMode = "full_dex"
    
    // 启用插件
    enabled = true
}

// 在 assembleRelease 后自动生成补丁
tasks.named("assembleRelease").configure {
    finalizedBy("generateReleasePatch")
}
```

## CI/CD 集成

### GitHub Actions

```yaml
- name: Generate Patch
  run: ./gradlew generateReleasePatch
  env:
    PATCH_KEYSTORE_PASSWORD: ${{ secrets.PATCH_KEYSTORE_PASSWORD }}
    PATCH_KEY_PASSWORD: ${{ secrets.PATCH_KEY_PASSWORD }}

- name: Upload Patch
  uses: actions/upload-artifact@v3
  with:
    name: patch
    path: app/build/outputs/patch/*.patch
```

### Jenkins

```groovy
stage('Generate Patch') {
    steps {
        withCredentials([
            string(credentialsId: 'patch-keystore-password', variable: 'PATCH_KEYSTORE_PASSWORD'),
            string(credentialsId: 'patch-key-password', variable: 'PATCH_KEY_PASSWORD')
        ]) {
            sh './gradlew generateReleasePatch'
        }
    }
}
```

## 任务依赖

```
generateDebugPatch
└── assembleDebug

generateReleasePatch
└── assembleRelease
```

## 输出

补丁文件将输出到配置的 `outputDir` 目录：

```
build/patch/
├── patch-debug-1.0.1.patch
└── patch-release-1.0.1.patch
```

## 许可证

Apache License 2.0
