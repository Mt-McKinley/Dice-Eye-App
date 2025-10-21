@echo off
echo ================================================
echo   CLEAN REBUILD FOR ONNX RUNTIME
echo ================================================
echo.
echo This script will:
echo 1. Stop all Gradle daemons
echo 2. Clean the project
echo 3. Delete build caches
echo 4. Rebuild everything fresh
echo.
pause

cd /d "%~dp0"

echo.
echo [1/5] Stopping Gradle daemon...
call gradlew.bat --stop

echo.
echo [2/5] Cleaning project...
call gradlew.bat clean

echo.
echo [3/5] Deleting build directories...
if exist "app\build" rmdir /s /q "app\build"
if exist "build" rmdir /s /q "build"
if exist ".gradle" rmdir /s /q ".gradle"

echo.
echo [4/5] Verifying model file exists...
if exist "app\src\main\assets\my_model.onnx" (
    echo ✓ Model file found: app\src\main\assets\my_model.onnx
    for %%A in ("app\src\main\assets\my_model.onnx") do echo    Size: %%~zA bytes
) else (
    echo ✗ ERROR: Model file NOT found!
    echo    Expected location: app\src\main\assets\my_model.onnx
    pause
    exit /b 1
)

echo.
echo [5/5] Building project...
call gradlew.bat assembleDebug --stacktrace

echo.
echo ================================================
echo   BUILD COMPLETE
echo ================================================
echo.
echo Next steps:
echo 1. Open Android Studio
echo 2. Let it finish indexing
echo 3. Run the app
echo.
echo Check the logcat for detailed ONNX loading logs.
echo Look for "DiceDetector" tag messages.
echo.
pause

