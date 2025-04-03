package com.example.diagnow.core.navigation

// Definición de rutas para la navegación
sealed class Screen(val route: String) {
    // Autenticación
    object Login : Screen("login")
    object Register : Screen("register")

    // Pantalla principal
    object Home : Screen("home")

    // Detalle de receta
    object PrescriptionDetail : Screen("prescription/{prescriptionId}") {
        fun createRoute(prescriptionId: String) = "prescription/$prescriptionId"
    }
}