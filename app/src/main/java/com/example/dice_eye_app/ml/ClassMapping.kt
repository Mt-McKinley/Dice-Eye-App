package com.example.dice_eye_app.ml

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Central source of truth for mapping the classifier's raw output indices (0..5)
 * to application face indices (0..5 for faces 1..6).
 *
 * The mapping is derived from a labels file shipped with the app assets:
 *   assets/labels_classifier.txt
 * whose lines reflect the exact class order used during training/export, e.g.:
 *   five
 *   four
 *   one
 *   six
 *   three
 *   two
 *
 * Each label is mapped to a face index via name: one->0, two->1, three->2, four->3, five->4, six->5
 * If the labels file is missing or invalid, a safe hardcoded fallback is used (same order as above).
 */
object ClassMapping {
    private const val TAG = "ClassMapping"
    private const val LABELS_ASSET = "labels_classifier.txt"

    // Map folder/label names to face indices (0-based: one->0, ..., six->5)
    private val faceIndexByName = mapOf(
        "one" to 0,
        "two" to 1,
        "three" to 2,
        "four" to 3,
        "five" to 4,
        "six" to 5
    )

    // Backstop order matching the training folders if labels file isn't found
    private val fallbackOrder = arrayOf("five", "four", "one", "six", "three", "two")

    // Loaded labels from assets; if unavailable, fallbackOrder is used
    @Volatile private var labels: List<String> = fallbackOrder.toList()

    // Final mapping: model class index -> app face index (0..5). Initialized lazily.
    @Volatile var mapping: IntArray = computeMapping(labels)
        private set

    fun initialize(context: Context, expectedClasses: Int = 6) {
        try {
            val assetMgr = context.assets
            assetMgr.open(LABELS_ASSET).use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                val loaded = reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                if (loaded.size == expectedClasses) {
                    labels = loaded
                    mapping = computeMapping(labels)
                    Log.d(TAG, "Loaded labels from assets: ${labels.joinToString()}")
                } else {
                    Log.w(TAG, "Labels size ${loaded.size} != expected ${expectedClasses}. Using fallback order: ${fallbackOrder.joinToString()}")
                    labels = fallbackOrder.toList()
                    mapping = computeMapping(labels)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load labels from assets/$LABELS_ASSET. Using fallback order.", e)
            labels = fallbackOrder.toList()
            mapping = computeMapping(labels)
        }
        logMapping()
    }

    private fun computeMapping(order: List<String>): IntArray {
        val m = IntArray(order.size) { -1 }
        for (i in order.indices) {
            val label = order[i].lowercase()
            // Try direct name mapping; if not present, attempt to parse trailing digit
            val faceIdx = faceIndexByName[label] ?: run {
                val digit = label.takeLastWhile { it.isDigit() }
                if (digit.isNotEmpty()) (digit.toIntOrNull()?.minus(1)) else null
            }
            m[i] = faceIdx ?: -1
        }
        return m
    }

    fun map(rawClassId: Int): Int = if (rawClassId in mapping.indices) mapping[rawClassId] else -1

    fun isValid(): Boolean {
        val seen = BooleanArray(mapping.size)
        for (v in mapping) {
            if (v !in 0 until mapping.size) return false
            if (seen[v]) return false
            seen[v] = true
        }
        return true
    }

    fun logMapping() {
        Log.d(TAG, "Classifier labels order: ${labels.joinToString()}")
        Log.d(TAG, "Final class->face mapping: ${mapping.joinToString(prefix = "[", postfix = "]")}")
    }
}
