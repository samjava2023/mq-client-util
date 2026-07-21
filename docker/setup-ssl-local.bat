@echo off
setlocal

cd /d "%~dp0.."

echo ============================================
echo Local IBM MQ - enable TLS (production-like)
echo Cipher: ECDHE_RSA_AES_256_GCM_SHA384
echo ============================================
echo.

if not exist "docker\ssl\truststore.jks" (
  echo Generating certificates and truststore ...
  call docker\generate-ssl-certs.bat
  if errorlevel 1 exit /b 1
)

echo Starting IBM MQ container (if not running) ...
docker compose up -d
if errorlevel 1 (
  echo Failed to start Docker. Is Docker Desktop running?
  exit /b 1
)

echo Waiting for queue manager ...
:wait
docker exec ibmmq-dev chkmqready >nul 2>&1
if errorlevel 1 (
  timeout /t 3 /nobreak >nul
  goto wait
)

echo Configuring TLS inside container ...
docker exec ibmmq-dev sed -i "s/\r$//" /mnt/mqm/ssl/configure-qm-ssl.sh 2>nul
docker exec ibmmq-dev chmod +x /mnt/mqm/ssl/configure-qm-ssl.sh 2>nul
docker exec ibmmq-dev bash /mnt/mqm/ssl/configure-qm-ssl.sh
if errorlevel 1 (
  echo TLS configuration failed.
  exit /b 1
)

for %%I in ("%CD%\docker\ssl\truststore.jks") do set "TRUSTSTORE=%%~fI"
set "TRUSTSTORE=%TRUSTSTORE:\=/%"

echo.
echo Updating docker\mq-config.ssl-local-docker.json truststore path ...
powershell -NoProfile -Command ^
  "$p='%TRUSTSTORE%'; $j=Get-Content 'docker/mq-config.ssl-local-docker.json' -Raw; $j=$j -replace 'REPLACE_WITH_ABSOLUTE_PATH/docker/ssl/truststore.jks',$p; Set-Content 'docker/mq-config.ssl-local-docker.json' $j"

echo.
echo ============================================
echo TLS local MQ is ready
echo ============================================
echo.
echo Copy SSL config into your app:
echo   copy /Y docker\mq-config.ssl-local-docker.json sample-boot14-webapp\src\main\resources\mq-config.json
echo.
echo Truststore : %TRUSTSTORE%
echo Password   : changeit
echo.
echo Test send (Boot 1.4 example):
echo   curl -X POST "http://localhost:8084/mq/sendXml?queue=responseQ" -H "Content-Type: application/xml" -d "^<test^>1^</test^>"
echo.
echo To return to plain TCP dev profile:
echo   copy /Y docker\mq-config.local.json sample-boot14-webapp\src\main\resources\mq-config.json
echo   docker exec ibmmq-dev runmqsc QM.SU000423 -c "ALTER CHANNEL('DBTAX.VE.SVRCONN') CHLTYPE(SVRCONN) SSLCIPH(' ')"
echo.
endlocal
