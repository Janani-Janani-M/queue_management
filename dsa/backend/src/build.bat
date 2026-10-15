@echo off
REM ============================================================
REM  QueueCare Build Script (Windows)
REM  Run from: backend\src\
REM ============================================================

echo [1/3] Cleaning output directory...
if exist out rmdir /s /q out
mkdir out

echo [2/3] Compiling all sources...
javac -d out ^
  model\Citizen.java ^
  dsa\CustomQueue.java ^
  dsa\CitizenPriorityQueue.java ^
  dsa\PriorityQueue.java ^
  dsa\QueueScheduler.java ^
  dto\RegisterRequest.java ^
  dto\CitizenResponse.java ^
  dto\QueueStatsResponse.java ^
  dto\ApiResponse.java ^
  service\QueueService.java ^
  controller\QueueController.java ^
  QueueCareApplication.java

if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed.
    exit /b %errorlevel%
)

echo [3/3] Compilation successful.  Running self-test...
echo.
java -cp out QueueCareApplication
