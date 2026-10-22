@echo off
REM ==============================================================================
REM 🚀 Start Voice-Capability Service on Windows
REM ==============================================================================

cd /d "%~dp0"

if not exist "venv\Scripts\activate.bat" (
    echo [WARNING] Virtual environment not found. Running install.bat first...
    call install.bat
)

call venv\Scripts\activate.bat

set PORT=8005
if not "%1"=="" set PORT=%1

echo ==========================================================
echo   Starting Voice-Capability Node on http://0.0.0.0:%PORT%
echo ==========================================================

python voice_server.py --host 0.0.0.0 --port %PORT%
pause
