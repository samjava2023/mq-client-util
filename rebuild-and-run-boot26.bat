@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Zulu\zulu-8"
set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%~dp0"

echo Removing stale mq-client-util artifacts from local Maven repo...
if exist "%USERPROFILE%\.m2\repository\com\yourorg\mq" (
  rmdir /s /q "%USERPROFILE%\.m2\repository\com\yourorg\mq"
)

echo.
echo Building standalone mq-client-util-core library...
pushd "%~dp0..\mq-client-util-core"
call mvn clean install -DskipTests -U
if errorlevel 1 (
  echo Core library build failed.
  popd
  exit /b 1
)
popd

echo.
echo Building sample applications...
call mvn clean install -DskipTests -U
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo.
echo Copying local mq-config for Boot 2.6 app...
copy /Y docker\mq-config.local.json sample-boot26-webapp\src\main\resources\mq-config.json >nul

echo.
echo Starting Spring Boot 2.6.5 app on http://localhost:8086 ...
cd sample-boot26-webapp
call mvn spring-boot:run

endlocal
