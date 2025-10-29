@echo off
echo ========================================
echo   Replace Detection Model with YOLO11s
echo ========================================
echo.

set SOURCE=C:\Users\disne\Downloads\d6Training\my_model\model_float32.tflite
set DEST=C:\Users\disne\AndroidStudioProjects\DiceEyeApp\app\src\main\assets\die_detection.tflite

echo Source: %SOURCE%
echo Destination: %DEST%
echo.

if not exist "%SOURCE%" (
    echo ERROR: Source file not found!
    echo Please verify the path to model_float32.tflite
    pause
    exit /b 1
)

echo Backing up old detection model...
if exist "%DEST%" (
    copy "%DEST%" "%DEST%.backup"
    echo Backup created: %DEST%.backup
)

echo.
echo Copying YOLO11s model to detection...
copy /Y "%SOURCE%" "%DEST%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ SUCCESS! Detection model replaced.
    echo.
    echo File size:
    dir "%DEST%" | find "model_float32"
    echo.
    echo Next steps:
    echo 1. Clean build: gradlew.bat clean
    echo 2. Build: gradlew.bat assembleDebug
    echo 3. Install and test
) else (
    echo.
    echo ❌ FAILED to copy model
    echo Please check file paths and try again
)

echo.
pause

