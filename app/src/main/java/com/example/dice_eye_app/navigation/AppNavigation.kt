package com.example.dice_eye_app.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.dice_eye_app.screens.HomeScreen
import com.example.dice_eye_app.screens.GameScreen
import com.example.dice_eye_app.screens.HistoryScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home

/**
 * Enum defining all possible navigation destinations
 */
enum class AppScreen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Default.Home),
    GAME("game", "Game", Icons.Default.CameraAlt),
    HISTORY("history", "History", Icons.Default.History)
}

/**
 * Main navigation component
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppScreen.HOME.route,
        modifier = modifier
    ) {
        composable(AppScreen.HOME.route) {
            HomeScreen(
                onStartGame = {
                    navController.navigate(AppScreen.GAME.route)
                }
            )
        }

        composable(AppScreen.GAME.route) {
            GameScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToHistory = {
                    navController.navigate(AppScreen.HISTORY.route)
                }
            )
        }
        
        composable(AppScreen.HISTORY.route) {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
