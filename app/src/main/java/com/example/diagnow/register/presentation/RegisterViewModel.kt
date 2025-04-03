package com.example.diagnow.register.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnow.register.domain.RegisterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val age: String = "",
    val height: String = "",
    val weight: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRegistered: Boolean = false
)

class RegisterViewModel(private val registerUseCase: RegisterUseCase) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onLastNameChanged(lastName: String) {
        _uiState.update { it.copy(lastName = lastName) }
    }

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword) }
    }

    fun onAgeChanged(age: String) {
        if (age.isEmpty() || age.all { it.isDigit() }) {
            _uiState.update { it.copy(age = age) }
        }
    }

    fun onHeightChanged(height: String) {
        if (height.isEmpty() || height.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(height = height) }
        }
    }

    fun onWeightChanged(weight: String) {
        if (weight.isEmpty() || weight.all { it.isDigit() || it == '.' }) {
            _uiState.update { it.copy(weight = weight) }
        }
    }

    fun onRegisterClicked(deviceToken: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val age = _uiState.value.age.toIntOrNull() ?: 0
                val height = _uiState.value.height.toDoubleOrNull()
                val weight = _uiState.value.weight.toDoubleOrNull()

                val result = registerUseCase(
                    name = _uiState.value.name,
                    lastName = _uiState.value.lastName,
                    email = _uiState.value.email,
                    password = _uiState.value.password,
                    confirmPassword = _uiState.value.confirmPassword,
                    age = age,
                    height = height,
                    weight = weight,
                    deviceToken = deviceToken
                )

                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRegistered = true
                            )
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = throwable.message ?: "Error al registrarse"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error al procesar los datos"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}