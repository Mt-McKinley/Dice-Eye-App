# YOLO11s Testing Guide

## 🎲 Quick Test Procedure

### 1. Build & Install
```bash
cd C:\Users\disne\AndroidStudioProjects\DiceEyeApp
.\gradlew.bat assembleDebug -x lint -x test --no-daemon
```

### 2. Install on Device
- Build → Generate Signed Bundle / APK → APK
- Or use "Run" in Android Studio

### 3. Test with Known Roll
**Setup:** Roll dice showing 1, 2, 3, 4, 5, 6

**Expected Results:**
- ✅ Detects 6 dice (up from 3-4 with old model)
- ✅ 75-85% classification accuracy (up from 50%)
- ✅ Confidence scores >0.15 for all detections

---

## 📊 Comparing Results

### Old Model Performance
```
Roll: 1, 2, 3, 4, 5, 6
Detected: 1, 1, 5, 5, 5, 6 (3/6 correct = 50%)
Missing: 2, 3, 4 (not detected or misclassified)
```

### Expected New Model Performance
```
Roll: 1, 2, 3, 4, 5, 6
Detected: 1, 2, 3, 4, 5, 6 (5-6/6 correct = 83-100%)
Improvements:
- Better detection coverage (finds all 6 dice)
- Improved classification (trained on larger dataset)
- Higher confidence scores
```

---

## 🔍 Debug Image Locations

### On Device
```
/storage/emulated/0/Pictures/DiceEyeDebug/
```

### Windows Explorer
```
This PC\SAMSUNG-SM-G935A\Phone\Pictures\DiceEyeDebug
```

### Key Images to Check
1. `*_original_rotated.jpg` - Input image
2. `*_detector_input.jpg` - Letterboxed input to YOLO
3. `*_detector_final_detections.jpg` - Detected dice with bounding boxes
4. `*_crop_N.jpg` - Individual die crops for classification
5. `*_final_overlay.jpg` - Final result with classifications

---

## 📱 Logcat Commands

### Full Detection Pipeline
```bash
adb logcat -c && adb logcat | grep -E "DiceDetector|TwoStepDiceDetector|DiceClassifier|GameScreen"
```

### Detection Only
```bash
adb logcat | grep DiceDetector
```

### Classification Only
```bash
adb logcat | grep "CLASSIFICATION DEBUG"
```

### Model Inspection
```bash
adb logcat | grep "Model Inspection"
```

---

## ✅ Success Criteria

### Detection Stage
- [ ] Finds all 6 dice in frame
- [ ] Bounding boxes are tight and accurate
- [ ] Confidence scores >0.15 for each detection
- [ ] No false positives (detecting non-dice objects)

### Classification Stage
- [ ] 75-85% accuracy (5-6 out of 6 correct)
- [ ] Confidence scores >0.2 for accepted classifications
- [ ] Top-1 margin indicates clear winner
- [ ] Class mapping correct (model class → die face)

### Overall Performance
- [ ] Total processing time <10 seconds
- [ ] Debug images saved correctly
- [ ] No crashes or errors
- [ ] Consistent results across multiple captures

---

## 🐛 Troubleshooting

### Problem: "No detections found"
**Check:**
1. Logcat for "Model Inspection" - verify input/output shapes
2. Debug image `*_detector_input.jpg` - ensure dice are visible
3. Lighting conditions - YOLO needs good contrast

**Solution:**
- Lower `CONFIDENCE_THRESHOLD` in `DiceDetector.kt` (currently 0.15)
- Ensure dice are on contrasting background
- Check model file is `die_detection.tflite` in assets

### Problem: "Wrong classifications"
**Check:**
1. Logcat for "CLASSIFICATION DEBUG" - see raw probabilities
2. Debug images `*_crop_N.jpg` - verify crops are clear
3. Class mapping table - ensure correct model→face mapping

**Solution:**
- Verify `die_classifier.tflite` is the new trained model
- Check `ClassMapping.kt` for correct mapping
- Increase crop padding if dice are cut off

### Problem: "Model crashes on inference"
**Check:**
1. Logcat for "IllegalArgumentException" or "ArrayIndexOutOfBoundsException"
2. Model input/output tensor shapes match expectations
3. YOLO11 format detection working correctly

**Solution:**
- Verify model was exported correctly (YOLO11s → TFLite)
- Check `postprocessYOLO11Format()` is being called
- Inspect output tensor shape: should be `[1, 8400, 84]`

---

## 📈 Performance Metrics

### Detection Model (YOLO11s)
- **Precision:** 0.827 (82.7% of detections are correct)
- **Recall:** 0.828 (82.8% of actual dice are found)
- **mAP50:** 0.889 (88.9% accuracy at IoU threshold 0.5)

### Per-Class Performance
| Face | Precision | Recall | mAP50 |
|------|-----------|--------|-------|
| 1 | 0.755 | 0.831 | 0.916 |
| 2 | 0.823 | 0.769 | 0.839 |
| 3 | 0.804 | 0.816 | 0.884 |
| 4 | 0.958 | 0.962 | 0.987 |
| 5 | 0.745 | 0.715 | 0.796 |
| 6 | 0.875 | 0.872 | 0.911 |

**Weakest:** Face 5 (0.745 precision)  
**Strongest:** Face 4 (0.958 precision)

---

## 🔄 Iteration Workflow

1. **Test Current Model**
   - Capture test roll (1,2,3,4,5,6)
   - Note accuracy percentage
   - Check debug images

2. **Analyze Results**
   - Which faces are confused?
   - Are all dice detected?
   - What's the confidence distribution?

3. **Adjust Parameters** (if needed)
   - Detection: `CONFIDENCE_THRESHOLD`, `IOU_THRESHOLD`
   - Classification: `minClassificationConfidence`, `minTop1Margin`
   - Preprocessing: crop padding, resize quality

4. **Retrain** (if accuracy <75%)
   - Collect more training data
   - Balance dataset (Face 5 needs more samples)
   - Re-export model

---

## 🎯 Next Steps After Testing

### If Accuracy ≥75%
✅ **Success!** Model is ready for production
- Document final accuracy
- Update QUICK_REFERENCE.md
- Create release build

### If Accuracy 60-75%
⚠️ **Acceptable** but can be improved
- Adjust confidence thresholds
- Increase crop padding
- Consider ensemble approach (multiple rotations)

### If Accuracy <60%
❌ **Needs Work**
- Retrain classifier with more data
- Check for model quantization errors
- Verify preprocessing matches training

---

## 📝 Test Results Template

```markdown
## Test Results - [Date]

### Detection Stage
- Dice in frame: 6
- Dice detected: ___ / 6
- False positives: ___
- Avg confidence: ___

### Classification Stage
- Correct: ___ / 6 (___%）
- Confused pairs: (e.g., 3↔5)
- Avg confidence: ___
- Avg top-1 margin: ___

### Overall
- Total time: ___ seconds
- Success rate: ___%
- Issues: ___
```

---

## ✅ Ready to Test!

The YOLO11s model integration is complete. Follow the test procedure above and document results.

**Good luck! 🎲**

