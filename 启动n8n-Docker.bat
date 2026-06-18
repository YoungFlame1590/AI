@echo off
setlocal EnableExtensions

set "DOCKER_EXE=docker"
where docker >nul 2>nul
if not errorlevel 1 goto found_docker

if exist "C:\Program Files\Docker\Docker\resources\bin\docker.exe" (
  set "DOCKER_EXE=C:\Program Files\Docker\Docker\resources\bin\docker.exe"
  goto found_docker
)

echo [ERROR] docker.exe was not found. Please install Docker Desktop first.
pause
exit /b 1

:found_docker
echo [INFO] Checking Docker Desktop...
"%DOCKER_EXE%" info >nul 2>nul
if not errorlevel 1 goto docker_ready

if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" (
  echo [INFO] Starting Docker Desktop...
  start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
)

set "READY="
for /L %%i in (1,1,60) do call :wait_for_docker %%i
if defined READY goto docker_ready

echo [ERROR] Docker engine is not ready.
echo [HINT] Open Docker Desktop and make sure WSL2 is enabled.
echo [HINT] If WSL2 was just enabled, restart Windows and run this script again.
pause
exit /b 1

:docker_ready
echo [INFO] Docker is ready.

echo [INFO] Removing old n8n container if it exists...
"%DOCKER_EXE%" rm -f n8n >nul 2>nul

echo [INFO] Starting n8n container...
"%DOCKER_EXE%" run -d --name n8n -p 5678:5678 -v "%USERPROFILE%\.n8n:/home/node/.n8n" docker.n8n.io/n8nio/n8n:latest
if errorlevel 1 (
  echo [ERROR] Failed to start n8n container.
  pause
  exit /b 1
)

echo [INFO] Waiting for n8n...
timeout /t 5 /nobreak >nul
start "" "http://localhost:5678"

echo.
echo [OK] n8n started at http://localhost:5678
echo [TIP] In the n8n workflow, use this agentBaseUrl:
echo       http://host.docker.internal:8000
echo.
pause
exit /b 0

:wait_for_docker
if defined READY exit /b 0
"%DOCKER_EXE%" info >nul 2>nul
if not errorlevel 1 (
  set "READY=1"
  exit /b 0
)
echo [INFO] Waiting for Docker engine... %1/60
timeout /t 3 /nobreak >nul
exit /b 0
