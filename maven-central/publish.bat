@echo off
setlocal enabledelayedexpansion

echo ======================================
echo Maven Central 发布工具
echo ======================================
echo.

REM 从 gradle.properties 读取凭证
for /f "tokens=1,2 delims==" %%a in (..\gradle.properties) do (
    if "%%a"=="ossrhUsername" set USERNAME=%%b
    if "%%a"=="ossrhPassword" set PASSWORD=%%b
)

if "%USERNAME%"=="" (
    echo ❌ 错误：未找到 ossrhUsername
    pause
    exit /b 1
)

if "%PASSWORD%"=="" (
    echo ❌ 错误：未找到 ossrhPassword
    pause
    exit /b 1
)

REM 生成 Base64 编码的认证令牌
powershell -Command "$auth = '%USERNAME%:%PASSWORD%'; $bytes = [System.Text.Encoding]::UTF8.GetBytes($auth); $base64 = [Convert]::ToBase64String($bytes); Write-Host $base64" > temp_token.txt
set /p AUTH_TOKEN=<temp_token.txt
del temp_token.txt

echo 选择操作：
echo.
echo 1. 快速发布（推荐）- 只构建 patch-core 并上传
echo 2. 完整发布 - 清理、构建、上传
echo 3. 检查部署状态
echo 4. 检查 Maven Central 同步状态
echo 5. 清空所有部署
echo.
set /p CHOICE="请输入选项 (1-5): "

if "%CHOICE%"=="1" goto QUICK_PUBLISH
if "%CHOICE%"=="2" goto FULL_PUBLISH
if "%CHOICE%"=="3" goto CHECK_STATUS
if "%CHOICE%"=="4" goto CHECK_MAVEN
if "%CHOICE%"=="5" goto CLEAR_DEPLOYMENTS

echo 无效选项
pause
exit /b 1

:QUICK_PUBLISH
echo.
echo ========================================
echo 快速发布（3个模块）
echo ========================================
echo.
echo 发布模块：
echo   - patch-core（核心补丁库）
echo   - patch-generator-android（Android 补丁生成器）
echo   - update（热更新核心库）
echo.
echo 1️⃣  发布到本地仓库...
cd ..
call gradlew.bat :patch-core:publishMavenPublicationToLocalRepository :patch-generator-android:publishMavenPublicationToLocalRepository :update:publishMavenPublicationToLocalRepository
if errorlevel 1 (
    echo ❌ 发布失败
    pause
    exit /b 1
)

echo.
echo 2️⃣  创建 Bundle...
cd patch-core\build\repo
if not exist "..\..\..\temp_bundle_build" mkdir "..\..\..\temp_bundle_build"
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd patch-generator-android\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd update\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd temp_bundle_build
powershell -Command "Compress-Archive -Path io -DestinationPath ..\maven-central\bundle.zip -Force"
cd ..
rmdir /s /q temp_bundle_build

if not exist "maven-central\bundle.zip" (
    echo ❌ Bundle 创建失败
    pause
    exit /b 1
)

echo ✅ Bundle 已创建
powershell -Command "Get-Item maven-central\bundle.zip | Select-Object Name, @{Name='Size(KB)';Expression={[math]::Round($_.Length/1KB,2)}}"

echo.
echo 3️⃣  上传到 Central Portal...
cd maven-central
curl -X POST ^
     -H "Authorization: Bearer %AUTH_TOKEN%" ^
     -F "bundle=@bundle.zip" ^
     "https://central.sonatype.com/api/v1/publisher/upload?name=android-hotupdate-1.2.9&publishingType=USER_MANAGED" ^
     > deployment_response.json

echo.
echo ✅ 上传完成！
echo.
echo 部署 ID:
type deployment_response.json
echo.
echo.
echo 已发布模块：
echo   - patch-core:1.2.9
echo   - patch-generator-android:1.2.9
echo   - update:1.2.9
echo.
echo 下一步：
echo   1. 访问 https://central.sonatype.com/publishing/deployments
echo   2. 等待验证完成（约 2-5 分钟）
echo   3. 点击 "Publish" 发布
echo.
cd ..
pause
exit /b 0

:FULL_PUBLISH
echo.
echo ========================================
echo 完整发布（3个模块）
echo ========================================
echo.
echo 发布模块：
echo   - patch-core（核心补丁库）
echo   - patch-generator-android（Android 补丁生成器）
echo   - update（热更新核心库）
echo.
echo 1️⃣  清理构建...
cd ..
call gradlew.bat clean

echo.
echo 2️⃣  构建项目...
call gradlew.bat build -x test

echo.
echo 3️⃣  发布到本地仓库...
call gradlew.bat :patch-core:publishMavenPublicationToLocalRepository :patch-generator-android:publishMavenPublicationToLocalRepository :update:publishMavenPublicationToLocalRepository
if errorlevel 1 (
    echo ❌ 构建失败
    pause
    exit /b 1
)

echo.
echo 4️⃣  创建 Bundle...
cd patch-core\build\repo
if not exist "..\..\..\temp_bundle_build" mkdir "..\..\..\temp_bundle_build"
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd patch-generator-android\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd update\build\repo
xcopy /E /I /Y "io" "..\..\..\temp_bundle_build\io"
cd ..\..\..

cd temp_bundle_build
powershell -Command "Compress-Archive -Path io -DestinationPath ..\maven-central\bundle.zip -Force"
cd ..
rmdir /s /q temp_bundle_build

if not exist "maven-central\bundle.zip" (
    echo ❌ Bundle 创建失败
    pause
    exit /b 1
)

echo ✅ Bundle 已创建
powershell -Command "Get-Item maven-central\bundle.zip | Select-Object Name, @{Name='Size(KB)';Expression={[math]::Round($_.Length/1KB,2)}}"

echo.
echo 5️⃣  上传到 Central Portal...
cd maven-central
curl -X POST ^
     -H "Authorization: Bearer %AUTH_TOKEN%" ^
     -F "bundle=@bundle.zip" ^
     "https://central.sonatype.com/api/v1/publisher/upload?name=android-hotupdate-1.2.9&publishingType=USER_MANAGED" ^
     > deployment_response.json

echo.
echo ✅ 上传完成！
echo.
echo 部署 ID:
type deployment_response.json
echo.
echo.
echo 已发布模块：
echo   - patch-core:1.2.9
echo   - patch-generator-android:1.2.9
echo   - update:1.2.9
echo.
echo 下一步：
echo   1. 访问 https://central.sonatype.com/publishing/deployments
echo   2. 等待验证完成（约 2-5 分钟）
echo   3. 点击 "Publish" 发布
echo.
cd ..
pause
exit /b 0

:CHECK_STATUS
echo.
set /p DEPLOYMENT_ID="请输入部署 ID: "
if "%DEPLOYMENT_ID%"=="" (
    echo ❌ 部署 ID 不能为空
    pause
    exit /b 1
)

echo.
echo 正在检查部署状态...
cd maven-central
curl -X GET ^
     -H "Authorization: Bearer %AUTH_TOKEN%" ^
     "https://central.sonatype.com/api/v1/publisher/status?id=%DEPLOYMENT_ID%" ^
     > status_response.json

echo.
type status_response.json
echo.
cd ..
pause
exit /b 0

:CHECK_MAVEN
echo.
echo 正在检查 Maven Central...
curl -s -o nul -w "%%{http_code}" "https://repo1.maven.org/maven2/io/github/706412584/patch-core/1.2.9/patch-core-1.2.9.pom" > temp_status.txt
set /p STATUS=<temp_status.txt
del temp_status.txt

if "%STATUS%"=="200" (
    echo ✅ 库已在 Maven Central 上可见！
    echo.
    echo 使用方法：
    echo   implementation 'io.github.706412584:patch-core:1.2.9'
    echo.
    echo 查看：https://repo1.maven.org/maven2/io/github/706412584/patch-core/1.2.9/
) else (
    echo ⏳ 还在同步中（通常需要 15-30 分钟）
    echo HTTP 状态码: %STATUS%
)
echo.
pause
exit /b 0

:CLEAR_DEPLOYMENTS
echo.
echo ⚠️  警告：这将删除所有未发布的部署
set /p CONFIRM="确认删除？(Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo 已取消
    pause
    exit /b 0
)

echo.
echo 正在获取部署列表...
cd maven-central
curl -s -X GET ^
     -H "Authorization: Bearer %AUTH_TOKEN%" ^
     "https://central.sonatype.com/api/v1/publisher/deployments" ^
     > deployments_list.json

echo 正在删除...
powershell -Command ^
    "$json = Get-Content deployments_list.json -Raw | ConvertFrom-Json; " ^
    "$authToken = '%AUTH_TOKEN%'; " ^
    "$count = 0; " ^
    "foreach ($deployment in $json) { " ^
    "    $id = $deployment.deploymentId; " ^
    "    $name = $deployment.name; " ^
    "    Write-Host \"删除: $name ($id)\"; " ^
    "    $headers = @{'Authorization' = \"Bearer $authToken\"}; " ^
    "    try { " ^
    "        Invoke-RestMethod -Uri \"https://central.sonatype.com/api/v1/publisher/deployment/$id\" -Method Delete -Headers $headers | Out-Null; " ^
    "        $count++; " ^
    "    } catch { } " ^
    "} " ^
    "Write-Host \"`n✅ 已删除 $count 个部署\""

cd ..
echo.
pause
exit /b 0
