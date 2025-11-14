package com.example.dice_eye_app.util

/**
 * Debug configuration to enable diagnostic features
 */
object DebugConfig {
    // Master switch to enable/disable all debug features
    const val ENABLED = false // Disabled for production speed

    // Save various intermediate processing images
    const val SAVE_ORIGINAL = false          // Save original captured image after rotation
    const val SAVE_LETTERBOXED = false       // Save letterboxed input to detector
    const val SAVE_DETECTIONS_OVERLAY = false // Save overlay showing all initial detections
    const val SAVE_CROPS = false             // Save cropped die images sent to classifier
    const val SAVE_ROTATED_VARIANTS = false // Disabled - we're not using TTA anymore
    const val SAVE_FINAL_OVERLAY = false     // Save final labeled result overlay

    // Debug logging
    const val LOG_CLASSIFIER_DETAILS = false // Log detailed classification probabilities
    const val LOG_ALL_DETECTIONS = false     // Log all detections before filtering

    // Mapping diagnostics
    // If true, bypasses ClassMapping so the app uses raw class ids (0..5) directly.
    // This helps isolate whether wrong outputs are due to mapping or preprocessing.
    const val BYPASS_CLASS_MAPPING = false

    // Bitmapping diagnostics
    // If true, swap red and blue channels during classifier preprocessing to test
    // BGR vs RGB mismatches.
    // NOTE: Most TFLite models expect RGB order (standard Android bitmap format)
    const val SWAP_RB_CHANNELS = false  // Set to false for standard RGB TFLite models

    // Force FLOAT32 preprocessing even if model says UINT8
    // Use this to test if the model was trained with normalized 0.0-1.0 inputs
    // but the TFLite metadata incorrectly says UINT8
    const val FORCE_FLOAT32_INPUT = false  // Model actually expects UINT8, not FLOAT32

    // Path for saving debug images
    const val DEBUG_PATH = "DiceEyeApp/debug"  // Subdirectory under Pictures for debug images
}
