package com.example.diagnow.core.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")

    object Home : Screen("home")

    object PrescriptionDetail : Screen("prescription/{prescriptionId}/{diagnosis}") {
        fun createRoute(prescriptionId: String, diagnosis: String) =
            "prescription/$prescriptionId/$diagnosis"
    }
}