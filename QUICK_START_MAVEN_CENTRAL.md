# 快速开始：发布到 Maven Central

## ✅ 已完成的配置

1. **GPG 密钥已生成**
   - Key ID: `B873F9FE1613900C0EA43FE10B2E88CC9B5B6303`
   - 短 Key ID: `9B5B6303`
   - Email: `706412584@qq.com`
   - 密钥已上传到 keyserver.ubuntu.com

2. **本地文件已创建**
   - ✅ `secring.gpg` - GPG 私钥文件
   - ✅ `gradle.properties` - 构建配置文件
   - ✅ `maven-publish.gradle` - Maven 发布配置

## 📝 还需要完成的步骤

### 步骤 1：注册 Sonatype 账号

1. 访问 https://central.sonatype.com/
2. 点击右上角 "Sign Up" 注册
3. 使用你的 GitHub 账号登录（推荐）或邮箱注册
4. 验证邮箱

### 步骤 2：创建命名空间并验证

1. 登录后，点击左侧 "Namespaces"
2. 点击 "Add Namespace"
3. 输入：`io.github.706412584`
4. 选择验证方式：**GitHub Repository**
5. 按照提示验证 GitHub 所有权：
   - 方式 A：在你的 GitHub 账号下创建一个公开仓库，名称为 Sonatype 提供的随机字符串
   - 方式 B：在现有仓库（如 Android_hotupdate）中添加一个文件，内容为 Sonatype 提供的验证码
6. 点击 "Verify" 完成验证

### 步骤 3：生成 User Token

1. 点击右上角头像 → "View Account"
2. 在 "User Token" 部分，点击 "Generate User Token"
3. 会生成两个值：
   - **Username**（类似：`abcd1234`）
   - **Password**（类似：`xyz789...`）
4. **重要**：立即复制这两个值，关闭后无法再查看！

### 步骤 4：更新 gradle.properties

打开项目根目录的 `gradle.properties` 文件，替换以下内容：

```properties
# 将 YOUR_USERNAME 替换为 User Token 的 Username
ossrhUsername=YOUR_USERNAME

# 将 YOUR_PASSWORD 替换为 User Token 的 Password
ossrhPassword=YOUR_PASSWORD

# 以下配置已自动填写，无需修改
signing.keyId=9B5B6303
signing.password=706412584
signing.secretKeyRingFile=D:\\android\\projecet_iade\\androidhotupdate\\secring.gpg
```

### 步骤 5：发布到 Maven Central

运行发布脚本：

```bash
# Windows
publish-to-maven-central.bat

# 或手动执行
gradlew.bat clean build publishAllPublicationsToSonatypeRepository
```

### 步骤 6：在 Sonatype 中发布

1. 访问 https://s01.oss.sonatype.org/
2. 使用你的 Sonatype 账号登录（不是 User Token）
3. 点击左侧 "Staging Repositories"
4. 找到你的仓库（通常以 `iogithub706412584-` 开头）
5. 选中后点击上方 "Close" 按钮
   - 系统会自动验证（检查 POM、签名等）
   - 等待几分钟直到状态变为 "Closed"
6. 验证通过后，点击 "Release" 按钮
7. 确认发布

### 步骤 7：等待同步

- 发布后约 10-30 分钟会同步到 Maven Central
- 可以在这里查看：https://central.sonatype.com/artifact/io.github.706412584/patch-core

## 🎯 使用发布的库

发布成功后，其他人可以这样使用：

### Gradle
```groovy
dependencies {
    implementation 'io.github.706412584:patch-core:1.2.8'
    implementation 'io.github.706412584:update:1.2.8'
}
```

### Maven
```xml
<dependency>
    <groupId>io.github.706412584</groupId>
    <artifactId>patch-core</artifactId>
    <version>1.2.8</version>
</dependency>
```

## ⚠️ 重要提示

1. **gradle.properties 不会提交到 Git**（已添加到 .gitignore）
2. **secring.gpg 不会提交到 Git**（已添加到 .gitignore）
3. **User Token 只显示一次**，请妥善保存
4. **首次发布需要验证命名空间**，之后就不需要了
5. **发布是不可撤销的**，请确保版本号正确

## 🆘 遇到问题？

查看详细文档：`MAVEN_CENTRAL_PUBLISH.md`

常见问题：
- **签名失败**：检查 GPG 密钥和密码是否正确
- **认证失败**：确保使用的是 User Token，不是网站登录密码
- **命名空间验证失败**：确保 GitHub 验证已完成
- **POM 验证失败**：检查 maven-publish.gradle 配置
