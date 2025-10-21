# Dice Detection Troubleshooting Guide

## Problem: No detections showing despite dice in frame

### YOLOv11 Model Information

**Your model is YOLOv11** - This version has specific characteristics:
- **Output shape**: `[1, 10, 8400]` (batch, attributes, anchors)
- **Attributes**: 4 (bbox: cx, cy, w, h) + 6 (class scores) = 10 total
- **Coordinate format**: **NORMALIZED [0-1]** - This is critical!
- **No objectness score**: YOLOv11 removed the objectness confidence
- **Class scores**: Direct confidence per class (no multiplication needed)

### Most Common Issues & Solutions

#### 1. **Coordinate Format (MOST LIKELY ISSUE)**
YOLOv11 outputs **normalized coordinates [0-1]**, not pixel values!

**Correct conversion:**
```kotlin
// YOLOv11 outputs: cx, cy, w, h all in range [0-1]
// Step 1: Scale to 640x640 pixel space
val centerXPixel = centerX * 640
val centerYPixel = centerY * 640
val widthPixel = width * 640
val heightPixel = height * 640

// Step 2: Scale to original image dimensions
val scaleX = originalWidth / 640.0
val scaleY = originalHeight / 640.0
val left = (centerXPixel - widthPixel/2) * scaleX
val top = (centerYPixel - heightPixel/2) * scaleY
val right = (centerXPixel + widthPixel/2) * scaleX
val bottom = (centerYPixel + heightPixel/2) * scaleY
```

**What to check in logcat:**
```
DiceDetector: Tensor shape: [1, 10, 8400]
DiceDetector: Sample prediction (first):
DiceDetector:   attr[0] = 0.456  // Should be 0-1 range (normalized cx)
DiceDetector:   attr[1] = 0.512  // Should be 0-1 range (normalized cy)
DiceDetector:   attr[2] = 0.123  // Should be 0-1 range (normalized w)
DiceDetector:   attr[3] = 0.098  // Should be 0-1 range (normalized h)
DiceDetector:   attr[4] = 0.001  // Class 0 score
DiceDetector:   attr[5] = 0.823  // Class 1 score (if this is highest, it's class 1)
...
```

#### 2. **Low Confidence Scores**
If the model was trained well but outputs low confidence:
- ✅ **Check preprocessing**: YOLOv11 expects RGB input normalized to [0,1]
- ✅ **Current threshold**: Set to 0.01 (1%) for debugging
- ❌ **Don't use**: BGR format or [0,255] range

#### 3. **Model Export Issues**
Make sure you exported correctly:
```python
# Correct YOLOv11 export
from ultralytics import YOLO
model = YOLO('path/to/your/model.pt')
model.export(format='onnx', opset=21, simplify=True)
```

### Quick Diagnostic Steps

1. **Check the logs** when you capture:
   ```
   adb logcat DiceDetector:D *:S
   ```

2. **Look for these key lines:**
   - `Tensor shape:` - Should be `[1, 10, 8400]`
   - `MAX confidence found:` - Should be > 0.01 if dice is present
   - `[DEBUG] Detection candidate:` - Shows coordinates (should be 0-1 range)
   - `[DETECTION ADDED]` - Shows final detections in pixel coordinates

3. **If coordinates look wrong (e.g., > 1.0):**
   - Your model might have been quantized differently
   - Try looking at the actual values in the log

4. **If confidence is very low (< 0.01):**
   - Model might need different preprocessing
   - Check if training used different normalization

### Code Changes Made

I've updated `DiceDetector.kt` with:

1. ✅ **Lowered confidence threshold to 0.01 (1%)** - to catch any detections
2. ✅ **YOLOv11-specific coordinate handling** - properly converts normalized [0-1] coords
3. ✅ **Added extensive debug logging** - shows raw values and coordinate conversions
4. ✅ **Fixed coordinate scaling** - now correctly handles the 2-step conversion

### What the Fix Does

The key fix is in `processDetectionNormalized()`:

**BEFORE (Wrong):**
```kotlin
// Assumed coordinates were in pixels
val left = (centerX - width / 2) * scaleX
```

**AFTER (Correct for YOLOv11):**
```kotlin
// Step 1: Convert normalized [0-1] to 640x640 pixels
val centerXPixel = centerX * 640
// Step 2: Scale to original image size
val left = (centerXPixel - widthPixel / 2) * scaleX
```

### What to Do Next

1. **Rebuild and run the app** (the fix is already applied)
2. **Point camera at a dice**
3. **Capture an image**
4. **Check Android Studio Logcat** (filter by "DiceDetector")
5. **Look for:**
   - `[DEBUG] Detection candidate:` with coordinates in 0-1 range
   - `[DETECTION ADDED]` with final pixel coordinates
   - `MAX confidence found:` should be high if dice is visible

### Expected Log Output (Good Case)

```
DiceDetector: Tensor shape: [1, 10, 8400]
DiceDetector: Sample prediction (first):
DiceDetector:   attr[0] = 0.512
DiceDetector:   attr[1] = 0.498
DiceDetector:   attr[2] = 0.156
DiceDetector:   attr[3] = 0.143
DiceDetector: [DEBUG] Detection candidate: conf=0.87, class=3, bbox=[0.512, 0.498, 0.156, 0.143] (normalized)
DiceDetector: [DETECTION ADDED] conf=0.87, class=3, bbox=[450, 380, 550, 480] (pixels)
DiceDetector: MAX confidence found: 0.87
DiceDetector: Detected 1 dice
```

### If Still No Detections After This Fix

Please share your logcat output including:
1. The `Tensor shape:` line
2. The `Sample prediction (first):` section
3. Any `[DEBUG] Detection candidate:` lines
4. The `MAX confidence found:` line

This will tell us exactly what's happening!

### Re-export Model (If Needed)

If the coordinate format still seems wrong, re-export your model:
```python
from ultralytics import YOLO

# Load your trained model
model = YOLO('path/to/best.pt')

# Export with correct settings for Android
model.export(
    format='onnx',
    opset=21,  # Compatible with ONNX Runtime 1.19
    simplify=True,  # Simplify the graph
    dynamic=False,  # Fixed input size
    imgsz=640  # Input size
)
```

Then replace `my_model.onnx` in `app/src/main/assets/`
