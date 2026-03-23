package com.jasper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jasper.app.ui.screens.AnalyticsDashboardScreen
import com.jasper.app.ui.screens.AttendanceScreen
import com.jasper.app.ui.screens.HistoryScreen
import com.jasper.app.ui.screens.HomeScreen
import com.jasper.app.ui.screens.KioskScreen
import com.jasper.app.ui.screens.RecognitionScreen
import com.jasper.app.ui.screens.RegistrationScreen
import com.jasper.app.ui.screens.SettingsScreen
import com.jasper.app.ui.theme.JasperTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JasperTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                onNavigateToRegister = { navController.navigate("register") },
                                onNavigateToRegisterAddCaptures = { userId ->
                                    navController.navigate("register_add/$userId")
                                },
                                onNavigateToRecognize = { navController.navigate("recognize") },
                                onNavigateToHistory = { navController.navigate("history") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToKiosk = { navController.navigate("kiosk") },
                                onNavigateToAttendance = { navController.navigate("attendance") },
                                onNavigateToAnalytics = { navController.navigate("analytics") }
                            )
                        }
                        composable("register") {
                            RegistrationScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "register_add/{userId}",
                            arguments = listOf(navArgument("userId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            RegistrationScreen(
                                onNavigateBack = { navController.popBackStack() },
                                existingUserId = backStackEntry.arguments?.getInt("userId")
                            )
                        }
                        composable("recognize") {
                            RecognitionScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToKiosk = { navController.navigate("kiosk") }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("kiosk") {
                            KioskScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("attendance") {
                            AttendanceScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("analytics") {
                            AnalyticsDashboardScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
