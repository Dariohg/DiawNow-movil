package com.example.diagnow.core.navigation

// Definición de rutas para la navegación
sealed class Screen(val route: String) {
    // Autenticación
    object Login : Screen("login")
    object Register : Screen("register")

    // Pantalla principal
    object Home : Screen("home")

    object PrescriptionDetail : Screen("prescription/{prescriptionId}/{diagnosis}") {
        fun createRoute(prescriptionId: String, diagnosis: String) =
            "prescription/$prescriptionId/$diagnosis"
    }
}