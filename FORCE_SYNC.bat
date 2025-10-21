@echo off
echo ========================================
echo FORCE GRADLE SYNC FOR ONNX RUNTIME
echo ========================================
echo.

cd /d "%~dp0"

echo Step 1: Stopping all Gradle daemons...
call gradlew --stop
timeout /t 2 /nobreak >nul
echo.

echo Step 2: Cleaning build directories...
rmdir /s /q .gradle 2>nul
rmdir /s /q app\build 2>nul
rmdir /s /q build 2>nul
echo.

echo Step 3: Clearing Gradle cache for ONNX Runtime...
rmdir /s /q "%USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.microsoft.onnxruntime" 2>nul
echo.

echo Step 4: Refreshing dependencies...
call gradlew --refresh-dependencies
echo.

echo Step 5: Downloading dependencies explicitly...
call gradlew :app:dependencies --configuration debugRuntimeClasspath
echo.

echo Step 6: Building project...
call gradlew build -x test
echo.

echo ========================================
echo DONE!
echo ========================================
echo.
echo The ONNX Runtime library should now be downloaded.
echo Please open Android Studio and check if the errors are gone.
echo.
echo If errors persist, try:
echo 1. File -^> Invalidate Caches -^> Invalidate and Restart
echo 2. Close Android Studio
echo 3. Delete the .idea folder
echo 4. Reopen the project
echo.
pause

