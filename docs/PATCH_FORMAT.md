# 补丁包格式说明

本文档详细说明补丁包的内部结构和格式规范。

## 目录

- [补丁包概述](#补丁包概述)
- [文件结构](#文件结构)
- [元数据格式](#元数据格式)
- [DEX 文件格式](#dex-文件格式)
- [资源文件格式](#资源文件格式)
- [SO 库格式](#so-库格式)
- [Assets 文件格式](#assets-文件格式)
- [压缩规范](#压缩规范)

## 补丁包概述

补丁包是一个标准的 ZIP 文件，包含了从旧版本到新版本的所有变更内容。

**基本信息：**
- **格式**: ZIP 压缩包
- **扩展名**: `.zip`
- **编码**: UTF-8
- **压缩方法**: STORED (不压缩) 或 DEFLATED (压缩)

## 文件结构

```
patch.zip
├── patch.json              # 元数据文件（必需）
├── dex/                    # DEX 文件目录
│   ├── classes.dex.patch   # 主 DEX 差分文件
│   ├── classes2.dex.patch  # 第二个 DEX 差分文件
│   └── ...
├── res/                    # 资源文件目录
│   ├── resources.arsc      # 资源索引表（STORED）
│   ├── layout/             # 布局文件
│   │   └── activity_main.xml
│   ├── drawable/           # 图片资源
│   │   └── icon.png
│   └── values/             # 值资源
│       └── strings.xml
├── lib/                    # SO 库目录
│   ├── armeabi-v7a/        # 32位 ARM
│   │   └── libnative.so
│   ├── arm64-v8a/          # 64位 ARM
│   │   └── libnative.so
│   ├── x86/                # 32位 x86
│   │   └── libnative.so
│   └── x86_64/             # 64位 x86
│       └── libnative.so
└── assets/                 # Assets 文件目录
    ├── config.txt
    └── data/
        └── database.db
```

## 元数据格式

`patch.json` 文件包含补丁的元数据信息。

### 完整示例

```json
{
  "version": "1.0",
  "patchVersion": "v1.2.5",
  "baseVersion": "1.0.0",
  "packageName": "com.example.app",
  "timestamp": 1705478400000,
  "changes": {
    "dex": {
      "modified": ["classes.dex"],
      "added": [],
      "deleted": []
    },
    "resources": {
      "modified": ["res/layout/activity_main.xml", "res/values/strings.xml"],
      "added": ["res/drawable/new_icon.png"],
      "deleted": []
    },
    "so": {
      "modified": ["lib/armeabi-v7a/libnative.so"],
      "added": [],
      "deleted": []
    },
    "assets": {
      "modified": ["assets/config.txt"],
      "added": [],
      "deleted": []
    }
  },
  "statistics": {
    "totalSize": 1048576,
    "dexSize": 524288,
    "resourceSize": 262144,
    "soSize": 131072,
    "assetsSize": 131072
  }
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `version` | String | 是 | 补丁格式版本，当前为 "1.0" |
| `patchVersion` | String | 是 | 新版本号 |
| `baseVersion` | String | 是 | 基准版本号 |
| `packageName` | String | 是 | 应用包名 |
| `timestamp` | Long | 是 | 生成时间戳（毫秒） |
| `changes` | Object | 是 | 变更详情 |
| `statistics` | Object | 否 | 统计信息 |

## DEX 文件格式

### 目录结构

```
dex/
├── classes.dex.patch       # 主 DEX 差分文件
├── classes2.dex.patch      # 第二个 DEX 差分文件
├── classes3.dex.patch      # 第三个 DEX 差分文件
└── ...
```

### 差分算法

使用 **BsDiff** 算法生成差分文件：
- **输入**: 旧 DEX 文件 + 新 DEX 文件
- **输出**: `.patch` 差分文件
- **优点**: 压缩率高，通常只有原文件的 10-30%

### 应用流程

1. 从原 APK 提取旧 DEX 文件
2. 读取补丁中的 `.patch` 文件
3. 使用 BsPatch 算法合并生成新 DEX
4. 加载新 DEX 到 ClassLoader

### 文件命名规则

- `classes.dex.patch` - 对应 `classes.dex`
- `classes2.dex.patch` - 对应 `classes2.dex`
- `classesN.dex.patch` - 对应 `classesN.dex`

## 资源文件格式

### 目录结构

```
res/
├── resources.arsc          # 资源索引表（必需，STORED）
├── layout/                 # 布局文件
│   ├── activity_main.xml
│   └── fragment_home.xml
├── drawable/               # 图片资源
│   ├── icon.png
│   └── background.jpg
├── drawable-hdpi/          # 高密度图片
├── drawable-xhdpi/         # 超高密度图片
├── drawable-xxhdpi/        # 超超高密度图片
├── values/                 # 值资源
│   ├── strings.xml
│   ├── colors.xml
│   └── styles.xml
├── mipmap/                 # 应用图标
└── ...
```

### resources.arsc

**关键要求：**
- ⚠️ **必须使用 STORED 压缩方法**（不压缩）
- 包含所有资源的索引信息
- 必须与资源文件保持一致

**为什么必须 STORED？**
- Android 系统直接映射 `resources.arsc` 到内存
- DEFLATED 压缩会导致无法直接访问
- 使用 `ZipFile` 而非 `ZipInputStream` 读取

### 资源文件

- 支持所有 Android 资源类型
- 保持原始目录结构
- 可以使用 DEFLATED 压缩

## SO 库格式

### 目录结构

```
lib/
├── armeabi-v7a/            # 32位 ARM (必需)
│   ├── libnative.so
│   └── libother.so
├── arm64-v8a/              # 64位 ARM (推荐)
│   ├── libnative.so
│   └── libother.so
├── x86/                    # 32位 x86 (可选)
│   ├── libnative.so
│   └── libother.so
└── x86_64/                 # 64位 x86 (可选)
    ├── libnative.so
    └── libother.so
```

### ABI 支持

| ABI | 说明 | 优先级 |
|-----|------|--------|
| `armeabi-v7a` | 32位 ARM，兼容性最好 | 必需 |
| `arm64-v8a` | 64位 ARM，性能最好 | 推荐 |
| `x86` | 32位 x86，模拟器常用 | 可选 |
| `x86_64` | 64位 x86，模拟器常用 | 可选 |

### 加载规则

1. 检测设备 ABI（如 `arm64-v8a`）
2. 优先加载对应 ABI 的 SO
3. 如果不存在，降级到兼容 ABI（如 `armeabi-v7a`）
4. 如果都不存在，使用原 APK 的 SO

### 文件要求

- SO 文件必须是 ELF 格式
- 必须与设备 ABI 匹配
- 建议使用 DEFLATED 压缩

## Assets 文件格式

### 目录结构

```
assets/
├── config.txt              # 配置文件
├── data/                   # 数据目录
│   ├── database.db
│   └── cache.json
├── fonts/                  # 字体文件
│   └── custom.ttf
└── images/                 # 图片文件
    └── splash.png
```

### 文件类型

支持任意文件类型：
- 文本文件 (`.txt`, `.json`, `.xml`)
- 数据库文件 (`.db`, `.sqlite`)
- 字体文件 (`.ttf`, `.otf`)
- 图片文件 (`.png`, `.jpg`, `.webp`)
- 其他二进制文件

### 访问方式

```java
// 通过 AssetManager 访问
AssetManager assets = context.getAssets();
InputStream is = assets.open("config.txt");
```

### 注意事项

- Assets 文件随资源一起加载
- 需要重启应用才能生效
- 保持原始目录结构

## 压缩规范

### 压缩方法

| 文件类型 | 压缩方法 | 原因 |
|---------|---------|------|
| `resources.arsc` | **STORED** | Android 系统要求 |
| DEX 差分文件 | DEFLATED | 减小补丁大小 |
| 资源文件 | DEFLATED | 减小补丁大小 |
| SO 库 | DEFLATED | 减小补丁大小 |
| Assets 文件 | DEFLATED | 减小补丁大小 |

### 压缩级别

- **STORED**: 不压缩，直接存储
- **DEFLATED**: 标准压缩，级别 6-9

### 代码示例

```java
// 使用 zip4j 创建补丁包
ZipParameters parameters = new ZipParameters();

// resources.arsc 必须 STORED
if (fileName.equals("resources.arsc")) {
    parameters.setCompressionMethod(CompressionMethod.STORE);
} else {
    parameters.setCompressionMethod(CompressionMethod.DEFLATE);
    parameters.setCompressionLevel(CompressionLevel.NORMAL);
}

zipFile.addFile(file, parameters);
```

## 补丁验证

### 完整性检查

1. **ZIP 格式验证**
   - 检查是否是有效的 ZIP 文件
   - 验证文件头和中央目录

2. **元数据验证**
   - 检查 `patch.json` 是否存在
   - 验证 JSON 格式是否正确
   - 检查必需字段是否完整

3. **版本验证**
   - 验证 `baseVersion` 是否匹配当前版本
   - 检查 `packageName` 是否匹配

4. **文件验证**
   - 检查声明的文件是否存在
   - 验证文件大小和 MD5

### 安全检查

1. **签名验证**（可选）
   - 验证补丁包签名
   - 防止篡改

2. **MD5 校验**
   - 计算补丁包 MD5
   - 与服务端下发的 MD5 对比

3. **大小限制**
   - 检查补丁包大小是否合理
   - 防止恶意补丁

## 生成工具

### 使用 PatchPacker

```java
PatchPacker packer = new PatchPacker(outputFile);

// 添加元数据
packer.addMetadata(patchInfo);

// 添加 DEX 差分文件
packer.addDexPatch("classes.dex", patchData);

// 添加资源文件（resources.arsc 自动 STORED）
packer.addResource("res/layout/activity_main.xml", data);
packer.addResource("resources.arsc", arscData);

// 添加 SO 库
packer.addSoLib("armeabi-v7a", "libnative.so", soData);

// 添加 Assets 文件
packer.addAsset("config.txt", assetData);

// 完成打包
packer.finish();
```

## 最佳实践

### 补丁大小优化

1. **只包含变更的文件**
   - 不要包含未修改的文件
   - 使用差分算法减小 DEX 大小

2. **资源优化**
   - 压缩图片资源
   - 移除未使用的资源
   - 使用 WebP 格式

3. **SO 库优化**
   - 只包含必需的 ABI
   - 使用 strip 移除符号表
   - 启用压缩

### 兼容性保证

1. **版本检查**
   - 严格验证 `baseVersion`
   - 不允许跨版本应用补丁

2. **ABI 兼容**
   - 提供多个 ABI 的 SO
   - 支持 ABI 降级

3. **资源兼容**
   - 保持资源 ID 一致
   - 不要删除正在使用的资源

---

**返回**: [主文档](../README.md) | [使用文档](USAGE.md) | [常见问题](FAQ.md)
