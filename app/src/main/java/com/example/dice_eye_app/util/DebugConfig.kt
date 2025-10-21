package com.example.dice_eye_app.util

/**
 * Debug configuration to enable diagnostic features
 */
object DebugConfig {
    // Master switch to enable/disable all debug features
    const val ENABLED = true

    // Save various intermediate processing images
    const val SAVE_ORIGINAL = true          // Save original captured image after rotation
    const val SAVE_LETTERBOXED = true       // Save letterboxed input to detector
    const val SAVE_DETECTIONS_OVERLAY = true // Save overlay showing all initial detections
    const val SAVE_CROPS = true             // Save cropped die images sent to classifier
    const val SAVE_ROTATED_VARIANTS = true  // Save rotated variants of crops
    const val SAVE_FINAL_OVERLAY = true     // Save final labeled result overlay

    // Debug logging
    const val LOG_CLASSIFIER_DETAILS = true // Log detailed classification probabilities
    const val LOG_ALL_DETECTIONS = true     // Log all detections before filtering

    // Path for saving debug images
    const val DEBUG_PATH = "DiceEyeApp/debug"  // Subdirectory under Pictures for debug images
}
