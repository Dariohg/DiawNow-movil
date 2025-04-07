package com.example.diagnow.core.navigation

import android.util.Log // Importar Log
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
import androidx.navigation.NavGraph.Companion.findStartDestination // Importar
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.diagnow.core.session.SessionManager // Importar SessionManager
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

    // Estado para almacenar la ruta de inicio. Inicialmente null.
    var startDestination by remember { mutableStateOf<String?>(null) }
    // Estado para indicar si la comprobación inicial está completa.
    var isLoadingDestination by remember { mutableStateOf(true) }

    // Efecto lanzado una sola vez para determinar la ruta inicial.
    LaunchedEffect(key1 = Unit) {
        // Comprobar si hay token y si la sesión (basada en el cliente) ha expirado.
        val isLoggedIn = sessionManager.isLoggedIn()
        val isExpired = sessionManager.isSessionExpired() // Usa la lógica de 12 horas

        Log.d("NavigationWrapper", "Initial Check: isLoggedIn=$isLoggedIn, isSessionExpired=$isExpired")

        // Determinar la ruta
        startDestination = if (isLoggedIn && !isExpired) {
            // Sesión válida y no expirada -> Ir a Home
            Log.i("NavigationWrapper", "Valid session found. Starting at Home.")
            Screen.Home.route
        } else {
            // No hay sesión o ha expirado
            if (isLoggedIn && isExpired) {
                // Si había sesión pero expiró, limpiarla (solo SharedPreferences aquí)
                Log.w("NavigationWrapper", "Session found but expired. Clearing session data.")
                sessionManager.clearSession()
            } else if (!isLoggedIn) {
                Log.i("NavigationWrapper", "No active session found.")
            }
            // En ambos casos (no logueado o expirado) -> Ir a Login
            Log.i("NavigationWrapper", "Starting at Login.")
            Screen.Login.route
        }
        // Marcar que la determinación de la ruta ha terminado.
        isLoadingDestination = false
    }

    // --- Renderizado condicional ---
    // Muestra un indicador de carga mientras se determina la ruta inicial.
    if (isLoadingDestination) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator() // O una pantalla de Splash más elaborada
        }
    } else {
        // Una vez determinada la ruta, crea el NavHost.
        // Usamos startDestination!! porque garantizamos que no es null en este punto.
        NavHost(
            navController = navController,
            startDestination = startDestination!!
        ) {
            // --- Rutas de Autenticación ---
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                    onNavigateToHome = {
                        // Navegar a Home y limpiar todo el backstack hasta el inicio del grafo.
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            // Asegurar una única instancia de Home
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = {
                        // Navegar a Login y limpiar todo el backstack.
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            // Asegurar una única instancia de Login
                            launchSingleTop = true
                        }
                    }
                )
            }

            // --- Rutas Principales (Autenticadas) ---
            composable(Screen.Home.route) {
                HomeScreen(
                    onPrescriptionClick = { prescriptionId, diagnosis ->
                        // Navegar a detalles
                        navController.navigate(
                            Screen.PrescriptionDetail.createRoute(prescriptionId, diagnosis)
                        )
                    },
                    onLogout = {
                        // Navegar a Login y limpiar todo el backstack.
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
                // arguments = listOf(...) // Definir argumentos si es necesario (ya en la ruta)
            ) { backStackEntry ->
                // Extraer argumentos de forma segura
                val prescriptionId = backStackEntry.arguments?.getString("prescriptionId") ?: ""
                val diagnosis = backStackEntry.arguments?.getString("diagnosis") ?: ""
                PrescriptionDetailScreen(
                    prescriptionId = prescriptionId,
                    prescriptionDiagnosis = diagnosis,
                    onBackClick = { navController.popBackStack() } // Botón atrás simple
                )
            }
        } // Fin NavHost
    } // Fin else (isLoadingDestination == false)
}