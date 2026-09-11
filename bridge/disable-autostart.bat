@echo off
REM ============================================================
REM  AzurRem - disable auto-start of the data bridge
REM ============================================================
setlocal
chcp 65001 >nul

set "LNK=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\AzurRem Bridge.lnk"

if exist "%LNK%" (
  del "%LNK%"
  echo [OK] Auto-start disabled.
) else (
  echo [INFO] Auto-start was not enabled.
)
echo.
pause
endlocal
