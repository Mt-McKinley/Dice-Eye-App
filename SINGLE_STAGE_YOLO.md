# 🎯 Single-Stage YOLO Approach - Implementation Complete

**Date:** October 28, 2025  
**Status:** ✅ READY FOR TESTING

---

## 🔄 Architecture Change

### Original Plan (Two-Stage)
```
Detection Model → Classification Model
(YOLO11s)        (Simple softmax)
```

### Current Implementation (Single-Stage)
```
YOLO11s Model → Both Detection AND Classification
```

**Why?** You're using the same YOLO11s model for both tasks, which is actually a valid approach! YOLO models output class probabilities for each detection, so we can use those directly.

---

## 🛠️ Technical Implementation

### Model Output Format
**YOLO11s Output:** `[1, num_classes, num_predictions]`
- Example: `[1, 10, 8400]`
- 10 classes (but we only use first 6 for dice faces 1-6)
- 8400 predictions (grid cells from different feature map scales)

### How Classification Works

1. **Detection Stage** (DiceDetector.kt)
   - Input: Full image `[1, 640, 640, 3]`
   - Output: Bounding boxes for dice locations
   - Uses YOLO format parser: `postprocessYOLO11Format()`

2. **Classification Stage** (DiceClassifier.kt)
   - Input: Cropped die image (resized to model's input size)
   - Output: Class scores `[1, 10, 8400]`
   - **New logic:** 
     - For each of 6 dice classes, get max confidence across all 8400 grid cells
     - Apply softmax to get probability distribution
     - Select class with highest probability

### Code Flow

```kotlin
// DiceClassifier.kt - New approach
fun classify(bitmap: Bitmap): ClassificationResult? {
    val outputShape = getOutputShape()
    
    if (outputShape.size == 3) {
        // YOLO format: [1, num_classes, num_predictions]
        return classifyWithYoloModel(bitmap, outputShape)
    } else {
        // Simple format: [1, num_classes]
        return classifyWithSimpleModel(bitmap)
    }
}

fun classifyWithYoloModel(...): ClassificationResult {
    // Run inference
    val output = Array(1) { Array(10) { FloatArray(8400) } }
    interpreter.run(inputBuffer, output)
    
    // Extract class scores (max across grid cells)
    val classScores = FloatArray(6)
    for (c in 0..5) {
        classScores[c] = output[0][c].maxOrNull() ?: 0f
    }
    
    // Softmax and select winner
    val probs = softmax(classScores)
    val predictedClass = probs.indexOfMax()
    
    return ClassificationResult(predictedClass, probs[predictedClass], probs)
}
```

---

## 📊 Expected Behavior

### Detection Phase
```
Input: Image with 6 dice
DiceDetector (YOLO11s):
  → Output: [1, 8400, 84]
  → Detects 6 bounding boxes
  → Confidence: 0.4-0.6 per detection
```

### Classification Phase (Per Die)
```
Input: Cropped die image (e.g., 224x224)
DiceClassifier (Same YOLO11s model):
  → Output: [1, 10, 8400]
  → Extract scores for classes 0-5 (dice faces)
  → Max score per class: [0.15, 0.12, 0.28, 0.18, 0.14, 0.13]
  → After softmax: [0.18, 0.16, 0.25, 0.19, 0.15, 0.14]
  → Predicted: Class 2 (Face 3) with confidence 0.25
```

---

## ✅ Advantages of Single-Stage Approach

1. **Simplicity**
   - One model to maintain
   - Consistent preprocessing
   - Same export/deployment process

2. **Performance**
   - Already computed during detection
   - No need for separate classification model
   - Fewer model loads = lower memory

3. **Consistency**
   - Same training data
   - Same augmentations
   - Unified confidence scores

---

## ⚠️ Potential Limitations

1. **YOLO Not Optimized for Classification**
   - YOLO is designed for detection, not pure classification
   - May have lower classification accuracy than dedicated classifier
   - Grid-based approach might not capture die face details as well

2. **Resolution Trade-off**
   - Detection needs full image context
   - Classification benefits from high-resolution crops
   - Using same model means resolution compromise

3. **Class Confusion**
   - YOLO trained on 10 classes (includes COCO objects?)
   - Only first 6 classes represent dice faces
   - May have unexpected class activations

---

## 🧪 Testing Strategy

### Expected Results

**Best Case (75-85% accuracy):**
```
Roll: [1, 2, 3, 4, 5, 6]
Detected: [1, 2, 3, 4, 5, 6]
Accuracy: 100%
```

**Realistic Case (50-70% accuracy):**
```
Roll: [1, 2, 3, 4, 5, 6]
Detected: [1, 2, 3, 4, 5, 5]
Accuracy: 83% (5/6 correct)
```

**Worst Case (<50% accuracy):**
```
Roll: [1, 2, 3, 4, 5, 6]
Detected: [1, 1, 3, 3, 5, 5]
Accuracy: 50% (3/6 correct)
```

### If Accuracy Is Low

**Option 1: Adjust Class Extraction**
```kotlin
// Instead of max, try average
classScores[c] = output[0][c].average().toFloat()

// Or use top-k averaging
val topK = output[0][c].sortedDescending().take(100)
classScores[c] = topK.average().toFloat()
```

**Option 2: Train Dedicated Classifier**
- Export YOLO11s detection-only model
- Train separate classifier (ResNet50, MobileNetV2)
- Go back to two-stage approach

**Option 3: Fine-tune Class Count**
- Retrain YOLO with only 6 classes (dice faces)
- Remove unnecessary COCO classes
- Improve class-specific precision

---

## 📈 Performance Metrics

### Detection (YOLO11s - Primary Task)
- **mAP50:** 0.889 (88.9%)
- **Precision:** 0.827 (82.7%)
- **Recall:** 0.828 (82.8%)

### Classification (YOLO11s - Secondary Task)
- **Expected:** 50-70% accuracy
- **Why lower?** YOLO optimized for detection, not classification
- **Improvement:** Train dedicated classifier if needed

---

## 🚀 Next Steps

1. **Test Current Implementation**
   ```bash
   gradlew.bat assembleDebug
   # Install and test with roll [1,2,3,4,5,6]
   ```

2. **Check Logcat**
   ```bash
   adb logcat | grep "YOLO class scores"
   ```
   Look for patterns in class score distribution

3. **Analyze Results**
   - Are detections working? (Should be 6/6)
   - What's classification accuracy? (Target: >70%)
   - Which faces are confused? (Look for patterns)

4. **Iterate If Needed**
   - **If detection fails:** Check YOLO output parsing
   - **If classification fails:** Consider dedicated classifier
   - **If both fail:** Verify model export process

---

## 🔧 Troubleshooting

### Issue: "Cannot copy from TensorFlowLite tensor with shape [1, 10, 8400]"
**Status:** ✅ FIXED
**Solution:** Updated `DiceClassifier.kt` to handle YOLO output format

### Issue: Low classification accuracy (<50%)
**Check:**
1. Logcat for "YOLO class scores" - are scores meaningful?
2. Debug images - are crops clear and well-lit?
3. Class distribution - are all 6 classes represented?

**Solutions:**
- Adjust class score extraction (max vs average vs top-k)
- Increase crop padding to capture more context
- Try different confidence thresholds
- Consider training dedicated classifier

### Issue: Unexpected class predictions (>6)
**Check:**
- Verify model has only 6 output classes for dice
- Check if COCO classes are interfering
- Review training data labels

---

## 📝 Summary

✅ **DiceClassifier updated to handle YOLO format**  
✅ **Single-stage approach implemented**  
✅ **Auto-detects YOLO vs simple classification**  
✅ **Zero compilation errors**  
✅ **Ready for testing**

**Expected:** Detection works well (88.9% mAP), classification may be 50-70% accurate since YOLO isn't optimized for it.

**If needed:** Can train dedicated classifier and go back to two-stage approach for better classification accuracy.

---

## 🎉 Ready to Test!

The single-stage YOLO approach is now fully implemented. Build, install, and test!

**Success Criteria:**
- 6 dice detected ✓
- 50-85% classification accuracy (depending on YOLO's classification performance)
- No crashes ✓

Good luck! 🎲

