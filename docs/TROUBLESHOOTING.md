# 故障排查指南

## 🔍 常见问题诊断

### 快速诊断流程

```
遇到问题
    ↓
[查看日志] → 搜索关键错误信息
    ↓
[确定问题类型]
    ├── 补丁生成失败 → 第1节
    ├── 补丁应用失败 → 第2节
    ├── 签名验证失败 → 第3节
    ├── 加密解密失败 → 第4节
    ├── 资源加载失败 → 第5节
    └── 性能问题 → 第6节
```

---

## 1️⃣ 补丁生成失败

### 问题 1.1: APK 解析失败

**错误信息**:
```
[ApkParser] Failed to parse APK: java.util.zip.ZipException: error in opening zip file
```

**可能原因**:
1. APK 文件损坏
2. APK 文件不存在
3. 文件权限不足

**解决方案**:
```bash
# 1. 检查文件是否存在
ls -l app.apk

# 2. 检查文件完整性
unzip -t app.apk

# 3. 检查文件权限
chmod 644 app.apk

# 4. 重新下载或编译 APK
./gradlew assembleRelease
```

---

### 问题 1.2: DEX 比较失败

**错误信息**:
```
[DexDiffer] Failed to compare DEX: java.lang.OutOfMemoryError
```

**可能原因**:
1. DEX 文件过大
2. 内存不足
3. 没有使用流式处理

**解决方案**:
```java
// 1. 增加 JVM 内存
java -Xmx4g -jar patch-cli.jar ...

// 2. 使用 Native 引擎（内存占用更小）
PatchGenerator generator = new PatchGenerator.Builder()
    .engineType(EngineType.NATIVE)
    .build();

// 3. 分批处理 DEX
GeneratorConfig config = GeneratorConfig.builder()
    .enableParallel(false)  // 禁用并行，减少内存
    .build();
```

---

### 问题 1.3: 签名失败

**错误信息**:
```
[JarSigner] Failed to sign patch: java.security.UnrecoverableKeyException: Cannot recover key
```

**可能原因**:
1. 密钥库密码错误
2. 密钥别名错误
3. 密钥密码错误
4. 密钥库文件损坏

**解决方案**:
```bash
# 1. 验证密钥库
keytool -list -v -keystore keystore.jks

# 2. 检查密钥别名
keytool -list -keystore keystore.jks

# 3. 测试密钥访问
keytool -exportcert -alias <alias> -keystore keystore.jks

# 4. 如果密钥库损坏，重新生成
keytool -genkey -v -keystore keystore.jks \
  -alias <alias> -keyalg RSA -keysize 2048 -validity 10000
```

---

### 问题 1.4: resources.arsc 压缩问题

**错误信息**:
```
[JarSigner] resources.arsc is compressed, cannot use mmap
```

**可能原因**:
1. ZipSigner 压缩了 resources.arsc
2. 重新打包时没有保留压缩模式

**解决方案**:
```java
// 确保 resources.arsc 使用 STORE 模式
ZipParameters params = new ZipParameters();
params.setFileNameInZip("resources.arsc");
params.setCompressionMethod(CompressionMethod.STORE);  // 不压缩
zipFile.addFile(resourcesArsc, params);
```

**验证**:
```bash
# 检查 resources.arsc 的压缩方法
unzip -l patch.zip | grep resources.arsc
# 应该显示 "Stored" 而不是 "Defl:N"
```

---

## 2️⃣ 补丁应用失败

### 问题 2.1: 补丁格式验证失败

**错误信息**:
```
[HotUpdateHelper] Invalid patch format: Not a valid ZIP file
```

**可能原因**:
1. 文件不是 ZIP 格式
2. 文件损坏
3. 下载不完整

**解决方案**:
```java
// 1. 检查文件魔数
byte[] header = new byte[4];
try (FileInputStream fis = new FileInputStream(patchFile)) {
    fis.read(header);
}
// ZIP 文件应该是: 50 4B 03 04
System.out.println(Arrays.toString(header));

// 2. 验证文件完整性
String expectedMd5 = patchInfo.getMd5();
String actualMd5 = calculateMd5(patchFile);
if (!expectedMd5.equals(actualMd5)) {
    // 文件损坏，重新下载
    redownloadPatch();
}
```

---

### 问题 2.2: 包名验证失败

**错误信息**:
```
[HotUpdateHelper] Package name mismatch: expected com.example.app, got com.other.app
```

**可能原因**:
1. 使用了其他应用的补丁
2. patch.json 中的包名错误

**解决方案**:
```java
// 1. 检查 patch.json
{
  "packageName": "com.example.app",  // 必须与应用包名一致
  "baseVersion": "1.0.0",
  "targetVersion": "1.1.0"
}

// 2. 生成补丁时自动提取包名
PatchGenerator generator = new PatchGenerator.Builder()
    .baseApk(baseApk)
    .newApk(newApk)
    .build();
// 会自动从 APK 中提取包名
```

---

### 问题 2.3: DEX 加载失败

**错误信息**:
```
[DexPatcher] Failed to load DEX: java.lang.ClassNotFoundException
```

**可能原因**:
1. DEX 文件损坏
2. DEX 格式不兼容
3. ClassLoader 修改失败

**解决方案**:
```java
// 1. 验证 DEX 文件
try {
    DexFile dexFile = DexFile.loadDex(dexPath, null, 0);
    Enumeration<String> entries = dexFile.entries();
    while (entries.hasMoreElements()) {
        String className = entries.nextElement();
        System.out.println("Class: " + className);
    }
} catch (IOException e) {
    // DEX 文件损坏
    e.printStackTrace();
}

// 2. 检查 Android 版本兼容性
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
    // Android 5.0 以下不支持
    throw new UnsupportedOperationException("Requires Android 5.0+");
}

// 3. 检查 ClassLoader 类型
ClassLoader classLoader = context.getClassLoader();
if (!(classLoader instanceof BaseDexClassLoader)) {
    // 不支持的 ClassLoader 类型
    throw new UnsupportedOperationException("Unsupported ClassLoader");
}
```

---

### 问题 2.4: 资源加载失败

**错误信息**:
```
[ResourcePatcher] Failed to load resources: AssetManager.addAssetPath() returned false
```

**可能原因**:
1. resources.arsc 被压缩
2. 资源文件损坏
3. AssetManager 替换失败

**解决方案**:
```java
// 1. 检查 resources.arsc 压缩方法
try (ZipFile zipFile = new ZipFile(patchFile)) {
    ZipEntry entry = zipFile.getEntry("resources.arsc");
    if (entry.getMethod() != ZipEntry.STORED) {
        // resources.arsc 被压缩了！
        throw new IllegalStateException("resources.arsc must be STORED");
    }
}

// 2. 验证 AssetManager.addAssetPath()
AssetManager assetManager = AssetManager.class.newInstance();
Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
int cookie = (int) addAssetPath.invoke(assetManager, patchPath);
if (cookie == 0) {
    // 加载失败
    throw new RuntimeException("addAssetPath() failed");
}

// 3. 检查文件权限
File patchFile = new File(patchPath);
if (!patchFile.canRead()) {
    // 文件不可读
    patchFile.setReadable(true);
}
```

---

## 3️⃣ 签名验证失败

### 问题 3.1: 签名不匹配

**错误信息**:
```
[SecurityManager] Signature verification failed: Certificate mismatch
```

**可能原因**:
1. 补丁使用了不同的签名密钥
2. APK 和补丁的签名不一致
3. 签名被篡改

**解决方案**:
```java
// 1. 比对签名信息
PackageInfo apkInfo = context.getPackageManager()
    .getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
Signature[] apkSignatures = apkInfo.signatures;

// 从补丁中提取签名
JarFile jarFile = new JarFile(patchFile);
Certificate[] patchCerts = jarFile.getEntry("patch.json").getCertificates();

// 比对证书
if (!Arrays.equals(apkSignatures[0].toByteArray(), 
                   patchCerts[0].getEncoded())) {
    // 签名不匹配
    throw new SecurityException("Signature mismatch");
}

// 2. 使用相同的密钥签名补丁
// 确保 patch-cli 使用与 APK 相同的 keystore
java -jar patch-cli.jar \
  --keystore app-release.jks \  // 与 APK 相同
  --keystore-password <password> \
  --key-alias <alias> \
  --key-password <password>
```

---

### 问题 3.2: 签名文件缺失

**错误信息**:
```
[SecurityManager] Signature verification failed: No signature found
```

**可能原因**:
1. 补丁没有签名
2. 签名文件被删除
3. ZIP 文件损坏

**解决方案**:
```bash
# 1. 检查签名文件
unzip -l patch.zip | grep META-INF
# 应该包含:
# META-INF/MANIFEST.MF
# META-INF/CERT.SF
# META-INF/CERT.RSA

# 2. 重新签名补丁
java -jar patch-cli.jar \
  --sign-only \
  --input patch.zip \
  --output patch-signed.zip \
  --keystore keystore.jks \
  --keystore-password <password> \
  --key-alias <alias> \
  --key-password <password>
```

---

## 4️⃣ 加密解密失败

### 问题 4.1: 密码错误

**错误信息**:
```
[SecurityManager] Decryption failed: javax.crypto.BadPaddingException
```

**可能原因**:
1. 解密密码错误
2. 加密算法不匹配
3. 文件被篡改

**解决方案**:
```java
// 1. 验证密码
String password = "your_password";
try {
    SecurityManager securityManager = new SecurityManager(context);
    File decrypted = securityManager.decryptPatchWithPassword(
        encryptedPatch, password);
    // 密码正确
} catch (BadPaddingException e) {
    // 密码错误
    showPasswordDialog();
}

// 2. 检查加密算法
// 确保加密和解密使用相同的算法
// 默认: AES-256-GCM
```

---

### 问题 4.2: KeyStore 访问失败

**错误信息**:
```
[SecurityManager] KeyStore access failed: java.security.KeyStoreException
```

**可能原因**:
1. KeyStore 未初始化
2. 密钥不存在
3. 设备不支持 KeyStore

**解决方案**:
```java
// 1. 检查 KeyStore 可用性
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
    // Android 6.0 以下不支持 KeyStore
    throw new UnsupportedOperationException("Requires Android 6.0+");
}

// 2. 初始化 KeyStore
KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
keyStore.load(null);

// 3. 检查密钥是否存在
String keyAlias = "patch_encryption_key";
if (!keyStore.containsAlias(keyAlias)) {
    // 密钥不存在，生成新密钥
    generateKey(keyAlias);
}
```

---

## 5️⃣ 资源加载失败

### 问题 5.1: 资源未更新

**错误信息**:
```
应用了补丁，但资源没有更新
```

**可能原因**:
1. 没有重启 Activity
2. AssetManager 没有替换
3. 资源缓存问题

**解决方案**:
```java
// 1. 重启 Activity
Intent intent = getIntent();
finish();
startActivity(intent);

// 2. 清除资源缓存
Resources resources = context.getResources();
resources.flushLayoutCache();

// 3. 强制重新加载
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    context.createConfigurationContext(
        context.getResources().getConfiguration());
}
```

---

### 问题 5.2: 资源 ID 冲突

**错误信息**:
```
[ResourcePatcher] Resource ID conflict: 0x7f010001
```

**可能原因**:
1. 新增了资源
2. 资源 ID 重新分配
3. 混淆导致 ID 变化

**解决方案**:
```java
// 1. 使用资源名称而不是 ID
// 不推荐:
int resId = R.drawable.icon;

// 推荐:
int resId = context.getResources().getIdentifier(
    "icon", "drawable", context.getPackageName());

// 2. 固定资源 ID（在 public.xml 中）
// res/values/public.xml
<resources>
    <public type="drawable" name="icon" id="0x7f010001" />
</resources>

// 3. 避免新增资源
// 只修改现有资源的内容，不新增或删除
```

---

## 6️⃣ 性能问题

### 问题 6.1: 补丁生成太慢

**症状**:
```
补丁生成需要 30 秒以上
```

**可能原因**:
1. 使用 Java 引擎
2. 没有启用并行处理
3. APK 文件过大

**解决方案**:
```java
// 1. 使用 Native 引擎
PatchGenerator generator = new PatchGenerator.Builder()
    .engineType(EngineType.NATIVE)  // 2-3倍性能提升
    .build();

// 2. 启用并行处理
GeneratorConfig config = GeneratorConfig.builder()
    .enableParallel(true)
    .threadPoolSize(Runtime.getRuntime().availableProcessors())
    .build();

// 3. 使用增量算法
PatchGenerator generator = new PatchGenerator.Builder()
    .patchMode(PatchMode.BSDIFF)  // 使用 BsDiff
    .build();
```

---

### 问题 6.2: 应用启动变慢

**症状**:
```
应用补丁后，启动时间增加 500ms+
```

**可能原因**:
1. 在主线程加载补丁
2. 签名验证耗时
3. 资源加载耗时

**解决方案**:
```java
// 1. 延迟加载非关键资源
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    
    // 只加载 DEX 和 SO（立即生效）
    HotUpdateHelper.getInstance().loadDexAndSo();
}

@Override
public void onCreate() {
    super.onCreate();
    
    // 延迟加载资源
    new Handler().postDelayed(() -> {
        HotUpdateHelper.getInstance().loadResources();
    }, 1000);
}

// 2. 缓存签名验证结果
SharedPreferences prefs = getSharedPreferences("signature_cache", MODE_PRIVATE);
String cacheKey = patchFile.getAbsolutePath() + "_" + patchFile.lastModified();
if (prefs.getBoolean(cacheKey, false)) {
    // 跳过验证
    loadPatch(patchFile);
} else {
    // 验证并缓存
    if (verifySignature(patchFile)) {
        prefs.edit().putBoolean(cacheKey, true).apply();
        loadPatch(patchFile);
    }
}
```

---

### 问题 6.3: 内存占用过高

**症状**:
```
应用补丁后，内存占用增加 50MB+
```

**可能原因**:
1. 资源没有释放
2. 缓存过多
3. 内存泄漏

**解决方案**:
```java
// 1. 及时释放资源
try (ZipFile zipFile = new ZipFile(patchFile)) {
    // 处理补丁
} // 自动关闭

// 2. 限制缓存大小
LruCache<String, Bitmap> cache = new LruCache<>(
    (int) (Runtime.getRuntime().maxMemory() / 8)  // 最多占用 1/8 内存
);

// 3. 使用弱引用
Map<String, WeakReference<Bitmap>> cache = new HashMap<>();

// 4. 定期清理
new Handler().postDelayed(() -> {
    System.gc();
}, 5000);
```

---

## 🔧 调试工具

### 1. 日志分析工具

```bash
# 过滤热更新相关日志
adb logcat | grep -E "HotUpdate|PatchGenerator|JarSigner"

# 保存日志到文件
adb logcat -d > hotupdate.log

# 分析错误日志
grep -i "error\|exception\|failed" hotupdate.log
```

---

### 2. 补丁文件分析工具

```bash
# 查看补丁内容
unzip -l patch.zip

# 检查签名文件
unzip -l patch.zip | grep META-INF

# 检查 resources.arsc 压缩方法
unzip -lv patch.zip | grep resources.arsc

# 提取 patch.json
unzip -p patch.zip patch.json | jq .
```

---

### 3. 性能分析工具

```java
public class DebugHelper {
    // 性能监控
    public static void measurePerformance(String tag, Runnable task) {
        long start = System.currentTimeMillis();
        task.run();
        long duration = System.currentTimeMillis() - start;
        Log.d("Performance", tag + " took " + duration + "ms");
    }
    
    // 内存监控
    public static void logMemory(String tag) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        Log.d("Memory", tag + ": " + (used / 1024 / 1024) + "MB");
    }
    
    // 线程监控
    public static void logThreads(String tag) {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        int count = group.activeCount();
        Log.d("Threads", tag + ": " + count + " threads");
    }
}
```

---

## 📋 问题排查检查清单

### 补丁生成
- [ ] APK 文件存在且完整
- [ ] 密钥库配置正确
- [ ] 内存足够（建议 4GB+）
- [ ] 使用 Native 引擎
- [ ] resources.arsc 使用 STORE 模式

### 补丁应用
- [ ] 补丁格式正确（ZIP 文件）
- [ ] 包名匹配
- [ ] 签名验证通过（如果启用）
- [ ] 文件权限正确
- [ ] Android 版本支持（5.0+）

### 性能优化
- [ ] 使用 Native 引擎
- [ ] 启用并行处理
- [ ] 缓存验证结果
- [ ] 延迟加载资源
- [ ] 及时释放资源

---

## 📞 获取帮助

如果以上方法都无法解决问题，请：

1. **查看文档**: [docs/FAQ.md](FAQ.md)
2. **搜索 Issues**: [GitHub Issues](https://github.com/706412584/Android_hotupdate/issues)
3. **提交 Issue**: 包含以下信息
   - 完整的错误日志
   - 设备信息（型号、Android 版本）
   - APK 信息（大小、版本）
   - 复现步骤
4. **联系作者**: 706412584@qq.com
