@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Zulu\zulu-8"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using JAVA_HOME=%JAVA_HOME%
java -version
echo.

cd /d "%~dp0"

echo Building...
call mvn clean install -DskipTests
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo Starting sample-boot14-webapp on port 8084...
call mvn -e spring-boot:run
if errorlevel 1 (
  echo.
  echo App failed to start. Check the error above.
  echo Ensure JAVA_HOME points to JDK 8 and port 8084 is free.
  exit /b 1
)

endlocal
