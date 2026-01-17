# 预编译 Native 库

本目录包含预编译的 Native SO 库，用于在 JitPack 等无法编译 Native 代码的环境中使用。

## 📦 包含的库

```
jniLibs/
├── arm64-v8a/
│   └── libpatchengine.so      (338 KB) - 64位 ARM
├── armeabi-v7a/
│   └── libpatchengine.so      (262 KB) - 32位 ARM
├── x86/
│   └── libpatchengine.so      (294 KB) - 32位 x86
└── x86_64/
    └── libpatchengine.so      (327 KB) - 64位 x86
```

## 🔧 功能

这些 SO 库提供高性能的二进制差分算法：

- **BsDiff/BsPatch** - 二进制差分和补丁应用
- **MD5/SHA256** - 文件哈希计算
- **进度回调** - 实时进度报告
- **取消机制** - 支持中断操作

## 📊 性能对比

| 操作 | Native 引擎 | Java 引擎 | 性能提升 |
|------|------------|----------|---------|
| DEX 差分 | 快 | 较慢 | 2-3倍 |
| 资源处理 | 快 | 较慢 | 1.5-2倍 |
| 哈希计算 | 快 | 较慢 | 1.5倍 |

## 🚀 使用方式

### 方式一：通过 JitPack（自动）

从 JitPack 依赖时，会自动包含预编译的 SO 库：

```groovy
dependencies {
    implementation 'com.github.706412584.Android_hotupdate:patch-native:v1.2.4'
}
```

### 方式二：本地编译

如果需要自己编译 Native 库：

```bash
# 编译 Release 版本
./gradlew :patch-native:assembleRelease

# SO 库位置
patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/
```

### 方式三：手动集成

如果只需要 SO 库，可以直接复制到项目：

```
app/src/main/jniLibs/
├── arm64-v8a/
│   └── libpatchengine.so
├── armeabi-v7a/
│   └── libpatchengine.so
├── x86/
│   └── libpatchengine.so
└── x86_64/
    └── libpatchengine.so
```

## 🔍 验证

检查 Native 库是否可用：

```java
if (NativePatchEngine.isAvailable()) {
    Log.i(TAG, "Native engine is available");
    Log.i(TAG, "Version: " + new NativePatchEngine().getVersion());
} else {
    Log.w(TAG, "Native engine not available, using Java engine");
}
```

## 📝 编译信息

- **编译工具**: NDK 27.0.12077973
- **CMake 版本**: 3.22.1
- **C++ 标准**: C++17
- **STL**: c++_shared
- **优化级别**: RelWithDebInfo（Release with Debug Info）
- **支持 ABI**: arm64-v8a, armeabi-v7a, x86, x86_64

## 🔄 更新预编译库

如果需要更新预编译的 SO 库：

```bash
# 1. 编译 Release 版本
./gradlew :patch-native:assembleRelease

# 2. 复制 SO 库到预编译目录
# Windows PowerShell
Copy-Item "patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/arm64-v8a/libpatchengine.so" "patch-native/prebuilt/jniLibs/arm64-v8a/" -Force
Copy-Item "patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/armeabi-v7a/libpatchengine.so" "patch-native/prebuilt/jniLibs/armeabi-v7a/" -Force
Copy-Item "patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/x86/libpatchengine.so" "patch-native/prebuilt/jniLibs/x86/" -Force
Copy-Item "patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/x86_64/libpatchengine.so" "patch-native/prebuilt/jniLibs/x86_64/" -Force

# Linux/Mac
cp patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/arm64-v8a/libpatchengine.so patch-native/prebuilt/jniLibs/arm64-v8a/
cp patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/armeabi-v7a/libpatchengine.so patch-native/prebuilt/jniLibs/armeabi-v7a/
cp patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/x86/libpatchengine.so patch-native/prebuilt/jniLibs/x86/
cp patch-native/build/intermediates/cxx/RelWithDebInfo/*/obj/x86_64/libpatchengine.so patch-native/prebuilt/jniLibs/x86_64/

# 3. 提交到 Git
git add patch-native/prebuilt/jniLibs/
git commit -m "chore: 更新预编译Native库"
```

## ⚠️ 注意事项

1. **ABI 兼容性**
   - arm64-v8a 设备会优先使用 arm64-v8a 库
   - 如果不存在，会降级到 armeabi-v7a
   - x86/x86_64 主要用于模拟器

2. **库大小**
   - 总大小约 1.2 MB（4个ABI）
   - 如果只需要支持 ARM，可以删除 x86/x86_64

3. **自动降级**
   - 如果 Native 库加载失败，会自动降级到 Java 引擎
   - Java 引擎功能完整，只是速度较慢

## 📄 许可证

与主项目相同，采用 Apache License 2.0。

---

**返回**: [patch-native 主文档](../README.md)
