@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
echo ========================================
echo 检查发布配置
echo ========================================
echo.

echo [1/5] 检查版本号...
echo.
findstr /C:"pomVersion = " build.gradle
echo.

echo [2/5] 检查 Maven Central 凭证...
echo.
call :checkCred ossrhUsername OSSRH_USERNAME "Maven Central 用户名"
call :checkCred ossrhPassword OSSRH_PASSWORD "Maven Central 密码"
echo.

echo [3/5] 检查 Gradle Plugin Portal 凭证...
echo.
call :checkCred "gradle.publish.key" GRADLE_PUBLISH_KEY "Gradle Plugin Portal Key"
call :checkCred "gradle.publish.secret" GRADLE_PUBLISH_SECRET "Gradle Plugin Portal Secret"
echo.

echo [4/5] 检查签名配置...
echo.
call :checkCred "signing.keyId" SIGNING_KEY_ID "签名 Key ID"
call :checkCred "signing.password" SIGNING_PASSWORD "签名密码"
call :checkCred "signing.secretKeyRingFile" SIGNING_SECRET_KEY_RING_FILE "签名密钥文件"
echo.

echo [5/5] 检查 patch-gradle-plugin 配置...
echo.
findstr /C:"id 'com.gradle.plugin-publish'" patch-gradle-plugin\build.gradle >nul
if %errorlevel% equ 0 (
    echo [OK] plugin-publish 插件已配置
) else (
    echo [X] plugin-publish 插件未配置
)
findstr /C:"id = 'io.github.706412584.patch'" patch-gradle-plugin\build.gradle >nul
if %errorlevel% equ 0 (
    echo [OK] 插件 ID 已配置
) else (
    echo [X] 插件 ID 未配置
)
findstr /C:"website = " patch-gradle-plugin\build.gradle >nul
if %errorlevel% equ 0 (
    echo [OK] 插件 website 已配置
) else (
    echo [X] 插件 website 未配置
)
echo.

echo ========================================
echo 配置检查完成
echo ========================================
echo.
echo 发布模块列表：
echo    - patch-core
echo    - patch-native
echo    - patch-generator-android
echo    - update
echo    - patch-cli
echo    - patch-gradle-plugin
echo.
echo 发布目标：
echo    - Maven Central: https://central.sonatype.com/
echo    - Gradle Plugin Portal: https://plugins.gradle.org/
echo.
echo 准备发布？运行：
echo    publish-maven.bat （发布所有模块到 Maven Central）
echo    publish-plugin.bat （发布 patch-gradle-plugin）
echo.
pause
exit /b 0

REM ============================================================
REM :checkCred  检查某项凭证是否已配置，只报告状态与来源，不回显值
REM   %1 = 属性名（gradle.properties 中的键）
REM   %2 = 环境变量名
REM   %3 = 显示名称
REM ============================================================
:checkCred
set "_found="
findstr /B /C:"%~1=" "%~dp0gradle.properties" >nul 2>nul && set "_found=项目 gradle.properties"
if not defined _found (
    findstr /B /C:"%~1=" "%USERPROFILE%\.gradle\gradle.properties" >nul 2>nul && set "_found=用户级 gradle.properties"
)
if not defined _found (
    if defined %~2 set "_found=环境变量 %~2"
)
if defined _found (
    echo [OK] %~3 已配置  ^(来源: !_found!^)
) else (
    echo [X] %~3 未配置
)
exit /b
