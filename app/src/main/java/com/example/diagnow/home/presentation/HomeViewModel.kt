package com.example.diagnow.home.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.model.PrescriptionResponse
import com.example.diagnow.home.domain.GetPrescriptionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val prescriptions: List<PrescriptionResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val getPrescriptionsUseCase: GetPrescriptionsUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPrescriptions()
    }

    fun loadPrescriptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                Log.d("HomeViewModel", "Iniciando carga de prescripciones")

                getPrescriptionsUseCase()
                    .fold(
                        onSuccess = { prescriptions ->
                            Log.d("HomeViewModel", "Prescripciones cargadas con éxito: ${prescriptions.size}")
                            _uiState.update {
                                it.copy(
                                    prescriptions = prescriptions,
                                    isLoading = false
                                )
                            }
                        },
                        onFailure = { error ->
                            Log.e("HomeViewModel", "Error al cargar prescripciones", error)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Error: ${error.message}"
                                )
                            }
                        }
                    )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Excepción al cargar prescripciones", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error inesperado: ${e.message}"
                    )
                }
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }
}