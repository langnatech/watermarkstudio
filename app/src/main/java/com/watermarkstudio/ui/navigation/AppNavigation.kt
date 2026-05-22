package com.watermarkstudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.ui.screens.EditorScreen
import com.watermarkstudio.ui.screens.HomeScreen
import com.watermarkstudio.ui.screens.SubscriptionScreen
import com.watermarkstudio.viewmodel.WatermarkViewModel

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val viewModel: WatermarkViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToAddWatermark = { navController.navigate("add_watermark") },
                onNavigateToRemoveWatermark = { 
                    // Removal mode is a premium-only feature!
                    if (viewModel.uiState.value.isPremium) {
                        navController.navigate("remove_watermark")
                    } else {
                        navController.navigate("subscription")
                    }
                },
                onNavigateToHistory = { },
                onNavigateToSubscription = { navController.navigate("subscription") },
                viewModel = viewModel
            )
        }
        composable("add_watermark") {
            EditorScreen(
                viewModel = viewModel,
                mode = WatermarkType.TEXT, // Default to text, user can add image too
                onBack = { navController.popBackStack() },
                onNavigateToSubscription = { navController.navigate("subscription") }
            )
        }
        composable("remove_watermark") {
            EditorScreen(
                viewModel = viewModel,
                mode = WatermarkType.REMOVE,
                onBack = { navController.popBackStack() },
                onNavigateToSubscription = { navController.navigate("subscription") }
            )
        }
        composable("subscription") {
            SubscriptionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

