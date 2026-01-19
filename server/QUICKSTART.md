# 🚀 快速开始 - 补丁服务端

## 📦 已部署内容

✅ **GitHub Releases** - 补丁托管服务  
✅ **version.json** - 版本管理文件  
✅ **GitHub Actions** - 自动发布 workflow  
✅ **客户端示例** - 更新检查代码  

## 🎯 发布补丁（3 种方式）

### 方式 1: GitHub Actions 手动触发（推荐）

1. 打开 GitHub 仓库：https://github.com/706412584/Android_hotupdate
2. 点击 **Actions** 标签
3. 选择 **Release Patch** workflow
4. 点击 **Run workflow**
5. 填写参数：
   - **version**: `1.4.1`
   - **base_version**: `1.4.0`
   - **description**: `修复 SIGBUS 崩溃问题`
6. 点击 **Run workflow** 开始发布

### 方式 2: Git 标签触发

```bash
# 创建标签
git tag -a v1.4.1 -m "Release v1.4.1: 修复 SIGBUS 崩溃"

# 推送标签（自动触发发布）
git push origin v1.4.1
```

### 方式 3: 手动发布

```bash
# 1. 生成补丁
java -jar patch-cli/build/libs/patch-cli-1.3.2-all.jar \
  --old test_assets/app-v1.4-base.apk \
  --new app/build/outputs/apk/release/app-release.apk \
  --output patch-v1.4.1.zip \
  --sign

# 2. 在 GitHub 创建 Release 并上传补丁文件

# 3. 更新 version.json
vim version.json

# 4. 提交并推送
git add version.json
git commit -m "chore: update version.json for v1.4.1"
git push
```

## 📱 客户端集成

### 1. 添加依赖

```gradle
// app/build.gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### 2. 添加权限

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

### 3. 复制示例代码

将以下文件复制到你的项目：
- `server/client-example/UpdateChecker.kt`
- `server/client-example/UpdateActivity.kt`

### 4. 使用示例

```kotlin
// 在 MainActivity 中
class MainActivity : AppCompatActivity() {
    
    private lateinit var updateChecker: UpdateChecker
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        updateChecker = UpdateChecker(this)
        
        // 检查更新
        checkForUpdate()
    }
    
    private fun checkForUpdate() {
        lifecycleScope.launch {
            val currentVersion = getCurrentVersion()
            val result = updateChecker.checkUpdate(currentVersion)
            
            when (result) {
                is UpdateResult.HasUpdate -> {
                    // 有新版本，显示更新对话框
                    showUpdateDialog(result.patchInfo)
                }
                is UpdateResult.NoUpdate -> {
                    // 已是最新版本
                    Toast.makeText(this@MainActivity, "已是最新版本", Toast.LENGTH_SHORT).show()
                }
                is UpdateResult.Error -> {
                    // 检查失败
                    Log.e("Update", "检查更新失败: ${result.message}")
                }
            }
        }
    }
    
    private fun getCurrentVersion(): String {
        return packageManager.getPackageInfo(packageName, 0).versionName
    }
}
```

## 🔗 访问地址

### 版本信息 API
```
https://raw.githubusercontent.com/706412584/Android_hotupdate/main/version.json
```

### CDN 加速（推荐）
```
https://cdn.jsdelivr.net/gh/706412584/Android_hotupdate@main/version.json
```

### Releases 页面
```
https://github.com/706412584/Android_hotupdate/releases
```

## 📊 测试发布

现在可以测试发布第一个补丁：

```bash
# 1. 确保有补丁文件
ls test_assets/patch-v1.4-test.zip

# 2. 创建标签触发发布
git tag -a v1.4.1 -m "Release v1.4.1: 修复 SIGBUS 崩溃"
git push origin v1.4.1

# 3. 查看 Actions 执行情况
# https://github.com/706412584/Android_hotupdate/actions

# 4. 发布完成后，检查 Releases
# https://github.com/706412584/Android_hotupdate/releases
```

## 🎉 完成！

现在你的补丁服务已经部署完成，可以：

- ✅ 自动发布补丁到 GitHub Releases
- ✅ 客户端自动检查更新
- ✅ 下载并应用补丁
- ✅ 全球 CDN 加速
- ✅ 完全免费

## 📞 需要帮助？

- 📖 [完整文档](server/README.md)
- 🐛 [报告问题](https://github.com/706412584/Android_hotupdate/issues)
- 💬 [讨论区](https://github.com/706412584/Android_hotupdate/discussions)
