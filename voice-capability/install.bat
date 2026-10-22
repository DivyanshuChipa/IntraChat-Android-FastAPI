@echo off
REM ==============================================================================
REM 🎙️ Install Voice-Capability on Windows
REM ==============================================================================

echo ==========================================================
echo   Installing Voice-Capability Engine (Windows)
echo ==========================================================

REM Check Python
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python is not installed or not in PATH!
    pause
    exit /b 1
)

REM Create Virtual Environment
if not exist "venv" (
    echo Setting up Python Virtual Environment...
    python -m venv venv
)

echo Activating Virtual Environment...
call venv\Scripts\activate.bat

echo Upgrading pip...
python -m pip install --upgrade pip

echo Installing requirements...
pip install -r requirements.txt

echo.
echo ==========================================================
echo   Voice-Capability installed successfully!
echo   Run 'start.bat' to launch the service on port 8005.
echo ==========================================================
pause
