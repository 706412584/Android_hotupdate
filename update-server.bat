@echo off
echo ========================================
echo 更新服务端到 server 分支
echo ========================================
echo.

REM 确保在 main 分支
git checkout main
if %ERRORLEVEL% NEQ 0 (
    echo 切换到 main 分支失败！
    pause
    exit /b 1
)

echo 正在拉取最新代码...
git pull androidhotupdate main
git pull gitee main

echo.
echo 切换到 server 分支...
git checkout server
if %ERRORLEVEL% NEQ 0 (
    echo 切换到 server 分支失败！
    pause
    exit /b 1
)

echo.
echo 合并 main 分支的更改...
git merge main -m "chore: 同步 main 分支更新到 server"
if %ERRORLEVEL% NEQ 0 (
    echo 合并失败，请手动解决冲突！
    pause
    exit /b 1
)

echo.
echo 推送到远程仓库...
git push androidhotupdate server
git push gitee server

echo.
echo 切换回 main 分支...
git checkout main

echo.
echo ========================================
echo ✓ 服务端更新完成！
echo Zeabur 将自动部署新版本
echo ========================================
pause
