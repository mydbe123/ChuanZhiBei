@echo off
chcp 65001 >nul
setlocal EnableExtensions
title 智学引擎 ZhiXue Engine - 一键启动
cd /d "%~dp0"

echo.
echo   ====================================================
echo      智学引擎 ZhiXue Engine  ^|  本地一键启动
echo      Spring Boot 3.5  +  MySQL  +  DeepSeek V4.1
echo   ====================================================
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo   [X] 未检测到 Java。
  echo       请安装 JDK 21 或更高版本，并把 java 加入 PATH。
  echo       下载地址: https://adoptium.net/temurin/releases/
  echo.
  pause
  exit /b 1
)
echo   [i] 已检测到 Java 运行环境。

if not exist ".env" (
  if exist ".env.example" (
    copy /y ".env.example" ".env" >nul
    echo.
    echo   [!] 首次运行：已根据模板生成 .env 配置文件。
    echo       请填写 DEEPSEEK_API_KEY 与数据库账号密码（DB_URL / DB_USERNAME / DB_PASSWORD），
    echo       保存并关闭记事本后，重新双击本文件即可。
    start "" notepad ".env"
    echo.
    pause
    exit /b 1
  )
  echo   [X] 缺少 .env 配置文件，且未找到 .env.example。
  pause
  exit /b 1
)
echo   [i] 配置文件 .env 已就绪。

echo   [i] 检查 8080 端口是否已有服务 ...
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 (
  echo   [i] 服务已在运行，正在打开浏览器 ...
  start "" "http://localhost:8080"
  ping -n 3 127.0.0.1 >nul
  exit /b 0
)

if not exist "target\zhixue-engine.jar" (
  echo   [i] 未找到可执行 jar，开始用 Maven 构建（约 1-3 分钟，请勿关闭本窗口）...
  where mvn >nul 2>nul
  if errorlevel 1 (
    echo.
    echo   [X] 未找到 target\zhixue-engine.jar，且本机没有安装 Maven。
    echo       方案一：安装 Maven（https://maven.apache.org）后重新双击本文件；
    echo       方案二：在项目目录手动执行  mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
  )
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo.
    echo   [X] 构建失败，请查看上面的 Maven 输出。
    pause
    exit /b 1
  )
)

echo   [>] 正在后台启动服务 ...
start "ZhiXue Engine" /min cmd /c "java -jar target\zhixue-engine.jar > app.log 2>&1"

echo   [>] 等待服务就绪（首次启动约 10-15 秒）...
set /a tries=0
:wait
set /a tries+=1
netstat -ano | findstr ":8080" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto ready
if %tries% geq 45 (
  echo.
  echo   [X] 启动超时。请查看项目目录下的 app.log。
  echo       常见原因：MySQL 连接失败 / 端口被占用 / .env 配置缺失。
  echo.
  pause
  exit /b 1
)
ping -n 3 127.0.0.1 >nul
goto wait

:ready
echo.
echo   ====================================================
echo     [OK] 启动成功
echo     访问地址 : http://localhost:8080
echo     体验账号 : demo / demo123456   （或点「一键体验」）
echo     停止服务 : 双击「停止智学引擎.bat」
echo   ====================================================
echo.
echo   已预置演示数据：个人笔记 / 10 天计划 / 3 次测验 / 3 段 Agent 对话
echo.
start "" "http://localhost:8080"
ping -n 8 127.0.0.1 >nul
exit /b 0
