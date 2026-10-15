@echo off
REM ============================================================
REM  QueueCare - One-Click Launcher
REM  Launches Spring Boot Backend (Port 8080) and Web App (Port 3000)
REM ============================================================

title QueueCare Launcher
color 0B

echo ============================================================
echo   QueueCare - Accessible Queue Management System
echo ============================================================
echo.

REM 1. Check MySQL
netstat -ano | findstr 3306 >nul
if %errorlevel% neq 0 (
    echo [!] Warning: MySQL port 3306 is not detected.
    echo     Please make sure XAMPP / MySQL is running.
) else (
    echo [OK] MySQL is active on port 3306.
)

REM 2. Check Backend (Port 8080)
netstat -ano | findstr 8080 >nul
if %errorlevel% neq 0 (
    echo [*] Starting Spring Boot Backend in background...
    start "QueueCare Backend" /min cmd /c "start-backend.bat"
    timeout /t 3 >nul
) else (
    echo [OK] Backend is already running on port 8080.
)

REM 3. Check Frontend Web Server (Port 3000)
netstat -ano | findstr 3000 >nul
if %errorlevel% neq 0 (
    echo [*] Starting local web server on port 3000...
    start "QueueCare Web" /min cmd /c "python -m http.server 3000"
    timeout /t 1 >nul
) else (
    echo [OK] Frontend web server is active on port 3000.
)

REM 4. Open in Default Browser
echo.
echo [OK] Opening QueueCare Citizen Portal in your browser...
start http://localhost:3000/index.html#sec-portal

echo.
echo ============================================================
echo   QueueCare is running:
echo   - Web App & Portal:  http://localhost:3000
echo   - Citizen Portal:    http://localhost:3000/index.html#sec-portal
echo   - Backend REST API:  http://localhost:8080/api/queue/stats
echo ============================================================
echo.
pause
