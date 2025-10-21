# How to Debug Why Only Classes 5 and 6 Are Detected

## What I Changed

I've added detailed logging to the DiceDetector that will show you **exactly** what class scores your model is outputting for each detection.

## Steps to Debug

### 1. Run Your App
Launch the app and point the camera at a dice.

### 2. Open Logcat
In Android Studio, go to: **Logcat** (bottom panel)
Filter by: `DiceDetector`

### 3. Look for These Log Messages

When a detection occurs, you'll see logs like this:

```
[DEBUG] Detection: conf=0.XXXX, winner=class4
        All scores: class0=0.0012, class1=0.0015, class2=0.0008, class3=0.0010, class4=0.7823, class5=0.0045
        bbox=[320.5, 240.3, 80.2, 85.1]
```

## Interpreting the Results

### Scenario A: Scores Show Clear Bias
```
All scores: class0=0.0001, class1=0.0001, class2=0.0001, class3=0.0001, class4=0.8500, class5=0.7200
```
**Problem**: Training data had too many examples of dice showing 5 and 6 dots
**Solution**: Retrain with balanced dataset

### Scenario B: All Scores Are Similar
```
All scores: class0=0.1667, class1=0.1666, class2=0.1667, class3=0.1666, class4=0.1667, class5=0.1667
```
**Problem**: Model isn't learning anything (essentially random guessing)
**Solutions**:
- Model needs more training
- Check if training loss decreased
- Verify training data quality
- Check learning rate

### Scenario C: Pattern Shows Classes Are Reversed
Test with different dice and record results:

| Real Dice | Detected Class | Expected Class |
|-----------|----------------|----------------|
| 1 dot     | class5         | class0         |
| 2 dots    | class4         | class1         |
| 3 dots    | class3         | class2         |
| 4 dots    | class2         | class3         |
| 5 dots    | class1         | class4         |
| 6 dots    | class0         | class5         |

**Problem**: Classes are in reverse order
**Quick Fix**: In DiceDetector.kt, line 25, change:
```kotlin
private val reverseClassIndices = false
```
to:
```kotlin
private val reverseClassIndices = true
```

Then add this code in `processDetectionNormalized` after line 661 (after classId is determined):
```kotlin
// Apply reverse mapping if needed
if (reverseClassIndices) {
    classId = (numClasses - 1) - classId
    Log.d(TAG, "        Reversed class ID to: $classId")
}
```

### Scenario D: Classes Have an Offset
| Real Dice | Detected Class | Offset |
|-----------|----------------|--------|
| 1 dot     | class1         | +1     |
| 2 dots    | class2         | +1     |
| 3 dots    | class3         | +1     |
| 4 dots    | class4         | +1     |
| 5 dots    | class5         | +1     |
| 6 dots    | class0         | +1 (wraps) |

**Problem**: Class indices are shifted
**Fix**: Adjust the mapping in processDetectionNormalized

## What to Report Back

Please run the app and share:

1. **The actual log output** showing the class scores
2. **Test results** from showing different dice (1-6 dots)
3. **Pattern you observe** (which classes are detected for which dice)

Example output to share:
```
Tested dice with 1 dot → Detected class5 (Dice 6)
Tested dice with 2 dots → Detected class5 (Dice 6) 
Tested dice with 3 dots → Detected class4 (Dice 5)
Tested dice with 4 dots → Detected class4 (Dice 5)
Tested dice with 5 dots → Detected class5 (Dice 6)
Tested dice with 6 dots → Detected class5 (Dice 6)

Sample log:
[DEBUG] Detection: conf=0.0234, winner=class5
        All scores: class0=0.0012, class1=0.0015, class2=0.0008, class3=0.0010, class4=0.0089, class5=0.0234
```

## Common Issues and Fixes

### If Model Always Detects Same Class
- Model is severely biased
- Need to retrain with better data

### If Scores Are All Very Low (< 0.1)
- Model is not confident
- Training may not have converged
- Might need more training data/epochs

### If Pattern Is Consistent But Wrong
- Systematic mapping error
- Can be fixed with code adjustment (see scenarios above)

---

## Next Steps After Identifying the Issue

Once you know the pattern, I can help you:
1. Add a class remapping fix if needed
2. Adjust the model export if the format is wrong
3. Provide guidance on retraining if that's needed

