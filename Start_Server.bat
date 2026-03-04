@echo off
setlocal enabledelayedexpansion
title Intra Backend Server

set "SCRIPT_DIR=%~dp0"
set "BACKEND_DIR=%SCRIPT_DIR%backend"
set "VENV_DIR=%BACKEND_DIR%\venv"

if not exist "%BACKEND_DIR%" (
    echo [ERROR] backend folder not found: %BACKEND_DIR%
    pause
    exit /b 1
)

cd /d "%BACKEND_DIR%"

echo ---------------------------------------------------
echo Checking Python installation...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in PATH.
    echo Install Python from https://www.python.org/downloads/
    pause
    exit /b 1
)

if not exist "%VENV_DIR%" (
    echo Creating virtual environment (venv)...
    python -m venv "%VENV_DIR%"
    if %errorlevel% neq 0 (
        echo [ERROR] Failed to create virtual environment.
        pause
        exit /b 1
    )
)

echo Activating virtual environment...
call "%VENV_DIR%\Scripts\activate.bat"
if %errorlevel% neq 0 (
    echo [ERROR] Failed to activate virtual environment.
    pause
    exit /b 1
)

echo Upgrading pip + installing requirements...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
if %errorlevel% neq 0 (
    echo [ERROR] Dependency installation failed.
    pause
    exit /b 1
)

if /I "%~1"=="--background" goto :run_background

echo Starting Intra Backend in this terminal...
python run_server.py
pause
exit /b 0

:run_background
echo Starting Intra Backend in background window...
start "Intra Backend" /min cmd /c "cd /d %BACKEND_DIR% && call %VENV_DIR%\Scripts\activate.bat && python run_server.py"
echo [OK] Backend started in background window.
echo Use Task Manager to stop python.exe if needed.
exit /b 0
