# Android Hot Update Patch Tool

[中文](README.md) | English

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)


A complete Android hot update solution that supports **DEX, Resources, SO libraries, and Assets** hot updates without reinstalling the APK.

## ✨ Core Features

- 🔥 **True Hot Update** - No app restart required, code takes effect immediately
- 📦 **Full Support** - DEX, Resources, SO libraries, and Assets fully supported
- 🚀 **High Performance** - Native engine acceleration, 2-3x faster patch generation
- 📱 **On-Device Generation** - Generate patches directly on Android devices
- 🛠️ **Multiple Methods** - Command line, Gradle plugin, Android SDK
- 🔒 **Secure & Reliable** - Signature verification and encryption to prevent tampering
- 🎯 **Good Compatibility** - Supports Android 5.0+ (API 21+)
- ⚡ **Auto Fallback** - Automatically uses Java engine when Native is unavailable

## 📚 Documentation

- **[Quick Start](#-quick-start)** - Get started in 5 minutes
- **[Security Mechanisms](#-security-mechanisms)** - Signature verification and encryption
- **[Demo Download](https://github.com/706412584/Android_hotupdate/releases/tag/demo)** - Download demo APK
- **[Detailed Usage](docs/USAGE.md)** - Complete API documentation
- **[FAQ](docs/FAQ.md)** - Troubleshooting guide
- **[Patch Format](docs/PATCH_FORMAT.md)** - Patch package structure
- **[Release Guide](JITPACK_RELEASE.md)** - How to release new versions

## 🌿 Branch Description

- **main** - Main branch with latest stable code
- **server** - Server deployment branch, Zeabur auto-deploy source (push to this branch only when updating server)

## 🌐 Online Service

We provide a free patch hosting service for testing and learning:

- **Service URL**: https://android-hotupdateserver.zeabur.app
- **Admin Dashboard**: https://android-hotupdateserver.zeabur.app/dashboard

**Features**:
- ✅ App Management - Create and manage multiple applications
- ✅ Patch Upload - Manual upload or automatic generation
- ✅ Version Control - Manage patches for different versions
- ✅ Update Check - RESTful API for client update checks
- ✅ Download Statistics - View patch downloads and usage

**Server Dashboard Preview**:

![Server Dashboard](docs/server-dashboard.png)

**How to Use**:
1. Click "🌐 Server Test" button in Demo App
2. Login with default credentials to test API features
3. Refer to [Server API Documentation](patch-server/README.md) for integration
4. Check [Client Integration Example](app/src/main/java/com/orange/update/ServerTestActivity.java) to learn how to call APIs

> ⚠️ **Note**: This service is for testing and learning only, with storage and bandwidth limits. Not recommended for production use. For production, please refer to [Deployment Guide](patch-server/README.md#deployment) to deploy your own server.

## 🚀 Quick Start

### 1. Add Dependencies

**Method 1: Using Maven Central (Recommended)**

```groovy
dependencies {
    // Hot update core library (includes full functionality)
    implementation 'io.github.706412584:update:1.3.4'
    
    // If you need to generate patches on device, add:
    implementation 'io.github.706412584:patch-generator-android:1.3.4'
    
    // If you need Native high-performance engine (optional, auto-fallback)
    implementation 'io.github.706412584:patch-native:1.3.4'
    
    // If you need core patch engine (usually not required separately)
    implementation 'io.github.706412584:patch-core:1.3.4'
}
```

**Maven Central Components List:**

| Component | Maven Coordinates | Description |
|-----------|------------------|-------------|
| **update** | `io.github.706412584:update:1.3.4` | Hot update core library, required |
| **patch-generator-android** | `io.github.706412584:patch-generator-android:1.3.4` | On-device patch generation |
| **patch-native** | `io.github.706412584:patch-native:1.3.4` | Native high-performance engine (AAR) |
| **patch-core** | `io.github.706412584:patch-core:1.3.4` | Core patch engine |
| **patch-cli** | [Download JAR](https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.4/patch-cli-1.3.4-all.jar) | Command-line tool (standalone) |

> 💡 **Tips**:
> - The `update` library includes basic functionality, most cases only need this dependency
> - `patch-native` provides 2-3x performance boost, auto-fallback to Java engine when unavailable
> - `patch-cli` is a standalone command-line tool, no need to add to project dependencies

### 2. Generate Patch

**Method 1: Using Command-Line Tool (Recommended for CI/CD)**

```bash
# Download patch-cli
wget https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.4/patch-cli-1.3.4-all.jar

# Generate signed patch
java -jar patch-cli-1.3.4-all.jar \
  --base app-v1.0.apk \
  --new app-v1.1.apk \
  --output patch.zip \
  --keystore keystore.jks \
  --keystore-password <password> \
  --key-alias <alias> \
  --key-password <password>
```

**Method 2: Using Android SDK (On-Device Generation)**

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
    })
    .build();

generator.generateInBackground();
```

**Method 3: Using Gradle Plugin (Build-Time Generation)**

```gradle
patchGenerator {
    baselineApk = file("baseline/app-v1.0.apk")
    outputDir = file("build/patch")
    
    signing {
        keystoreFile = file("keystore.jks")
        keystorePassword = "password"
        keyAlias = "alias"
        keyPassword = "password"
    }
}
```

> 📖 **Detailed Documentation**: [patch-cli Usage Guide](patch-cli/README.md)

### 3. Apply Patch

Using `HotUpdateHelper` class (Recommended):

```java
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatch(patchFile, new HotUpdateHelper.Callback() {
    @Override
    public void onProgress(int percent, String message) {
        Log.d(TAG, "Progress: " + percent + "% - " + message);
    }
    
    @Override
    public void onSuccess(HotUpdateHelper.PatchResult result) {
        Log.i(TAG, "Hot update successful!");
        Log.i(TAG, "Patch version: " + result.patchVersion);
        // DEX and SO take effect immediately, resource updates require app restart
    }
    
    @Override
    public void onError(String message) {
        Log.e(TAG, "Hot update failed: " + message);
    }
});
```

> 💡 **More Application Methods**:
> - [Using PatchApplier](docs/USAGE.md#using-patchapplier) - More flexible control
> - [Using Low-level API](docs/USAGE.md#using-low-level-api) - DexPatcher, SoPatcher, ResourcePatcher
> - [Using UpdateManager](docs/USAGE.md#using-updatemanager) - Server-side update flow

### 4. Integration in Application

```java
public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // Load applied patches (must be called in attachBaseContext)
        HotUpdateHelper helper = new HotUpdateHelper(base);
        helper.loadAppliedPatch();
    }
}
```

> 📖 **Detailed Configuration**: [Application Integration Guide](docs/USAGE.md#application-integration)

## 🛡️ Security Mechanisms

To prevent patch tampering and theft, the system provides **APK Signature Verification** and **AES Encryption** security mechanisms.

### APK Signature Verification (Recommended)

Sign patches with the same signing key as the APK to ensure the patch source is trusted and unmodified.

**Generate Signed Patch:**

```groovy
// build.gradle
patchGenerator {
    baselineApk = file("baseline/app-v1.2.apk")
    outputDir = file("build/patch")
    
    // Configure signing (use the same signing key as APK)
    signing {
        keystoreFile = file("keystore/app.jks")
        keystorePassword = "your_keystore_password"
        keyAlias = "your_key_alias"
        keyPassword = "your_key_password"
    }
}
```

**Enable Signature Verification:**

```java
HotUpdateHelper helper = new HotUpdateHelper(context);

// Require patch signature (recommended for production)
helper.setRequireSignature(true);

// Signature will be automatically verified when applying patch
helper.applyPatch(patchFile, callback);
```

**Signature Verification Principle:**
- Patch generation uses JarSigner to generate complete JAR signature (META-INF/MANIFEST.MF, .SF, .RSA)
- When applying patch, apksig library verifies if signature matches APK signature
- If signature doesn't match or is deleted, patch will be automatically rejected and cleared

### AES Encryption Protection

Use AES-256-GCM to encrypt patch content, preventing patch theft or reverse engineering.

**Encrypt with Custom Password (Recommended):**

```java
// Encrypt patch
SecurityManager securityManager = new SecurityManager(context);
String password = "your_secure_password";
File encryptedPatch = securityManager.encryptPatchWithPassword(patchFile, password);

// Apply encrypted patch
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.applyPatchWithAesPassword(encryptedPatch, password, callback);
```

**Use ZIP Password Protection (Best Compatibility):**

```java
// Apply patch with ZIP password
HotUpdateHelper helper = new HotUpdateHelper(context);
String zipPassword = "your_zip_password";
helper.applyPatchWithZipPassword(patchFile, zipPassword, callback);
```

> 💡 **More Encryption Methods**:
> - [Android KeyStore Encryption](docs/USAGE.md#using-keystore-encryption) - Device-bound, most secure
> - [Combine Signature and Encryption](docs/USAGE.md#combine-signature-and-encryption) - Highest security level
> - [Security Best Practices](docs/USAGE.md#security-best-practices) - Production environment configuration

### Anti-Tampering Protection (New in v1.3.2)

The system automatically provides patch integrity verification and auto-recovery:

- ✅ **SHA-256 Hash Verification**: Calculate and save hash when applying patch
- ✅ **Startup Verification**: Verify patch integrity on every app startup
- ✅ **Auto Recovery**: Automatically recover tampered patches from encrypted storage
- ✅ **Tampering Counter**: Allow up to 3 tampering attempts, auto-clear after exceeding

**No additional configuration required**, anti-tampering is automatically integrated into `PatchApplication` and `HotUpdateHelper`.

> 📖 **Detailed Documentation**: [Anti-Tampering Protection](docs/SECURITY.md)

## 🎯 Demo Application

**Download Demo APK:** https://github.com/706412584/Android_hotupdate/releases/tag/demo

Or build it yourself:

```bash
./gradlew :app:installDebug
```

**Demo Features:**
1. 📱 On-device patch generation
2. 🔒 Configure security policies (signature verification, encryption)
3. 🔐 Support multiple encryption methods (KeyStore, custom password, ZIP password)
4. 🛡️ Automatic anti-tampering protection
5. 🔄 Patch rollback functionality

## 🔄 Patch Rollback

```java
HotUpdateHelper helper = new HotUpdateHelper(context);
helper.clearPatch();
Toast.makeText(context, "Patch cleared, please restart app", Toast.LENGTH_LONG).show();

// Auto restart app
Intent intent = context.getPackageManager()
    .getLaunchIntentForPackage(context.getPackageName());
if (intent != null) {
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    android.os.Process.killProcess(android.os.Process.myPid());
}
```

## 📁 Project Structure

```
├── patch-core/              # Core library - Patch generation engine
├── patch-native/            # Native library - C/C++ high-performance engine
├── patch-generator-android/ # Android SDK - On-device generation
├── patch-cli/               # Command line tool - PC/Server side (Standalone download available)
├── patch-gradle-plugin/     # Gradle plugin - Build integration
├── update/                  # Hot update SDK - Patch application
└── app/                     # Demo application
```

| Module | Description | Documentation |
|--------|-------------|---------------|
| **patch-generator-android** | Android SDK, on-device patch generation | [README](patch-generator-android/README.md) |
| **update** | Hot update SDK, patch application and loading | - |
| **patch-core** | Core engine, APK parsing, diff comparison | [README](patch-core/README.md) |
| **patch-native** | Native SO library, BsDiff algorithm | [README](patch-native/README.md) |
| **patch-cli** | Command line tool, standalone execution, [Direct download](https://repo1.maven.org/maven2/io/github/706412584/patch-cli/1.3.4/patch-cli-1.3.4-all.jar) | [README](patch-cli/README.md) |
| **patch-gradle-plugin** | Gradle plugin, build integration | [README](patch-gradle-plugin/README.md) |

## 💡 Hot Update Principles

- **DEX Hot Update**: Modify ClassLoader's dexElements via reflection, takes effect immediately
- **Resource Hot Update**: Replace AssetManager, requires Activity restart
- **SO Library Hot Update**: Modify nativeLibraryPathElements, takes effect immediately
- **Assets Hot Update**: Loaded with resources, requires restart

For detailed principles, see [Usage Documentation](docs/USAGE.md#hot-update-principles)

## ❓ FAQ

### Q: Which Android versions are supported?
**A:** Supports Android 5.0+ (API 21+), recommended Android 7.0+ (API 24+)

### Q: Can I hot update AndroidManifest.xml?
**A:** No, this is an Android mechanism limitation, requires APK reinstallation

### Q: Why do resource updates require restart?
**A:** Resources need to be reloaded into AssetManager, requires Activity restart to see new UI

### Q: How to rollback a patch?
**A:** Call `helper.clearPatch()` then restart the app

### Q: How to enable signature verification?
**A:** Use `helper.setRequireSignature(true)` to enable signature verification. Patch generation must use the same signing key as APK. See [Security Mechanisms](#-security-mechanisms) section

### Q: Can I skip signature verification in debug mode?
**A:** Yes, use `helper.setRequireSignature(false)` to disable signature verification for development testing. Recommended to enable in production

### Q: Does it support hardened APKs (360 hardening, etc.)?
**A:** Partial support, recommend generating patches before hardening and thorough testing after. See [FAQ - Hardening Related](docs/FAQ.md#hardening-related)

For more questions, see [FAQ Documentation](docs/FAQ.md)

## 📋 System Requirements

### Development Environment
- Java 11+
- Android SDK 21+
- Gradle 8.9+
- NDK 27.0+ (only for compiling Native modules)

### Runtime Environment
- Minimum version: Android 5.0 (API 21)
- Recommended version: Android 7.0+ (API 24+)
- Target version: Android 14 (API 34)

## 🔧 Build

```bash
# Build all modules
./gradlew build

# Build and install Demo
./gradlew :app:installDebug

# Run tests
./gradlew test
```

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork this repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

```
Copyright 2026 Orange Update

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

## 🙏 Acknowledgments

This project references the following excellent open source projects:
- [Tinker](https://github.com/Tencent/tinker) - Tencent's Android hot fix solution
- [Robust](https://github.com/Meituan-Dianping/Robust) - Meituan's hot fix solution

## 📞 Contact

- **GitHub Issues**: [Submit Issue](https://github.com/706412584/Android_hotupdate/issues)
- **Email**: 706412584@qq.com

---

**⭐ If this project helps you, please give it a Star!**
