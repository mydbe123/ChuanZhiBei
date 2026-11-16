@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
title 智学引擎 ZhiXue Engine - 停止服务
cd /d "%~dp0"

echo.
echo   正在停止占用 8080 端口的智学引擎服务 ...

set "FOUND="
set "LAST="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    if not "%%p"=="!LAST!" (
        echo   [x] 停止进程 PID %%p
        taskkill /PID %%p /F >nul 2>&1
        set "LAST=%%p"
        set "FOUND=1"
    )
)

if not defined FOUND echo   [i] 服务当前未在运行

echo.
echo   [OK] 已处理完成。可关闭本窗口。
ping -n 5 127.0.0.1 >nul
exit /b 0
