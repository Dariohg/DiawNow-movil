package com.example.diagnow.login.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnow.core.fcm.domain.RegisterDeviceTokenUseCase
import com.example.diagnow.core.fcm.getFcmToken
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
    private val registerDeviceTokenUseCase: RegisterDeviceTokenUseCase
) : ViewModel() {

    private val TAG = "LoginViewModel"

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun onLoginClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val loginResult = loginUseCase(
                email = _uiState.value.email,
                password = _uiState.value.password
            )

            loginResult.fold(
                onSuccess = { loggedInUser ->
                    Log.i(TAG, "Login successful for user: ${loggedInUser}. Proceeding to register FCM token.")

                    registerCurrentDeviceToken()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true
                        )
                    }
                },
                onFailure = { throwable ->
                    Log.e(TAG, "Login failed", throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Error desconocido al iniciar sesión"
                        )
                    }
                }
            )
        }
    }

    private fun registerCurrentDeviceToken() {
        viewModelScope.launch {
            try {
                val fcmToken = getFcmToken()

                if (fcmToken != null) {
                    Log.d(TAG, "Obtained FCM token: ${fcmToken.take(10)}... Attempting to register.")
                    val registrationResult = registerDeviceTokenUseCase(fcmToken)

                    registrationResult.fold(
                        onSuccess = {
                            Log.i(TAG, "FCM token registered successfully on backend.")
                        },
                        onFailure = { registrationError ->
                            Log.e(TAG, "Failed to register FCM token on backend.", registrationError)
                        }
                    )
                } else {
                    Log.e(TAG, "Could not get FCM token from Firebase to register.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during FCM token retrieval/registration.", e)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}