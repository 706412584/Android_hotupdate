@echo off
echo ======================================
echo 测试 Maven Central 配置
echo ======================================
echo.

echo 1. 检查 GPG 密钥...
gpg --list-keys B873F9FE1613900C0EA43FE10B2E88CC9B5B6303
if %errorlevel% neq 0 (
    echo ❌ GPG 密钥未找到
    exit /b 1
)
echo ✅ GPG 密钥存在
echo.

echo 2. 检查私钥文件...
if exist "secring.gpg" (
    echo ✅ secring.gpg 文件存在
) else (
    echo ❌ secring.gpg 文件不存在
    exit /b 1
)
echo.

echo 3. 检查 gradle.properties...
if exist "gradle.properties" (
    echo ✅ gradle.properties 文件存在
    echo.
    echo 请确保已配置以下内容：
    echo   - ossrhUsername（从 Sonatype User Token 获取）
    echo   - ossrhPassword（从 Sonatype User Token 获取）
    echo.
    echo 如果还没有 User Token，请访问：
    echo https://central.sonatype.com/
    echo 注册后在 Account 页面生成 User Token
) else (
    echo ❌ gradle.properties 文件不存在
    exit /b 1
)
echo.

echo 4. 测试签名功能...
echo test > test.txt
gpg --detach-sign --armor --passphrase 706412584 --batch --yes test.txt
if exist "test.txt.asc" (
    echo ✅ GPG 签名测试成功
    del test.txt test.txt.asc
) else (
    echo ❌ GPG 签名测试失败
    del test.txt
    exit /b 1
)
echo.

echo ======================================
echo ✅ 配置检查完成！
echo ======================================
echo.
echo 下一步：
echo 1. 访问 https://central.sonatype.com/ 注册账号
echo 2. 创建命名空间 io.github.706412584
echo 3. 验证 GitHub 所有权
echo 4. 生成 User Token
echo 5. 更新 gradle.properties 中的 ossrhUsername 和 ossrhPassword
echo 6. 运行 publish-to-maven-central.bat 发布
echo.

pause
