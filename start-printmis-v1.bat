@echo off
setlocal

cd /d "%~dp0generated-code\printshop-v1"

echo [1/3] Checking Docker...
where docker >nul 2>nul
if errorlevel 1 goto demo

echo [2/3] Starting MySQL on 127.0.0.1:13306...
docker compose up -d mysql
if errorlevel 1 goto demo

echo [3/3] Starting Print MIS with MySQL...
start "" "http://127.0.0.1:8080/"
mvn spring-boot:run
exit /b %errorlevel%

:demo
echo [WARN] Docker MySQL is unavailable. Starting with local H2 demo profile.
echo [INFO] Demo data is stored under generated-code\printshop-v1\target.
start "" "http://127.0.0.1:8080/"
mvn spring-boot:run -Dspring-boot.run.profiles=demo
exit /b %errorlevel%
