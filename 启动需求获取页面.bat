@echo off
setlocal
chcp 65001 >nul

cd /d "%~dp0"
set APP_DIR=%~dp0agent-app
set REQ_FILE=%APP_DIR%\requirements.txt
set STAMP_FILE=%APP_DIR%\.venv\.requirements.stamp

echo [A1] Checking port 8000...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:"127.0.0.1:8000 .*LISTENING"') do (
  echo [A1] Closing old server process PID %%p on port 8000...
  taskkill /F /PID %%p >nul 2>nul
)

for /L %%i in (1,1,10) do (
  netstat -ano | findstr /R /C:"127.0.0.1:8000 .*LISTENING" >nul
  if errorlevel 1 goto port_free
  echo [A1] Waiting for port 8000 to be released...
  timeout /t 1 /nobreak >nul
)

echo [A1] Port 8000 is still occupied. Please close the process manually and retry.
pause
exit /b 1

:port_free
echo [A1] Port 8000 is free.

if not exist "%APP_DIR%\.venv\Scripts\python.exe" (
  echo [A1] Creating local Python virtual environment...
  python -m venv "%APP_DIR%\.venv"
  if errorlevel 1 (
    echo [A1] Failed to create virtual environment. Please check Python installation.
    pause
    exit /b 1
  )
)

echo [A1] Installing or checking dependencies...
"%APP_DIR%\.venv\Scripts\python.exe" -m pip install --upgrade pip
if errorlevel 1 (
  echo [A1] Failed to upgrade pip.
  pause
  exit /b 1
)

fc /b "%REQ_FILE%" "%STAMP_FILE%" >nul 2>nul
if errorlevel 1 (
  echo [A1] Requirements changed or not installed. Installing dependencies...
  "%APP_DIR%\.venv\Scripts\python.exe" -m pip install -r "%REQ_FILE%"
  if errorlevel 1 (
    echo [A1] Failed to install dependencies.
    pause
    exit /b 1
  )
  copy /y "%REQ_FILE%" "%STAMP_FILE%" >nul
) else (
  echo [A1] Dependencies are already installed.
)

echo [A1] Starting local server. Close this window to stop it.
start "" powershell -NoProfile -WindowStyle Hidden -Command "Start-Sleep -Seconds 3; Start-Process 'http://127.0.0.1:8000'"
cd /d "%APP_DIR%"
".venv\Scripts\python.exe" -m uvicorn app:app --host 127.0.0.1 --port 8000

endlocal
