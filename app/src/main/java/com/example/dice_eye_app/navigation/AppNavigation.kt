package com.example.dice_eye_app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.dice_eye_app.screens.HomeScreen
import com.example.dice_eye_app.screens.GameScreen

/**
 * Enum defining all possible navigation destinations
 */
enum class AppScreen(val route: String) {
    HOME("home"),
    GAME("game")
}

/**
 * Main navigation component that sets up the navigation graph
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
                }
            )
        }
    }
}
