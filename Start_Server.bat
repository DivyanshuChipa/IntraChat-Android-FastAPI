@echo off
setlocal enabledelayedexpansion
title INTRA LAN SERVER

REM ===== Move to script directory =====
cd /d "%~dp0"

echo ======================================
echo        INTRA LAN SERVER LAUNCHER
echo ======================================
echo.

REM ===== Backend folder check =====
set BACKEND_DIR=%cd%\backend

if not exist "%BACKEND_DIR%" (
 echo [ERROR] Backend folder not found!
 echo Expected location: %BACKEND_DIR%
 pause
 exit /b
)

cd /d "%BACKEND_DIR%"

echo [1/5] Checking Python installation...

python --version >nul 2>&1
if errorlevel 1 (
 echo.
 echo [ERROR] Python is not installed.
 echo Please install Python from:
 echo https://www.python.org/downloads/
 pause
 exit /b
)

echo Python detected.
echo.

REM ===== External Tools Check (FFmpeg, Tesseract, Ghostscript) =====
echo [1.5/5] Checking External Tools...

winget --version >nul 2>&1
if errorlevel 1 (
    echo [!] winget not found. Please install FFmpeg, Tesseract, and Ghostscript manually.
    goto :venv_start
)

where ffmpeg >nul 2>&1
if errorlevel 1 (
    echo [!] FFmpeg missing. Installing via winget...
    winget install --id=Gyan.FFmpeg -e --silent --accept-source-agreements --accept-package-agreements
    if errorlevel 0 echo [OK] FFmpeg installed. Please restart script if it fails later.
) else (
    echo [+] FFmpeg detected.
)

where tesseract >nul 2>&1
if errorlevel 1 (
    echo [!] Tesseract missing. Installing via winget...
    winget install --id=UB-Mannheim.TesseractOCR -e --silent --accept-source-agreements --accept-package-agreements
    if errorlevel 0 echo [OK] Tesseract installed.
) else (
    echo [+] Tesseract detected.
)

where gswin64c >nul 2>&1
if errorlevel 1 (
    echo [!] Ghostscript missing. Installing via winget...
    winget install --id=ArtifexSoftware.Ghostscript -e --silent --accept-source-agreements --accept-package-agreements
    if errorlevel 0 echo [OK] Ghostscript installed.
) else (
    echo [+] Ghostscript detected.
)

:venv_start

echo.

REM ===== Create virtual environment =====
if not exist venv (
 echo [2/5] Creating virtual environment...
 python -m venv venv
) else (
 echo [2/5] Virtual environment already exists.
)

echo.

REM ===== Activate venv =====
echo [3/5] Activating virtual environment...
call venv\Scripts\activate

echo.

REM ===== Install dependencies =====
echo [4/5] Installing dependencies...
pip install --upgrade pip >nul
pip install -r requirements.txt

echo.

REM ===== Get local IP =====
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr "IPv4 Address"') do (
 set IP=%%a
)

set IP=!IP: =!

echo ======================================
echo        SERVER STARTING
echo ======================================
echo.
echo Local access:
echo http://localhost:8000
echo.
echo LAN access:
echo http://!IP!:8000
echo.
echo Android app me ye IP daalo:
echo !IP!
echo.

REM ===== Open browser =====
start http://localhost:8000

echo [5/5] Starting Intra Backend Server...
echo.

python run_server.py

pause