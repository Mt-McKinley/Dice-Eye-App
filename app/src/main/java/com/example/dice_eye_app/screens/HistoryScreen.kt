package com.example.dice_eye_app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dice_eye_app.data.DiceRoll
import com.example.dice_eye_app.data.RollHistoryManager
import com.example.dice_eye_app.ui.theme.DiceEyeCyan
import com.example.dice_eye_app.ui.theme.DiceEyeCyanLight
import com.example.dice_eye_app.ui.theme.DiceEyeDarkBlue
import com.example.dice_eye_app.ui.theme.DiceEyeNavy

/**
 * History screen - View and edit roll history
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rolls by RollHistoryManager.rolls.collectAsState()
    var rollToEdit by remember { mutableStateOf<DiceRoll?>(null) }
    var rollToDelete by remember { mutableStateOf<DiceRoll?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "ROLL HISTORY",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        )
                        Text(
                            "View and Edit Your Rolls",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp
                            ),
                            color = DiceEyeCyanLight
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DiceEyeDarkBlue,
                    titleContentColor = DiceEyeCyan,
                    navigationIconContentColor = DiceEyeCyan
                )
            )
        },
        containerColor = Color(0xFF0F1F2B)
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Header with stats
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DiceEyeNavy
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${rolls.size}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = DiceEyeCyan
                            )
                            Text(
                                text = "Total Rolls",
                                style = MaterialTheme.typography.bodySmall,
                                color = DiceEyeCyanLight
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${rolls.sumOf { it.values.size }}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = DiceEyeCyan
                            )
                            Text(
                                text = "Total Dice",
                                style = MaterialTheme.typography.bodySmall,
                                color = DiceEyeCyanLight
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${RollHistoryManager.getGrandTotal()}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = DiceEyeCyan
                            )
                            Text(
                                text = "Grand Total",
                                style = MaterialTheme.typography.bodySmall,
                                color = DiceEyeCyanLight
                            )
                        }
                    }
                
                if (rolls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { rollToDelete = DiceRoll(id = "clear_all", values = emptyList()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DiceEyeCyan.copy(alpha = 0.3f),
                            contentColor = DiceEyeCyanLight
                        ),
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Text("Clear All History")
                    }
                }
            }
        }
        
        // Roll list
        if (rolls.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📊",
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No rolls recorded yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = DiceEyeCyanLight.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start a game to record your dice rolls!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DiceEyeCyanLight.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rolls.reversed(), key = { it.id }) { roll ->
                    RollHistoryItem(
                        roll = roll,
                        rollNumber = rolls.size - rolls.indexOf(roll),
                        onEdit = { rollToEdit = it },
                        onDelete = { rollToDelete = it }
                    )
                }
            }
        }
        }
        
        // Edit Dialog
        rollToEdit?.let { roll ->
            EditRollDialog(
                roll = roll,
                onDismiss = { rollToEdit = null },
                onSave = { newValues ->
                    RollHistoryManager.editRoll(roll.id, newValues)
                    rollToEdit = null
                }
            )
        }
        
        // Delete Confirmation Dialog
        rollToDelete?.let { roll ->
        AlertDialog(
            onDismissRequest = { rollToDelete = null },
            title = {
                Text(
                    text = if (roll.id == "clear_all") "Clear All History?" else "Delete Roll?",
                    color = DiceEyeCyan
                )
            },
            text = {
                Text(
                    text = if (roll.id == "clear_all") 
                        "This will permanently delete all ${rolls.size} rolls from history."
                    else 
                        "This will permanently delete this roll from history.",
                    color = DiceEyeCyanLight
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (roll.id == "clear_all") {
                            RollHistoryManager.clearAll()
                        } else {
                            RollHistoryManager.deleteRoll(roll.id)
                        }
                        rollToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiceEyeCyan,
                        contentColor = DiceEyeDarkBlue
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { rollToDelete = null }) {
                    Text("Cancel", color = DiceEyeCyanLight)
                }
            },
            containerColor = DiceEyeNavy
        )
        }
    }
}

@Composable
fun RollHistoryItem(
    roll: DiceRoll,
    rollNumber: Int,
    onEdit: (DiceRoll) -> Unit,
    onDelete: (DiceRoll) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DiceEyeNavy
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Roll #$rollNumber",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DiceEyeCyanLight
                    )
                    if (roll.isEdited) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = DiceEyeCyan.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = "EDITED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = DiceEyeCyan,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = roll.values.joinToString(", "),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DiceEyeCyan
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Dice: ${roll.values.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DiceEyeCyanLight
                    )
                    Text(
                        text = "Total: ${roll.getTotal()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DiceEyeCyanLight
                    )
                }
                
                Text(
                    text = roll.getFormattedTimestamp(),
                    style = MaterialTheme.typography.bodySmall,
                    color = DiceEyeCyanLight.copy(alpha = 0.6f)
                )
                
                if (roll.isEdited && roll.editTimestamp != null) {
                    Text(
                        text = "Edited: ${roll.getFormattedEditTimestamp()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DiceEyeCyan.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onEdit(roll) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit roll",
                        tint = DiceEyeCyan
                    )
                }
                
                IconButton(
                    onClick = { onDelete(roll) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete roll",
                        tint = DiceEyeCyan.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun EditRollDialog(
    roll: DiceRoll,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit
) {
    var diceValues by remember { mutableStateOf(roll.values.joinToString(", ")) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DiceEyeNavy
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Edit Roll",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DiceEyeCyan
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Enter dice values (comma-separated):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DiceEyeCyanLight
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = diceValues,
                    onValueChange = {
                        diceValues = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., 1, 2, 3, 4, 5, 6") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DiceEyeCyan,
                        unfocusedBorderColor = DiceEyeCyanLight,
                        focusedTextColor = DiceEyeCyan,
                        unfocusedTextColor = DiceEyeCyanLight,
                        cursorColor = DiceEyeCyan
                    ),
                    isError = errorMessage != null
                )
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = DiceEyeCyanLight)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            try {
                                val values = diceValues
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .map { it.toInt() }
                                
                                if (values.isEmpty()) {
                                    errorMessage = "Please enter at least one value"
                                } else if (values.any { it < 1 || it > 6 }) {
                                    errorMessage = "Dice values must be between 1 and 6"
                                } else {
                                    onSave(values)
                                }
                            } catch (e: NumberFormatException) {
                                errorMessage = "Please enter valid numbers"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DiceEyeCyan,
                            contentColor = DiceEyeDarkBlue
                        )
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
