package com.example.diagnow.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.presentation.HomeScreen
import com.example.diagnow.login.presentation.LoginScreen
import com.example.diagnow.register.presentation.RegisterScreen

@Composable
fun NavigationWrapper(
    navController: NavHostController = rememberNavController()
) {
    // Determinar la ruta inicial basándose en el estado de autenticación
    val startDestination = Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Rutas de autenticación
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }

        // Ruta principal
        composable(Screen.Home.route) {
            HomeScreen(
                onPrescriptionClick = { prescriptionId ->
                    navController.navigate(
                        Screen.PrescriptionDetail.createRoute(prescriptionId)
                    )
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Detalle de receta
        composable(
            route = Screen.PrescriptionDetail.route
        ) { backStackEntry ->
            val prescriptionId = backStackEntry.arguments?.getString("prescriptionId") ?: ""
            // La pantalla de detalle se implementará más adelante
        }
    }
}