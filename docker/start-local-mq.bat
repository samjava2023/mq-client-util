@echo off
setlocal

cd /d "%~dp0.."

echo Starting local IBM MQ (QM1) on port 1414...
echo Web console: https://localhost:9443/ibmmq/console/
echo   admin user: admin / passw0rd
echo   app user:   app   / passw0rd
echo.

docker compose up -d
if errorlevel 1 (
  echo.
  echo Failed to start IBM MQ. Is Docker Desktop running?
  exit /b 1
)

echo Waiting for queue manager to be ready...
:wait
docker exec ibmmq-dev chkmqready >nul 2>&1
if errorlevel 1 (
  timeout /t 3 /nobreak >nul
  goto wait
)

echo.
echo IBM MQ is ready.
echo.
echo Connection details for mq-config.json:
echo   queueManager: QM.SU000423
echo   channel:      DBTAX.VE.SVRCONN
echo   connection:   localhost(1423)
echo   queue:        PWM.VE.RESPONSE.QUEUE
echo   user/pass:    app / passw0rd
echo.
echo Verify setup: docker\verify-local-mq.bat
echo Local config: docker\mq-config.local.json  (ssl=false for Docker)
echo.
echo To stop: docker compose down
endlocal
