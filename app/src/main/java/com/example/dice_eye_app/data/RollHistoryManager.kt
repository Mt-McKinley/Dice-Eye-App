package com.example.dice_eye_app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing a single dice roll in history
 */
data class DiceRoll(
    val id: String = UUID.randomUUID().toString(),
    val values: List<Int>,
    val timestamp: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val editTimestamp: Long? = null
) {
    fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getFormattedEditTimestamp(): String? {
        return editTimestamp?.let {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            sdf.format(Date(it))
        }
    }
    
    fun getTotal(): Int = values.sum()
}

/**
 * Singleton manager for roll history
 */
object RollHistoryManager {
    private val _rolls = MutableStateFlow<List<DiceRoll>>(emptyList())
    val rolls: StateFlow<List<DiceRoll>> = _rolls.asStateFlow()
    
    /**
     * Add a new roll to history
     */
    fun addRoll(values: List<Int>) {
        if (values.isEmpty()) return
        
        val newRoll = DiceRoll(values = values)
        _rolls.value = _rolls.value + newRoll
        android.util.Log.d("RollHistoryManager", "Added roll: $values, total rolls: ${_rolls.value.size}")
    }
    
    /**
     * Edit an existing roll
     */
    fun editRoll(rollId: String, newValues: List<Int>) {
        if (newValues.isEmpty()) return
        
        _rolls.value = _rolls.value.map { roll ->
            if (roll.id == rollId) {
                roll.copy(
                    values = newValues,
                    isEdited = true,
                    editTimestamp = System.currentTimeMillis()
                )
            } else {
                roll
            }
        }
        android.util.Log.d("RollHistoryManager", "Edited roll $rollId to: $newValues")
    }
    
    /**
     * Delete a roll from history
     */
    fun deleteRoll(rollId: String) {
        _rolls.value = _rolls.value.filter { it.id != rollId }
        android.util.Log.d("RollHistoryManager", "Deleted roll $rollId, remaining: ${_rolls.value.size}")
    }
    
    /**
     * Clear all history
     */
    fun clearAll() {
        _rolls.value = emptyList()
        android.util.Log.d("RollHistoryManager", "Cleared all roll history")
    }
    
    /**
     * Get total number of rolls
     */
    fun getRollCount(): Int = _rolls.value.size
    
    /**
     * Get sum of all roll totals
     */
    fun getGrandTotal(): Int = _rolls.value.sumOf { it.getTotal() }
}
