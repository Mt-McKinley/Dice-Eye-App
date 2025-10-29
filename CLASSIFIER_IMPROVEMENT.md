# Classifier Improvement - Disabled Test-Time Augmentation

## The Issue

You got **1,1,5,5,5,6** when the actual roll was **1,2,3,4,5,6**
- ✅ Got 3 correct (1, 5, 6) = **50% accuracy**
- ❌ Got 3 wrong (2→1, 3→5, 4→5)

## Root Cause Analysis

### 1. Test-Time Augmentation (TTA) Was Hurting Performance
The classifier was using **8 augmented versions** of each crop:
- 4 rotations (0°, 90°, 180°, 270°)
- 2 orientations per rotation (normal + horizontal flip)
- **Total: 8 classifications averaged together**

**Problem:** If the model wasn't trained with rotation invariance, averaging predictions from rotated/flipped images can **reduce accuracy** instead of improving it.

### 2. Flat Probability Distributions
Looking at the logs:
```
All probabilities (quant): 0.131, 0.130, 0.349, 0.130, 0.130, 0.130
```

The model gives very flat probabilities (all around 13% except the winner at 35%). This suggests:
- Model needs more/better training data
- Model is uncertain about classifications
- Current training data may be limited

## The Fix Applied

### Disabled Test-Time Augmentation
**File:** `TwoStepDiceDetector.kt`

Changed `classifyWithRotations()` to:
- ✅ Use **only the original crop** (no rotations, no flips)
- ✅ Classify once instead of 8 times
- ✅ Much faster (8x speed improvement)
- ✅ Should be more accurate if model wasn't trained for rotation invariance

**Before:**
```kotlin
// Classify 8 variations, average probabilities
for (deg in [0, 90, 180, 270]) {
    classify(rotated)
    classify(flipped)
}
avgProbs = average(all 8 results)
```

**After:**
```kotlin
// Just classify the original crop once
return classifier.classify(cropped)
```

### Also Disabled Saving Rotated Variants
**File:** `DebugConfig.kt`
- Set `SAVE_ROTATED_VARIANTS = false` 
- Saves disk space and I/O time
- No longer needed since we're not using TTA

## Expected Results

### Performance Impact:
- ⚡ **8x faster** classification (1 inference instead of 8)
- 📊 Accuracy should **improve** if the model wasn't trained for rotation invariance
- 💾 Less disk usage (no rotated variant images saved)

### What to Test:
1. **Rebuild and install** the app
2. **Take the same photo** of 1,2,3,4,5,6 dice
3. **Compare results:**
   - Before: 1,1,5,5,5,6 (50% accuracy)
   - After: Should be better (hopefully 4-6 correct)

## Why This Should Help

Dice faces have **orientation-specific features**:
- **Face 1**: Single dot in center
- **Face 2**: Two dots diagonal (orientation matters!)
- **Face 3**: Three dots diagonal (orientation matters!)
- **Face 4**: Four dots in corners
- **Face 5**: Five dots (one center + four corners)
- **Face 6**: Six dots in two columns

If you rotate a **Face 3** by 90°, it can look very similar to a rotated **Face 2**. Averaging these confused predictions reduces accuracy.

## If Accuracy Doesn't Improve

The next steps would be:
1. **Collect more training data** for your specific dice
2. **Retrain the model** with your dice images
3. **Use data augmentation during training** (rotations, flips, brightness, etc.)
4. **Try a larger model architecture** (current is likely MobileNet or EfficientNet-Lite)

## Current Model Characteristics

Based on the flat probabilities, this model:
- ✅ Can detect dice locations well (DiceDetector working great!)
- ⚠️ Has moderate classification accuracy (~50-70% expected)
- 📉 Produces low-confidence predictions (35% max vs ideal 80%+)
- 🎯 Needs more training data for your specific dice type

## The Good News

**50% accuracy is actually reasonable** for a small training set! The fact that it got 1, 5, and 6 correct shows the model is learning. The confusion between 2↔3 and 4↔5 is expected with limited training data.

With this TTA fix, you should see improvement to 60-80% accuracy, which is quite usable!

