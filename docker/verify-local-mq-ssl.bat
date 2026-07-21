@echo off
setlocal
cd /d "%~dp0.."

echo ============================================
echo Verify local IBM MQ TLS setup
echo ============================================
echo.

docker ps --filter name=ibmmq-dev --filter status=running --format "{{.Names}}" | findstr /i ibmmq-dev >nul
if errorlevel 1 (
  echo [FAIL] Container ibmmq-dev is not running. Run docker\setup-ssl-local.bat
  exit /b 1
)
echo [OK] Container running

if not exist "docker\ssl\truststore.jks" (
  echo [FAIL] docker\ssl\truststore.jks not found. Run docker\generate-ssl-certs.bat
  exit /b 1
)
echo [OK] truststore.jks exists

docker exec ibmmq-dev chkmqready >nul 2>&1
if errorlevel 1 (
  echo [FAIL] Queue manager not ready
  exit /b 1
)
echo [OK] Queue manager ready

echo Checking channel SSL cipher ...
docker exec ibmmq-dev runmqsc QM.SU000423 -c "DISPLAY CHANNEL(DBTAX.VE.SVRCONN) SSLCIPH" | findstr /i "ECDHE_RSA_AES_256_GCM_SHA384" >nul
if errorlevel 1 (
  echo [WARN] Channel may not have ECDHE_RSA_AES_256_GCM_SHA384 - run docker\setup-ssl-local.bat
) else (
  echo [OK] Channel cipher ECDHE_RSA_AES_256_GCM_SHA384
)

if exist "docker\ssl\truststore-path.txt" (
  echo [OK] Truststore path file:
  type docker\ssl\truststore-path.txt
)

echo.
echo Use mq-config: docker\mq-config.ssl-local-docker.json
echo   ssl: true
echo   sslCipherSpec: ECDHE_RSA_AES_256_GCM_SHA384
echo   useIbmCipherMappings: false
echo   preferTls: true
echo   sslTrustStore + sslTrustStorePassword: changeit
echo.
endlocal
