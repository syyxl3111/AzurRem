@echo off
REM ============================================================
REM  AzurRemBridge - build the single-file GUI exe
REM
REM  Double-click this file (or run it from a terminal) to rebuild
REM  dist\AzurRemBridge.exe from bridge\mobile_bridge.py.
REM
REM  Flags that matter:
REM    --onefile     -> one .exe, nothing else to ship
REM    --noconsole   -> NO console window. The bridge is a tkinter
REM                     widget now, so a black console next to it
REM                     would be pure noise. Equivalent to --windowed.
REM                     Consequence: sys.stdout / sys.stderr are None
REM                     at runtime, so every message goes to
REM                     dist\AzurRemBridge.log instead of print().
REM    --icon        -> exe icon, generated from image\ by make-icon.py
REM    --add-data    -> same .ico packed inside, used for the widget's
REM                     own window / taskbar icon at runtime
REM    --noconfirm   -> overwrite the previous output without asking
REM
REM  NOTE 0: the tray icon (blue dot -> "hide to the notification area")
REM          is raw ctypes / Shell_NotifyIconW inside mobile_bridge.py.
REM          That means NO third-party packages are needed here: no
REM          pystray, no Pillow, no six, and therefore no
REM          --hidden-import / --collect-all / --copy-metadata entries.
REM          If someone ever swaps that for pystray, add the flags to the
REM          pyinstaller call at the bottom of this file.
REM          Do NOT go looking for AzurRemBridge.spec to edit: it is a
REM          generated artifact (--specpath + --noconfirm rewrite it on
REM          every build) and it is gitignored -- it used to be committed,
REM          and it carried the builder's absolute path (username included)
REM          into the repo.
REM
REM  NOTE 1: keep the pyinstaller call on ONE line. A "^" continued
REM          command is fragile in .bat and the failure mode is a
REM          confusing "scriptname required" error from pyinstaller.
REM  NOTE 2: %HERE% ends with a backslash, so "%HERE%" expands to
REM          "...\bridge\" and that \" escapes the closing quote --
REM          cmd then swallows the rest of the command line and
REM          pyinstaller reports "scriptname required". Always write
REM          "%HERE%." (or "%HERE%something") instead.
REM
REM  Output: ..\dist\AzurRemBridge.exe
REM ============================================================
setlocal
chcp 65001 >nul

set "HERE=%~dp0"
set "OUTDIR=%HERE%..\dist"
set "ICON=%HERE%azurrem.ico"

REM Python interpreter: %AZURREM_PYTHON% if you set it, else whatever "python"
REM resolves to on PATH. Deliberately NOT hard-coded to a specific drive --
REM that would leak the builder's machine layout into the repo.
set "PY=%AZURREM_PYTHON%"
if not defined PY set "PY=python"

echo.
echo   Building %OUTDIR%\AzurRemBridge.exe
echo   Python: %PY%
echo.

"%PY%" -c "import PyInstaller, sys; print('   PyInstaller', PyInstaller.__version__, '/ Python', sys.version.split()[0])"
if errorlevel 1 (
  echo   [X] PyInstaller not available for %PY%
  echo       Install it with:  "%PY%" -m pip install pyinstaller
  pause
  exit /b 2
)

REM Icon: generated from image\ by Pillow (multi-size 16..256)
if not exist "%ICON%" (
  echo   Generating %ICON% from ..\image\ ...
  "%PY%" "%HERE%make-icon.py"
  if errorlevel 1 (
    echo   [!] Icon generation failed - building without an icon
    set "ICON="
  )
)

"%PY%" -m PyInstaller --noconfirm --clean --onefile --noconsole --name AzurRemBridge --icon "%ICON%" --add-data "%ICON%;." --distpath "%OUTDIR%" --workpath "%HERE%build" --specpath "%HERE%." "%HERE%mobile_bridge.py"

set "RC=%ERRORLEVEL%"
echo.
if not "%RC%"=="0" (
  echo   [X] Build failed with exit code %RC%
  pause
  exit /b %RC%
)

for %%F in ("%OUTDIR%\AzurRemBridge.exe") do set "SIZE=%%~zF"
echo   [OK] Built: %OUTDIR%\AzurRemBridge.exe  (%SIZE% bytes)
echo        mode: GUI (--noconsole) + icon %ICON%
echo        runtime log: %OUTDIR%\AzurRemBridge.log
echo.
echo   Smoke test, all 8 endpoints on port 25561 (headless, no window):
echo       "%PY%" "%HERE%smoke_test_exe.py" --port 25561
echo   Widget self-test (buttons, sizes, tray, minimize, close stops the bridge):
echo       "%PY%" "%HERE%gui_test.py"
echo   Same widget self-test, but driving the REAL exe (tray round-trip cross-process):
echo       "%PY%" "%HERE%tray_test_exe.py" --port 25565
echo   Parity check of the ported ship-exp code (needs the project venv):
echo       "%AZURPILOT_ROOT%\.venv\Scripts\python.exe" "%HERE%verify_ship_exp_parity.py"
echo.
endlocal
exit /b 0
