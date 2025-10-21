# Dice Detection Debug Guide

## Issue: Model mostly detects classes 5 and 6

### Possible Causes:

#### 1. **Training Data Imbalance**
- Your model may have been trained with many more examples of dice showing 5 and 6 dots
- Solution: Check your training dataset distribution

#### 2. **Class Index Mismatch**
The model might be outputting classes in a different order than expected:
- **Current mapping**: class0=Dice1, class1=Dice2, ..., class5=Dice6
- **Possible actual mapping**: Could be reversed or different order

#### 3. **Output Format Issue**
YOLOv11 models can have different output formats depending on export settings:
- **Without NMS**: `[batch, num_predictions, 4+num_classes]` - what we expect
- **With NMS**: Different structure with pre-filtered results

#### 4. **Model Export Problem**
If the model was exported incorrectly, the class scores might be in wrong positions

---

## Debug Steps:

### Step 1: Check the Logcat Output
Run your app and look for these log messages:
```
[DEBUG] Detection: conf=X.XXXX, winner=classX
        All scores: class0=X.XXXX, class1=X.XXXX, class2=X.XXXX, class3=X.XXXX, class4=X.XXXX, class5=X.XXXX
```

### What to look for:
- Are ALL class scores very low except classes 4 and 5 (which map to Dice 5 and Dice 6)?
- Are the scores in a pattern like: `[0.0001, 0.0001, 0.0001, 0.0001, 0.8, 0.7]`?
- Or are all scores similar: `[0.16, 0.17, 0.16, 0.17, 0.18, 0.16]`?

---

## Solutions Based on Findings:

### If scores show clear bias toward classes 4-5:
**Problem**: Training data imbalance or model convergence issue
**Fix**: 
1. Retrain model with balanced dataset
2. Use data augmentation
3. Apply class weights during training

### If all scores are similar (e.g., all ~0.16):
**Problem**: Model is not confident, essentially guessing
**Fix**: 
1. Model needs more training epochs
2. Check if training converged properly
3. Verify input preprocessing matches training preprocessing

### If the pattern suggests reversed order:
**Problem**: Class index mapping is backwards
**Fix**: Try this temporary fix in code to test:

```kotlin
// In processDetectionNormalized, after getting classId:
classId = (numClasses - 1) - classId  // Reverse the class ID
```

### If certain attributes are in wrong positions:
**Problem**: Model output format doesn't match expectations
**Fix**: Check your YOLO export parameters:
- Ensure model was exported WITHOUT NMS (post-processing should be done in app)
- Verify export used: `model.export(format='onnx', simplify=True, opset=21)`

---

## Testing Different Dice Values:

Try these dice images to verify pattern:
1. Show dice with 1 dot → Check what class is detected
2. Show dice with 2 dots → Check what class is detected
3. Show dice with 3 dots → Check what class is detected
4. Show dice with 4 dots → Check what class is detected
5. Show dice with 5 dots → Check what class is detected
6. Show dice with 6 dots → Check what class is detected

Create a table:
| Actual Dice | Detected Class | Detected Label | Pattern |
|-------------|----------------|----------------|---------|
| 1 dot       | class?         | Dice ?         |         |
| 2 dots      | class?         | Dice ?         |         |
| 3 dots      | class?         | Dice ?         |         |
| 4 dots      | class?         | Dice ?         |         |
| 5 dots      | class?         | Dice ?         |         |
| 6 dots      | class?         | Dice ?         |         |

This will reveal if there's a systematic offset or reversal.

---

## Quick Fix to Test Class Reversal:

If you suspect classes are reversed, add this to DiceDetector.kt temporarily:

