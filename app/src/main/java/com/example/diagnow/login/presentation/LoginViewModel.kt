// --- START OF (MODIFIED) diagnow/login/presentation/LoginViewModel.kt ---
package com.example.diagnow.login.presentation

import android.util.Log // Necesario para Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnow.core.fcm.domain.RegisterDeviceTokenUseCase // Importar el nuevo Use Case
import com.example.diagnow.core.fcm.getFcmToken // Importar la función para obtener token
import com.example.diagnow.login.domain.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    // --- NECESITAS PROVEER ESTE CASO DE USO ---
    // Si no usas DI (Hilt/Koin), tendrás que instanciarlo aquí o pasarlo desde donde creas el ViewModel
    private val registerDeviceTokenUseCase: RegisterDeviceTokenUseCase
) : ViewModel() {

    private val TAG = "LoginViewModel" // Tag para logs

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    // La UI llama a esta función sin parámetros ahora
    fun onLoginClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. Ejecutar el caso de uso de Login (ya no le pasamos el token FCM)
            val loginResult = loginUseCase(
                email = _uiState.value.email,
                password = _uiState.value.password
                // No deviceToken here anymore
            )

            loginResult.fold(
                onSuccess = { loggedInUser ->
                    // 2. ¡Login Exitoso! El token JWT y User YA están guardados por el LoginRepository/SessionManager.
                    Log.i(TAG, "Login successful for user: ${loggedInUser}. Proceeding to register FCM token.")

                    // 3. Intentar registrar el token FCM del dispositivo AHORA que estamos logueados
                    registerCurrentDeviceToken() // Llama a la función separada

                    // 4. Actualizar el estado de la UI para reflejar el éxito del login
                    // La navegación se disparará al observar isLoggedIn = true
                    _uiState.update {
                        it.copy(
                            isLoading = false, // Termina la carga del login
                            isLoggedIn = true // Indica que el login fue exitoso
                        )
                    }
                },
                onFailure = { throwable ->
                    // Login falló
                    Log.e(TAG, "Login failed", throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false, // Termina la carga
                            error = throwable.message ?: "Error desconocido al iniciar sesión"
                        )
                    }
                }
            )
        }
    }

    /**
     * Función privada para obtener el token FCM actual y intentar registrarlo en el backend.
     * Se llama DESPUÉS de un login exitoso.
     */
    private fun registerCurrentDeviceToken() {
        // Usamos un launch separado porque obtener/registrar el token es una operación
        // secundaria y no debería bloquear la actualización del estado de login si falla.
        viewModelScope.launch {
            try {
                // a) Obtener el token FCM actual
                val fcmToken = getFcmToken() // Llama a nuestra función suspend

                if (fcmToken != null) {
                    // b) Si obtuvimos el token, llamar al caso de uso para registrarlo
                    Log.d(TAG, "Obtained FCM token: ${fcmToken.take(10)}... Attempting to register.")
                    val registrationResult = registerDeviceTokenUseCase(fcmToken)

                    registrationResult.fold(
                        onSuccess = {
                            Log.i(TAG, "FCM token registered successfully on backend.")
                            // Podrías guardar en SessionManager que el token fue enviado exitosamente
                            // sessionManager.setDeviceTokenSent(true) // Si necesitas esta lógica
                        },
                        onFailure = { registrationError ->
                            // No se pudo registrar el token en el backend
                            // El login YA fue exitoso, así que solo logueamos el error.
                            // Podríamos intentar reenviarlo más tarde.
                            Log.e(TAG, "Failed to register FCM token on backend.", registrationError)
                            // Podrías guardar el token localmente para reintentar
                            // sessionManager.setPendingDeviceToken(fcmToken) // Si implementas reintentos
                        }
                    )
                } else {
                    // No se pudo obtener el token FCM del dispositivo
                    Log.e(TAG, "Could not get FCM token from Firebase to register.")
                    // Considera guardar un flag para intentarlo más tarde
                }
            } catch (e: Exception) {
                // Captura cualquier otra excepción durante el proceso de obtener/registrar token
                Log.e(TAG, "Exception during FCM token retrieval/registration.", e)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
// --- END OF (MODIFIED) diagnow/login/presentation/LoginViewModel.kt ---