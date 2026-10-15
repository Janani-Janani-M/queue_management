@echo off
REM ============================================================
REM  QueueCare – Start Backend with MySQL Database
REM  Ensures MySQL is running and boots Spring Boot on port 8080
REM ============================================================

title QueueCare Backend (Spring Boot + MySQL)
color 0A

echo ============================================================
echo   QueueCare - DSA Queue Management System (Backend)
echo   Connecting to MySQL (queuecare_db) on localhost:3306
echo ============================================================
echo.

REM 1. Check if MySQL is running
netstat -ano | findstr 3306 >nul
if %errorlevel% neq 0 (
    echo [WARNING] Port 3306 does not appear to be listening.
    echo Please ensure XAMPP MySQL or your MySQL service is started.
    echo.
) else (
    echo [OK] MySQL is active on port 3306.
)

REM 2. Locate Maven executable
set MVN_BIN=mvn
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    if exist "D:\dsa_proj\.maven\apache-maven-3.9.6\bin\mvn.cmd" (
        set "MVN_BIN=D:\dsa_proj\.maven\apache-maven-3.9.6\bin\mvn.cmd"
    ) else (
        echo [ERROR] Maven not found on PATH or in D:\dsa_proj\.maven.
        echo Please ensure Maven or Java is installed.
        pause
        exit /b 1
    )
)

echo [OK] Using Maven: %MVN_BIN%
echo.
echo Starting Spring Boot backend on http://localhost:8080 ...
echo Press Ctrl+C anytime to stop.
echo ============================================================
echo.

cd /d "%~dp0backend"
call "%MVN_BIN%" spring-boot:run
pause
