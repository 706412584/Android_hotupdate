# Android Hot Update Patch Tool

[����](README.md) | English

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)

A complete Android hot update solution that supports **DEX, Resources, SO libraries, and Assets** hot updates without reinstalling the APK.

## ? Core Features

- ?? **True Hot Update** - No app restart required, code takes effect immediately
- ?? **Full Support** - DEX, Resources, SO libraries, and Assets fully supported
- ?? **High Performance** - Native engine acceleration, 2-3x faster patch generation
- ?? **On-Device Generation** - Generate patches directly on Android devices
- ??? **Multiple Methods** - Command line, Gradle plugin, Android SDK
- ?? **Secure & Reliable** - Signature verification and encryption to prevent tampering
- ?? **Good Compatibility** - Supports Android 5.0+ (API 21+)
- ? **Auto Fallback** - Automatically uses Java engine when Native is unavailable
- ?? **Version Detection** - ?? Auto-detect APK version changes, clear old patches on upgrade
- ?? **Format Detection** - ?? Auto-validate patch format and package name to prevent misuse
- ?? **Management Dashboard** - ?? Web admin dashboard with canary release and analytics

## ?? Documentation Navigation

### Quick Start
- **[Quick Start](#-quick-start)** - Get started in 5 minutes
- **[Demo Download](https://github.com/706412584/Android_hotupdate/releases/tag/demo)** - Download demo APK

### Core Features
- **[Singleton Pattern Usage](docs/SINGLETON_PATTERN.md)** - ?? Elegant singleton pattern API
- **[Version Detection and Auto-Clear](docs/VERSION_CHECK.md)** - ?? APK version detection, auto-clear patches on upgrade
- **[Patch Format Detection](docs/PATCH_FORMAT_VALIDATION.md)** - ?? Auto-validate patch format and package name
- **[Security Mechanisms](#-security-mechanisms)** - Signature verification and encryption

### Architecture and Design
- **[Architecture Design](docs/ARCHITECTURE.md)** - ?? Overall architecture, module design, core algorithms
- **[Performance Optimization](docs/PERFORMANCE.md)** - ?? Patch generation/application optimization, memory optimization
- **[Troubleshooting](docs/TROUBLESHOOTING.md)** - ?? Common issue diagnosis and solutions

### Detailed Documentation
- **[Detailed Usage](docs/USAGE.md)** - Complete API documentation
- **[FAQ](docs/FAQ.md)** - Troubleshooting guide
- **[Patch Format](docs/PATCH_FORMAT.md)** - Patch package structure
- **[resources.arsc Compression Issue](docs/RESOURCES_ARSC_COMPRESSION_EXPLAINED.md)** - ?? Why can't compress?

### Development Guide
- **[Project Completion Assessment](docs/PROJECT_STATUS_AND_ROADMAP.md)** - ?? Current status and roadmap
- **[Server Enhancement](docs/SERVER_ENHANCEMENT.md)** - ?? Push notifications, CDN integration
- **[Release Guide](JITPACK_RELEASE.md)** - How to release new versions

## ?? Branch Description

- **main** - Main branch with latest stable code
- **server** - Server deployment branch, Zeabur auto-deploy source (push to this branch only when updating server)

## ?? Online Service

We provide a free patch hosting service for testing and learning:

- **Service URL**: https://android-hotupdateserver.zeabur.app
- **Admin Dashboard**: https://android-hotupdateserver.zeabur.app/dashboard

**Features**:
- ? App Management - Create and manage multiple applications
- ? Patch Upload - Manual upload or automatic generation
- ? Version Control - Manage patches for different versions
- ? Update Check - RESTful API for client update checks
- ? Download Statistics - View patch downloads and usage

**Server Dashboard Preview**:

![Server Dashboard](docs/server-dashboard.png)

**How to Use**:
1. Click "?? Server Test" button in Demo App
2. Login with default credentials to test API features
3. Refer to [Server API Documentation](patch-server/README.md) for integration
4. Check [Client Integration Example](app/src/main/java/com/orange/update/ServerTestActivity.java) to learn how to call APIs

> ?? **Note**: This service is for testing and learning only, with storage and bandwidth limits. Not recommended for production use. For production, please refer to [Deployment Guide](patch-server/README.md#deployment) to deploy your own server.

## ?? Quick Start

### 1. Add Dependencies

**Method 1: Using Maven Central (Recommended)**

```groovy
dependencies {
    // Hot update core library
    implementation 'io.github.706412584:update:1.4.0'
    
    // If you need to generate patches on device (optional, not recommended, use official demo APK instead):
    implementation 'io.github.706412584:patch-generator-android:1.4.0'
    
}
```

**Maven Central Components List:**

| Component | Maven Coordinates | Description |
|-----------|------------------|-------------|
| **update** | `io.github.706412584:update:1.4.0` | Hot update core library, required |
| **patch-generator-android** | `io.github.706412584:patch-generator-android:1.4.0` | On-device patch generation |
| **patch-native** | `io.github.706412584:patch-native:1.4.0` | Native high-performance engine (AAR) |
| **patch-core** | `io.github.706412584:patch-core:1.4.0` | Core patch engine |
| **patch-cli** | [Download JAR](https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.4.0/patch-cli-1.4.0-all.jar) | Command-line tool (standalone) |
| **patch-gradle-plugin** | `io.github.706412584:patch-gradle-plugin:1.4.0` | Gradle plugin for build integration |

> ?? **Tips**:
> - The `update` library includes basic functionality, most cases only need this dependency
> - `patch-native` provides 2-3x performance boost, auto-fallback to Java engine when unavailable
> - `patch-cli` is a standalone command-line tool, no need to add to project dependencies

### 2. Generate Patch

**Method 1: Using Command-Line Tool (Recommended for CI/CD)**

```bash
# Download patch-cli
wget https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.4.0/patch-cli-1.4.0-all.jar

# Generate signed patch
java -jar patch-cli-1.4.0-all.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip \
  --keystore keystore.jks \
  --keystore-password <password> \
  --key-alias <alias> \
  --key-password <password>
```

**Method 2: Using Android SDK (On-Device Generation)**

<details open>
<summary><b>Java Example</b></summary>

```java
AndroidPatchGenerator generator = new AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .callback(new SimpleAndroidGeneratorCallback() {
        @Override
        public void onComplete(PatchResult result) {
            if (result.isSuccess()) {
                Log.i(TAG, "Patch generated successfully");
            }
        }
        
        @Override
        public void onError(String message) {
            Log.e(TAG, "Patch generation failed: " + message);
        }
    })
    .build();

generator.generate();
```
</details>

<details open>
<summary><b>Kotlin Example</b></summary>

```kotlin
val generator = AndroidPatchGenerator.Builder(context)
    .baseApk(baseApkFile)
    .newApk(newApkFile)
    .output(patchFile)
    .callback(object : SimpleAndroidGeneratorCallback() {
        override fun onComplete(result: PatchResult) {
            if (result.isSuccess) {
                Log.i(TAG, "Patch generated successfully")
            }
        }
        
        override fun onError(message: String) {
            Log.e(TAG, "Patch generation failed: $message")
        }
    })
    .build()

generator.generate()
```
</details>

**Method 3: Using Gradle Plugin (Build-Time Generation)**

```groovy
// Method A: Via Gradle Plugin Portal (Recommended)
plugins {
    id 'com.android.application'
    id 'io.github.706412584.patch' version '1.4.0'
}

// Method B: Via Maven Central
buildscript {
    dependencies {
        classpath 'io.github.706412584:patch-gradle-plugin:1.4.0'
    }
}
apply plugin: 'io.github.706412584.patch'

// Configure patch generation
patchGenerator {
    baselineApk = file("baseline/app-v1.0.apk")  // Baseline APK (previous release)
    outputDir = file("build/patch")              // Patch output directory
    
    signing {
        keystoreFile = file("keystore.jks")      // Keystore file
        keystorePassword = "password"            // Keystore password
        keyAlias = "alias"                       // Key alias
        keyPassword = "password"                 // Key password
    }
    
    engine = "auto"        // Engine type: auto, java, native (default: auto)
    patchMode = "full_dex" // Patch mode: full_dex, bsdiff (default: full_dex)
    enabled = true         // Enable plugin (default: true)
}

// Generate patch
// ./gradlew generateDebugPatch   # Generate debug patch
// ./gradlew generateReleasePatch # Generate release patch
```

> 📖 **Documentation**: [patch-cli Guide](patch-cli/README.md) | [Gradle Plugin Guide](patch-gradle-plugin/README.md)
> 
> ✅ **Verified**: Plugin published to [Maven Central](https://central.sonatype.com/artifact/io.github.706412584/patch-gradle-plugin) and [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.706412584.patch), tested and working

### 3. Apply Patch

**Recommended: Singleton Pattern (Elegant API)**

<details open>
<summary><b>Java Example</b></summary>

```java
public class MyApplication extends Application {
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // Initialize HotUpdateHelper (call once)
        HotUpdateHelper.init(this);
        
        // Load applied patch (if any)
        HotUpdateHelper.getInstance().loadPatchIfNeeded();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Apply patch when needed
        File patchFile = new File("/path/to/patch.zip");
        HotUpdateHelper.getInstance().applyPatch(patchFile, new HotUpdateHelper.Callback() {
            @Override
            public void onProgress(int percent, String message) {
                Log.d(TAG, "Progress: " + percent + "% - " + message);
            }
            
            @Override
            public void onSuccess(PatchResult result) {
                Log.i(TAG, "Hot update successful!");
                // Restart app or notify user
            }
            
            @Override
            public void onError(String message) {
                Log.e(TAG, "Hot update failed: " + message);
            }
        });
    }
}
```
</details>

<details open>
<summary><b>Kotlin Example</b></summary>

```kotlin
class MyApplication : Application() {
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        
        // Initialize HotUpdateHelper (call once)
        HotUpdateHelper.init(this)
        
        // Load applied patch (if any)
        HotUpdateHelper.getInstance().loadPatchIfNeeded()
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Apply patch when needed
        val patchFile = File("/path/to/patch.zip")
        HotUpdateHelper.getInstance().applyPatch(patchFile, object : HotUpdateHelper.Callback {
            override fun onProgress(percent: Int, message: String) {
                Log.d(TAG, "Progress: $percent% - $message")
            }
            
            override fun onSuccess(result: PatchResult) {
                Log.i(TAG, "Hot update successful!")
                // Restart app or notify user
            }
            
            override fun onError(message: String) {
                Log.e(TAG, "Hot update failed: $message")
            }
        })
    }
}
```
</details>

**Alternative: Traditional Method**

```java
public class MyApplication extends Application {
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // Load applied patch
        PatchApplier applier = new PatchApplier(this);
        applier.loadAppliedPatch();
    }
}
```

## 🛡️ Security Mechanisms

### 1. Signature Verification

Verify the patch signature and match it against the app signature to prevent tampering:

```java
// Signature verification is enabled by DEFAULT — no call required.
// To disable it (NOT recommended), use helper.setRequireSignature(false)
// which logs a prominent warning.
HotUpdateHelper helper = new HotUpdateHelper(context);
// helper.setRequireSignature(true);  // default, can be omitted
```

**How it works:**
- Patches are signed with a full JAR signature (`META-INF/MANIFEST.MF`, `.SF`, `.RSA`)
- On apply, the patch is verified with standard JAR signature verification
  (`java.util.jar.JarFile`), then the patch certificate's public key is compared
  against the app's APK signing certificate
- Signature verification is **on by default**; missing, mismatched, or invalid
  signatures cause the patch to be rejected
- Patches are ZIP files, not APKs, so **apksig / `ApkVerifier` is NOT used**

### 2. AES Encryption

Encrypt patches with AES-256 to protect sensitive code.

**Using PatchEncryptor (On-Device Generation):**

```java
// Encrypt patch during generation (using PatchEncryptor)
PatchEncryptor encryptor = new PatchEncryptor(context);
String password = "your_secure_password";
File encryptedPatch = encryptor.encryptPatchWithPassword(patchFile, password);

// Decrypt when applying patch (using SecurityManager)
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatchWithAesPassword(encryptedPatch, password, callback);
```

> 💡 **Architecture Note**:
> - **PatchEncryptor** (`patch-generator-android` module): Used for encrypting patches during generation
> - **SecurityManager** (`update` module): Used for decrypting patches during application
> - Both classes use the same encryption algorithm (AES-256-GCM + PBKDF2) to ensure compatibility

**Using CLI Tool (Server-Side Generation):**

```bash
# Generate encrypted patch
java -jar patch-cli.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip.enc \
  --encrypt \
  --password "your-secret-password"
```

### 3. ZIP Password Protection

Add password protection to patch ZIP files:

```bash
# Generate password-protected patch
java -jar patch-cli.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip \
  --zip-password "your-zip-password"
```

## ?? Patch Package Structure

```
patch.zip
������ patch.json          # Patch metadata
������ classes.dex         # DEX patch (if any)
������ classes2.dex        # Additional DEX (if any)
������ res/                # Resource patch (if any)
��   ������ ...
������ lib/                # SO library patch (if any)
��   ������ ...
������ assets/             # Assets patch (if any)
��   ������ ...
������ META-INF/           # Signature files (if signed)
    ������ MANIFEST.MF
    ������ CERT.SF
    ������ CERT.RSA
```

## ?? Rollback Support

```java
// Clear current patch (rollback to original APK)
HotUpdateHelper.getInstance().clearPatch(new HotUpdateHelper.Callback() {
    @Override
    public void onSuccess(PatchResult result) {
        Log.i(TAG, "Patch cleared, app will use original code");
        // Restart app
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "Failed to clear patch: " + message);
    }
});
```

## ?? Use Cases

- **Bug Fixes** - Fix critical bugs without app store review
- **Feature Toggles** - Enable/disable features remotely
- **A/B Testing** - Test different implementations
- **Emergency Updates** - Deploy urgent fixes immediately
- **Resource Updates** - Update UI, strings, images without reinstall

## ?? Limitations

1. **Cannot modify AndroidManifest.xml** - Manifest changes require full APK update
2. **Cannot add new Activities/Services** - New components require manifest changes
3. **Cannot modify Application class** - Application class loaded before patch
4. **Obfuscation compatibility** - Keep same ProGuard rules between versions

## ?? Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## ?? License

```
Copyright 2024 Android Hot Update

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## ?? Acknowledgments

- [Tinker](https://github.com/Tencent/tinker) - Inspiration for resource patching
- [AndFix](https://github.com/alibaba/AndFix) - Inspiration for hot fix mechanism
- [Robust](https://github.com/Meituan-Dianping/Robust) - Inspiration for patch generation

## ?? Contact

- **Issues**: [GitHub Issues](https://github.com/706412584/Android_hotupdate/issues)
- **Email**: 706412584@qq.com

---

**Star ? this repo if you find it helpful!**

