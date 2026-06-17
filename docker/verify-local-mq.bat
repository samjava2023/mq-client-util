@echo off
setlocal

cd /d "%~dp0.."

echo ============================================
echo IBM MQ local config verification
echo ============================================
echo.

docker version >nul 2>&1
if errorlevel 1 (
  echo [FAIL] Docker is not running. Start Docker Desktop first.
  exit /b 1
)
echo [OK] Docker client is available

docker ps --filter name=ibmmq-dev --filter status=running --format "{{.Names}}" | findstr /i ibmmq-dev >nul
if errorlevel 1 (
  echo [FAIL] Container ibmmq-dev is not running.
  echo       Run: docker\start-local-mq.bat
  exit /b 1
)
echo [OK] Container ibmmq-dev is running

docker exec ibmmq-dev chkmqready >nul 2>&1
if errorlevel 1 (
  echo [FAIL] Queue manager is not ready yet. Wait and retry.
  exit /b 1
)
echo [OK] Queue manager is ready
echo.

set QMGR=QM.SU000423
set CHANNEL=DBTAX.VE.SVRCONN
set QNAME=PWM.VE.RESPONSE.QUEUE

echo Checking queue manager name...
docker exec ibmmq-dev dspmq | findstr /i "%QMGR%" >nul
if errorlevel 1 (
  echo [FAIL] Expected queue manager %QMGR% not found.
) else (
  echo [OK] Queue manager %QMGR% is active
)

echo Checking channel %CHANNEL%...
docker exec ibmmq-dev runmqsc %QMGR% -c "DISPLAY CHANNEL(%CHANNEL%)" | findstr /i "%CHANNEL%" >nul
if errorlevel 1 (
  echo [FAIL] Channel %CHANNEL% not found.
) else (
  echo [OK] Channel %CHANNEL% exists
)

echo Checking queue %QNAME%...
docker exec ibmmq-dev runmqsc %QMGR% -c "DISPLAY QLOCAL(%QNAME%)" | findstr /i "%QNAME%" >nul
if errorlevel 1 (
  echo [FAIL] Queue %QNAME% not found.
) else (
  echo [OK] Queue %QNAME% exists
)

echo Checking listener port mapping localhost(1423)...
netstat -an | findstr ":1423" >nul
if errorlevel 1 (
  echo [WARN] Port 1423 is not listening on host. Check docker port mapping.
) else (
  echo [OK] Port 1423 is listening on host
)

echo.
echo --------------------------------------------
echo Config compatibility summary
echo --------------------------------------------
echo Supported locally (use docker\mq-config.local.json):
echo   queueManager     = QM.SU000423
echo   channel          = DBTAX.VE.SVRCONN
echo   connectionName   = localhost(1423)
echo   qname            = PWM.VE.RESPONSE.QUEUE
echo   queueType        = ibm.mq
echo   username/password= app / passw0rd
echo.
echo NOT supported on plain local Docker (needs real MQ + TLS):
echo   ssl              = true
echo   sslCipherSpec    = ECDHE_RSA_AES_256_GCM_SHA384
echo.
echo For local testing, copy:
echo   docker\mq-config.local.json
echo to your app:
echo   src\main\resources\mq-config.json
echo (ssl disabled for local dev)
echo.

endlocal
