@echo off
setlocal EnableExtensions

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\ccb-approve.ps1"

echo.
pause
