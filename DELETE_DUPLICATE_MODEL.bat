@echo off
echo ========================================
echo   Consolidate to Single YOLO Model
echo ========================================
echo.

set MODEL_FILE=C:\Users\disne\AndroidStudioProjects\DiceEyeApp\app\src\main\assets\die_detection.tflite

echo Code updated to use "die_classifier.tflite" for both detection and classification.
echo.
echo Deleting duplicate file: die_detection.tflite
echo.

if exist "%MODEL_FILE%" (
    del "%MODEL_FILE%"
    echo ✅ Deleted: die_detection.tflite
    echo.
    echo Now you have only one model file:
    echo   - die_classifier.tflite (used for BOTH detection and classification)
) else (
    echo File not found: die_detection.tflite
    echo Already deleted?
)

echo.
echo Next steps:
echo 1. Rebuild: gradlew.bat clean assembleDebug
echo 2. Test with 6 dice
echo.
pause

