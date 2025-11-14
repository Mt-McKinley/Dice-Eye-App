package com.example.dice_eye_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.example.dice_eye_app.navigation.AppNavigation
import com.example.dice_eye_app.ui.theme.DiceEyeAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before calling super
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiceEyeAppTheme {
                DiceEyeApp()
            }
        }
    }
}

@Composable
fun DiceEyeApp() {
    val navController = rememberNavController()

    AppNavigation(
        navController = navController,
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    DiceEyeAppTheme {
        DiceEyeApp()
    }
}