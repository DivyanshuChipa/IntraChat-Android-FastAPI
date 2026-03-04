@echo off
title Intra Backend Server
cd backend
echo ---------------------------------------------------
echo Checking Python installation...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Python is not installed or not in PATH.
    echo Please install Python from python.org
    pause
    exit /b
)

:: Check for Virtual Environment
if not exist "venv" (
    echo Creating Virtual Environment (venv)...
    python -m venv venv
)

echo Activating Virtual Environment...
call venv\Scripts\activate

echo Checking/Installing Dependencies...
pip install -r requirements.txt

echo Starting Intra Backend...
python run_server.py
pause
