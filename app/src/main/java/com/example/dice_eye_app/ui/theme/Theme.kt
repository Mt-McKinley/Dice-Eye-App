package com.example.dice_eye_app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DiceEyeCyan,
    onPrimary = DiceEyeDarkBlue,
    primaryContainer = DiceEyeNavy,
    onPrimaryContainer = DiceEyeCyanLight,
    secondary = DiceEyeCyanLight,
    onSecondary = DiceEyeDarkBlue,
    tertiary = DiceEyeCyanLight,
    background = DiceEyeBackground,
    onBackground = Color.White,
    surface = DiceEyeNavy,
    onSurface = Color.White,
    surfaceVariant = DiceEyeDarkBlue,
    onSurfaceVariant = DiceEyeCyanLight
)

private val LightColorScheme = lightColorScheme(
    primary = DiceEyeCyanDark,
    onPrimary = Color.White,
    primaryContainer = DiceEyeCyanLight,
    onPrimaryContainer = DiceEyeDarkBlue,
    secondary = DiceEyeCyan,
    onSecondary = DiceEyeDarkBlue,
    tertiary = DiceEyeCyan,
    background = DiceEyeLightBg,
    onBackground = DiceEyeDarkBlue,
    surface = DiceEyeLightSurface,
    onSurface = DiceEyeDarkBlue,
    surfaceVariant = DiceEyeLightBg,
    onSurfaceVariant = DiceEyeDarkBlue
)

@Composable
fun DiceEyeAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color to maintain brand identity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}