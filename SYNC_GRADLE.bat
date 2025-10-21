@echo off
echo ========================================
echo Syncing Gradle and Building Project
echo ========================================
echo.

cd /d "%~dp0"

echo Stopping Gradle daemon...
call gradlew --stop
echo.

echo Cleaning build...
call gradlew clean
echo.

echo Syncing dependencies...
call gradlew --refresh-dependencies
echo.

echo Building project...
call gradlew build
echo.

echo ========================================
echo Done! Now open the project in Android Studio
echo and it should recognize the ONNX Runtime library.
echo ========================================
pause

