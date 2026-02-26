@echo off
REM vFlow API 快速测试脚本 (Windows版本)
setlocal enabledelayedexpansion

echo ╔═══════════════════════════════════════════════════════╗
echo ║        vFlow API 快速测试                          ║
echo ╚═══════════════════════════════════════════════════════╝
echo.

if "%API_URL%"=="" (
    echo ❌ 错误: 请设置环境变量
    echo.
    echo 使用方法:
    echo   set API_URL=http://192.168.1.100:8080
    echo   set API_TOKEN=your-token-here
    echo   quick_test.bat
    echo.
    echo 或者在命令行中传入参数:
    echo   set API_URL=http://192.168.1.100:8080 && set API_TOKEN=your-token && quick_test.bat
    exit /b 1
)

echo 🔗 测试连接...
curl -s -H "Authorization: Bearer %API_TOKEN%" "%API_URL%/api/v1/system/health" -o response.json >nul 2>&1

findstr /C:"\"code\":0" response.json >nul
if errorlevel 1 (
    echo ❌ 连接失败
    del response.json
    exit /b 1
)

echo ✅ 连接成功!
echo.

echo 📱 获取系统信息...
curl -s -H "Authorization: Bearer %API_TOKEN%" "%API_URL%/api/v1/system/info" -o response.json
echo   设备信息:
type response.json | findstr /C:"brand" "model" "androidVersion" >nul
echo.

echo 📋 获取工作流列表...
curl -s -H "Authorization: Bearer %API_TOKEN%" "%API_URL%/api/v1/workflows" -o response.json
for /f "tokens=2 delims=:" %%a in ('type response.json ^| find "workflowCount"') do set COUNT=%%a
echo ✅ 找到 %COUNT% 个工作流
echo.

echo 🧩 获取模块分类...
curl -s -H "Authorization: Bearer %API_TOKEN%" "%API_URL%/api/v1/modules/categories" -o response.json

echo   模块分类:
type response.json ^| findstr /C:"\"name\":" /a >nul
echo.

echo 📊 获取系统统计...
curl -s -H "Authorization: Bearer %API_TOKEN%" "%API_URL%/api/v1/system/stats" -o response.json
echo   统计信息:
type response.json ^| findstr /C:"workflowCount" "totalExecutions" "successRate" /a >nul
echo.

echo ╔═══════════════════════════════════════════════════════╗
echo ║                  🎉 快速测试完成！                          ║
echo ╚═══════════════════════════════════════════════════════╝
echo.
echo 💡 提示:
echo    • 运行完整测试: python scripts/test_api.py --url %API_URL% --token %API_TOKEN%
echo    • 查看示例: python scripts/examples.py --url %API_URL% --token %API_TOKEN%
echo    • 完整文档: docs\API.md

del response.json
endlocal
