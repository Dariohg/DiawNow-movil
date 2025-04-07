package com.example.diagnow.core.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.presentation.HomeScreen
import com.example.diagnow.home.presentation.PrescriptionDetailScreen
import com.example.diagnow.login.presentation.LoginScreen
import com.example.diagnow.register.presentation.RegisterScreen

@Composable
fun NavigationWrapper(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    var startDestination by remember { mutableStateOf<String?>(null) }
    var isLoadingDestination by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = Unit) {
        val isLoggedIn = sessionManager.isLoggedIn()
        val isExpired = sessionManager.isSessionExpired()

        Log.d("NavigationWrapper", "Initial Check: isLoggedIn=$isLoggedIn, isSessionExpired=$isExpired")

        startDestination = if (isLoggedIn && !isExpired) {
            Log.i("NavigationWrapper", "Valid session found. Starting at Home.")
            Screen.Home.route
        } else {
            if (isLoggedIn && isExpired) {
                Log.w("NavigationWrapper", "Session found but expired. Clearing session data.")
                sessionManager.clearSession()
            } else if (!isLoggedIn) {
                Log.i("NavigationWrapper", "No active session found.")
            }
            Log.i("NavigationWrapper", "Starting at Login.")
            Screen.Login.route
        }
        isLoadingDestination = false
    }

    if (isLoadingDestination) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = startDestination!!
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onPrescriptionClick = { prescriptionId, diagnosis ->
                        navController.navigate(
                            Screen.PrescriptionDetail.createRoute(prescriptionId, diagnosis)
                        )
                    },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.PrescriptionDetail.route
            ) { backStackEntry ->
                val prescriptionId = backStackEntry.arguments?.getString("prescriptionId") ?: ""
                val diagnosis = backStackEntry.arguments?.getString("diagnosis") ?: ""
                PrescriptionDetailScreen(
                    prescriptionId = prescriptionId,
                    prescriptionDiagnosis = diagnosis,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}