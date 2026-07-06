@echo off
setlocal EnableExtensions

set "DOCKER_EXE=docker"
set "PORT_FILE=%~dp0.n8n-port"
set "REQUESTED_N8N_HOST_PORT=%N8N_HOST_PORT%"
set "N8N_HOST_PORT="
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

if defined REQUESTED_N8N_HOST_PORT (
  call :select_port %REQUESTED_N8N_HOST_PORT%
) else (
  for %%p in (5678 6789 15678) do call :select_port %%p
)
if not defined N8N_HOST_PORT (
  echo [ERROR] No available n8n host port found.
  echo [HINT] Free one of these ports or set N8N_HOST_PORT before running this script: 5678, 6789, 15678
  pause
  exit /b 1
)
echo [INFO] Using host port %N8N_HOST_PORT% for n8n.
> "%PORT_FILE%" echo %N8N_HOST_PORT%

echo [INFO] Removing old n8n container if it exists...
"%DOCKER_EXE%" rm -f n8n >nul 2>nul

echo [INFO] Starting n8n container...
"%DOCKER_EXE%" run -d --name n8n -p %N8N_HOST_PORT%:5678 -e N8N_EDITOR_BASE_URL=http://localhost:%N8N_HOST_PORT% -e WEBHOOK_URL=http://localhost:%N8N_HOST_PORT%/ -v "%USERPROFILE%\.n8n:/home/node/.n8n" docker.n8n.io/n8nio/n8n:latest
if errorlevel 1 (
  echo [ERROR] Failed to start n8n container.
  pause
  exit /b 1
)

echo [INFO] Waiting for n8n...
timeout /t 5 /nobreak >nul
start "" "http://localhost:%N8N_HOST_PORT%"

echo.
echo [OK] n8n started at http://localhost:%N8N_HOST_PORT%
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

:select_port
if defined N8N_HOST_PORT exit /b 0
set "CANDIDATE_PORT=%~1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$port = [int]$env:CANDIDATE_PORT; $excluded = netsh interface ipv4 show excludedportrange protocol=tcp | Select-String '^\s*(\d+)\s+(\d+)' | ForEach-Object { $m = [regex]::Match($_.Line, '^\s*(\d+)\s+(\d+)'); [pscustomobject]@{ Start = [int]$m.Groups[1].Value; End = [int]$m.Groups[2].Value } }; $isExcluded = @($excluded | Where-Object { $port -ge $_.Start -and $port -le $_.End }).Count -gt 0; $inUse = $null -ne (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue); if ($isExcluded -or $inUse) { exit 1 } else { exit 0 }" >nul 2>nul
if not errorlevel 1 set "N8N_HOST_PORT=%CANDIDATE_PORT%"
exit /b 0
