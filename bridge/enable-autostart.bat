@echo off
REM ============================================================
REM  AzurRem - enable auto-start of the data bridge
REM
REM  Drops a shortcut into the current user's Startup folder,
REM  so the bridge comes up by itself after you log in.
REM
REM  No administrator rights needed, and nothing is written into
REM  the AzurPilot folder. Undo with disable-autostart.bat.
REM
REM  It targets dist\AzurRemBridge.exe (the single-file build).
REM  The old version pointed at a start_bridge.bat that no longer
REM  exists, so the shortcut silently did nothing -- fixed here.
REM ============================================================
setlocal
chcp 65001 >nul

set "HERE=%~dp0"
set "ROOTDIR=%HERE%.."
set "EXE=%ROOTDIR%\dist\AzurRemBridge.exe"
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
set "LNK=%STARTUP%\AzurRem Bridge.lnk"

if not exist "%EXE%" (
  echo [ERROR] %EXE% not found.
  echo         Build it first by running bridge\build-exe.bat
  echo         ^(or use "..\先在电脑上双击运行这个.bat" once to check the setup^).
  pause
  exit /b 2
)

REM WindowStyle 7 = minimized: the bridge keeps running, the console
REM window just stays out of the way in the taskbar.
powershell -NoProfile -Command ^
  "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('%LNK%');" ^
  "$s.TargetPath='%EXE%';" ^
  "$s.Arguments='--port 25550';" ^
  "$s.WorkingDirectory='%ROOTDIR%';" ^
  "$s.WindowStyle=7;" ^
  "$s.Description='AzurRem data bridge (AzurRemBridge.exe)';" ^
  "$s.Save()"

if exist "%LNK%" (
  echo [OK] Auto-start enabled. The bridge will run after every logon.
  echo      Shortcut : %LNK%
  echo      Target   : %EXE%
  echo.
  echo      Note: it still only runs while you are logged in.
  echo      To stop it for one session: close its window
  echo      ^(or kill AzurRemBridge.exe in Task Manager^).
) else (
  echo [ERROR] Failed to create the shortcut.
)
echo.
pause
endlocal
