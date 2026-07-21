@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Zulu\zulu-8"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ============================================
echo Spring Boot 2.6.5 test app
echo ============================================
echo.

cd /d "%~dp0.."

echo [1/4] Checking Docker IBM MQ...
call docker\verify-local-mq.bat
if errorlevel 1 (
  echo.
  echo Start MQ first: docker\start-local-mq.bat
  exit /b 1
)

echo.
echo [2/4] Using local mq-config (ssl=false)...
copy /Y docker\mq-config.local.json sample-boot26-webapp\src\main\resources\mq-config.json >nul

echo [3/4] Building mq-connector-util then sample app...
pushd "%~dp0..\..\mq-connector-util"
call mvn clean install -DskipTests
if errorlevel 1 (
  echo Core library build failed.
  popd
  exit /b 1
)
popd
call mvn -pl sample-boot26-webapp clean install -DskipTests
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo [4/4] Starting app on http://localhost:8086 ...
echo.
echo Test commands (new terminal):
echo   curl -X POST "http://localhost:8086/mq/sendJson?queue=responseQ" -H "Content-Type: text/plain" -d "{\"test\":1}"
echo   curl "http://localhost:8086/mq/receive?queue=responseQ&timeoutSec=5"
echo.

cd sample-boot26-webapp
call mvn spring-boot:run

endlocal
