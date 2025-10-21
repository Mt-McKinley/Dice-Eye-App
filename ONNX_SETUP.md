# ONNX Runtime Setup Instructions

## Current Status
Your app is configured to use ONNX Runtime for dice detection with your `my_model.onnx` file.
If the model isn't loading, it means the ONNX Runtime library hasn't been downloaded by Gradle yet.

## Quick Fix (Choose ONE method)

### Method 1: Run the Force Sync Script (RECOMMENDED)
1. Close Android Studio completely
2. Double-click `FORCE_SYNC.bat` in the project root folder
3. Wait for it to complete (it will download the ONNX Runtime library)
4. Reopen Android Studio
5. Build and run your app

### Method 2: Invalidate Caches in Android Studio
1. In Android Studio, go to: **File → Invalidate Caches...**
2. Check "Clear file system cache and Local History"
3. Check "Clear downloaded shared indexes"
4. Click **"Invalidate and Restart"**
5. Wait for Android Studio to restart and re-index
6. Build and run your app

### Method 3: Manual Gradle Sync
1. In Android Studio, click: **File → Sync Project with Gradle Files**
2. Wait for the sync to complete (watch the bottom status bar)
3. If you see any errors, click "Try Again"
4. Once sync is successful, click: **Build → Rebuild Project**
5. Run your app

## How to Check if ONNX Runtime is Working

When you run your app, open **Logcat** in Android Studio:
1. Filter by: `DiceDetector`
2. Look for one of these messages:

### ✓ Success - You'll see:
```
✓ ONNX Runtime classes found!
✓ ONNX model loaded successfully!
Model inputs: [images]
Model outputs: [output0]
```

### ✗ Not Working Yet - You'll see:
```
✗ ONNX Runtime NOT FOUND - ClassNotFoundException
```
With a box showing instructions on what to do.

## What Changed

1. **build.gradle.kts** - Now uses ONNX Runtime from the version catalog
2. **DiceDetector.kt** - Uses reflection to load ONNX Runtime dynamically
3. **GameScreen.kt** - Switched from SimpleDiceDetector to DiceDetector

## Fallback Behavior

Until ONNX Runtime loads properly, the app will:
- Still work and let you test the UI
- Generate mock/random dice detections (1-3 random dice)
- Show warnings in Logcat

Once ONNX Runtime is loaded, it will automatically:
- Use your real `my_model.onnx` for inference
- Detect actual dice in camera images
- Stop showing warnings

## Your Model File
Location: `app/src/main/assets/my_model.onnx`
Status: ✓ File exists and is in the correct location

## Troubleshooting

### Still not working after trying all methods?
1. Check your internet connection (Gradle needs to download ~20MB)
2. Try deleting these folders and re-syncing:
   - `.gradle` folder in project root
   - `.idea` folder in project root  
   - `app/build` folder
3. In Android Studio: **File → Project Structure → Dependencies**
   - Look for "onnxruntime-android:1.17.0"
   - If it's not there or shows as red, the download failed

### Need to see detailed diagnostics?
Look for "OnnxDiagnostics" in Logcat - it runs a full test on app startup.

## Why This Approach?

Using reflection allows the code to:
- Compile even if ONNX Runtime isn't synced yet (no red errors in IDE)
- Automatically detect when ONNX Runtime becomes available
- Provide clear feedback about what's wrong and how to fix it
- Work with mock data until the real library loads

Once Gradle successfully syncs and downloads ONNX Runtime, everything will work automatically!

