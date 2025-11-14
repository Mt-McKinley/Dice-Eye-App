package com.example.dice_eye_app.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dice_eye_app.R
import com.example.dice_eye_app.ui.theme.DiceEyeCyan
import com.example.dice_eye_app.ui.theme.DiceEyeCyanLight
import com.example.dice_eye_app.ui.theme.DiceEyeDarkBlue

/**
 * Home screen - the main landing screen with Dice Eye branding
 */
@Composable
fun HomeScreen(
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for the eye
    val infiniteTransition = rememberInfiniteTransition(label = "eye_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DiceEyeDarkBlue,
                        Color(0xFF0F1F2B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Eye Logo
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(scale)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.dice_eye),
                    contentDescription = "Dice Eye Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App Title
            Text(
                text = "DICE EYE",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    letterSpacing = 4.sp
                ),
                color = DiceEyeCyan
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tagline
            Text(
                text = "Keep Your Eye on the Die",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                ),
                color = DiceEyeCyanLight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Start Button with glow effect
            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DiceEyeCyan,
                    contentColor = DiceEyeDarkBlue
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                )
            ) {
                Text(
                    text = "START GAME",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subtle hint text
            Text(
                text = "Roll the dice. We'll keep watch.",
                style = MaterialTheme.typography.bodySmall,
                color = DiceEyeCyanLight.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
