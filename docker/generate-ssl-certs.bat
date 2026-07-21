@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0"

set "SSL_DIR=%~dp0ssl"
set "STORE_PASS=changeit"
set "DAYS=365"
set "KEYTOOL="
set "OPENSSL_EXE="

if defined JAVA_HOME (
  set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"
)
if not exist "%KEYTOOL%" (
  where keytool >nul 2>&1
  if not errorlevel 1 (
    for /f "delims=" %%K in ('where keytool 2^>nul') do set "KEYTOOL=%%K"
  )
)
if not exist "%KEYTOOL%" (
  echo keytool not found. Set JAVA_HOME or add JDK bin to PATH.
  exit /b 1
)

if not exist "%SSL_DIR%" mkdir "%SSL_DIR%"

if /i "%USE_OPENSSL%"=="1" (
  call :find_openssl
  if defined OPENSSL_EXE (
    call :openssl_flow
    if not errorlevel 1 goto :finish
    echo OpenSSL failed - falling back to Java keytool ...
  )
)

call :keytool_only
if errorlevel 1 exit /b 1
goto :finish

:find_openssl
where openssl >nul 2>&1
if not errorlevel 1 (
  for /f "delims=" %%O in ('where openssl 2^>nul') do set "OPENSSL_EXE=%%O"
  goto :configure_openssl
)
for %%P in (
  "%ProgramFiles%\Git\usr\bin\openssl.exe"
  "%ProgramFiles(x86)%\Git\usr\bin\openssl.exe"
  "%LocalAppData%\Programs\Git\usr\bin\openssl.exe"
  "C:\Program Files\OpenSSL-Win64\bin\openssl.exe"
  "C:\Program Files\OpenSSL\bin\openssl.exe"
) do (
  if exist %%P (
    set "OPENSSL_EXE=%%~P"
    goto :configure_openssl
  )
)
set "OPENSSL_EXE="
exit /b 0

:configure_openssl
if not defined OPENSSL_EXE exit /b 0
for %%P in (
  "%ProgramFiles%\Git\usr\ssl\openssl.cnf"
  "%ProgramFiles%\Git\mingw64\etc\ssl\openssl.cnf"
  "%ProgramFiles(x86)%\Git\usr\ssl\openssl.cnf"
  "%LocalAppData%\Programs\Git\usr\ssl\openssl.cnf"
  "%OPENSSL_WIN_DIR%\bin\openssl.cfg"
) do (
  if exist %%P (
    set "OPENSSL_CONF=%%~P"
    exit /b 0
  )
)
exit /b 0

:openssl_flow
echo Using OpenSSL: %OPENSSL_EXE%
if defined OPENSSL_CONF echo Using OPENSSL_CONF: %OPENSSL_CONF%

echo.
echo [OpenSSL] Generating self-signed certificate ...
if exist "%SSL_DIR%\server.key" del /f "%SSL_DIR%\server.key"
if exist "%SSL_DIR%\server.crt" del /f "%SSL_DIR%\server.crt"
"%OPENSSL_EXE%" req -x509 -newkey rsa:2048 ^
  -keyout "%SSL_DIR%\server.key" ^
  -out "%SSL_DIR%\server.crt" ^
  -days %DAYS% -nodes ^
  -subj "/CN=localhost" ^
  -addext "subjectAltName=DNS:localhost,DNS:ibmmq-dev,IP:127.0.0.1" 2>nul
if errorlevel 1 (
  "%OPENSSL_EXE%" req -x509 -newkey rsa:2048 ^
    -keyout "%SSL_DIR%\server.key" ^
    -out "%SSL_DIR%\server.crt" ^
    -days %DAYS% -nodes ^
    -subj "/CN=localhost"
)
if not exist "%SSL_DIR%\server.crt" exit /b 1
if not exist "%SSL_DIR%\server.key" exit /b 1

echo [OpenSSL] Creating PKCS12 ...
if exist "%SSL_DIR%\server.p12" del /f "%SSL_DIR%\server.p12"
"%OPENSSL_EXE%" pkcs12 -export ^
  -in "%SSL_DIR%\server.crt" ^
  -inkey "%SSL_DIR%\server.key" ^
  -out "%SSL_DIR%\server.p12" ^
  -name qmgrssl ^
  -passout pass:%STORE_PASS%
if not exist "%SSL_DIR%\server.p12" exit /b 1

echo [OpenSSL] Creating truststore.jks ...
if exist "%SSL_DIR%\truststore.jks" del /f "%SSL_DIR%\truststore.jks"
"%KEYTOOL%" -importcert -noprompt ^
  -alias ibmmq-local ^
  -file "%SSL_DIR%\server.crt" ^
  -keystore "%SSL_DIR%\truststore.jks" ^
  -storepass %STORE_PASS%
exit /b 0

:keytool_only
echo.
echo Using Java keytool ^(JDK^) - no OpenSSL required.
echo.

echo [1/3] Generating PKCS12 keystore ...
if exist "%SSL_DIR%\server.p12" del /f "%SSL_DIR%\server.p12"
"%KEYTOOL%" -genkeypair -alias qmgrssl -keyalg RSA -keysize 2048 -validity %DAYS% ^
  -keystore "%SSL_DIR%\server.p12" -storetype PKCS12 -storepass %STORE_PASS% ^
  -keypass %STORE_PASS% ^
  -dname "CN=localhost" ^
  -ext "SAN=DNS:localhost,DNS:ibmmq-dev,IP:127.0.0.1" 2>nul
if errorlevel 1 (
  echo Retrying without SAN extension ^(older JDK^) ...
  "%KEYTOOL%" -genkeypair -alias qmgrssl -keyalg RSA -keysize 2048 -validity %DAYS% ^
    -keystore "%SSL_DIR%\server.p12" -storetype PKCS12 -storepass %STORE_PASS% ^
    -keypass %STORE_PASS% ^
    -dname "CN=localhost"
)
if not exist "%SSL_DIR%\server.p12" (
  echo Failed to create server.p12
  exit /b 1
)

echo [2/3] Exporting certificate ...
if exist "%SSL_DIR%\server.crt" del /f "%SSL_DIR%\server.crt"
"%KEYTOOL%" -exportcert -alias qmgrssl -file "%SSL_DIR%\server.crt" ^
  -keystore "%SSL_DIR%\server.p12" -storetype PKCS12 -storepass %STORE_PASS%

echo [3/3] Creating truststore.jks ...
if exist "%SSL_DIR%\truststore.jks" del /f "%SSL_DIR%\truststore.jks"
"%KEYTOOL%" -importcert -noprompt ^
  -alias ibmmq-local ^
  -file "%SSL_DIR%\server.crt" ^
  -keystore "%SSL_DIR%\truststore.jks" ^
  -storepass %STORE_PASS%
if not exist "%SSL_DIR%\truststore.jks" (
  echo Failed to create truststore.jks
  exit /b 1
)
exit /b 0

:finish
for %%I in ("%SSL_DIR%") do set "SSL_ABS=%%~fI"
> "%SSL_DIR%\truststore-path.txt" echo %SSL_ABS%\truststore.jks

echo.
echo Done.
echo   server.p12               - queue manager certificate
echo   truststore.jks           - Java client truststore
echo   password                 - %STORE_PASS%
echo.
echo Next: docker\setup-ssl-local.bat
endlocal
exit /b 0
