package com.example.diagnow.home.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnow.core.database.entity.PrescriptionEntity
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.model.PrescriptionResponse
import com.example.diagnow.home.domain.GetPrescriptionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val prescriptions: List<PrescriptionResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val getPrescriptionsUseCase: GetPrescriptionsUseCase,
    private val localRepository: LocalDataRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPrescriptions()
        observeLocalPrescriptions()
    }

    fun loadPrescriptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                Log.d("HomeViewModel", "Iniciando carga de prescripciones")

                // Intentar cargar prescripciones remotas y almacenarlas localmente
                getPrescriptionsUseCase.fetchAndSaveRemotePrescriptions()
                    .fold(
                        onSuccess = { prescriptions ->
                            Log.d(
                                "HomeViewModel",
                                "Prescripciones remotas cargadas con éxito: ${prescriptions.size}"
                            )
                            _uiState.update {
                                it.copy(
                                    prescriptions = prescriptions,
                                    isLoading = false
                                )
                            }
                        },
                        onFailure = { error ->
                            Log.e("HomeViewModel", "Error al cargar prescripciones remotas", error)
                            // No actualizamos el estado de error aquí porque usaremos datos locales
                            _uiState.update { it.copy(isLoading = false) }
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

    private fun observeLocalPrescriptions() {
        viewModelScope.launch {
            localRepository.getAllPrescriptions().collectLatest { localPrescriptions ->
                if (localPrescriptions.isNotEmpty()) {
                    Log.d(
                        "HomeViewModel",
                        "Prescripciones locales actualizadas: ${localPrescriptions.size}"
                    )

                    // Convertir entidades locales a modelo de presentación
                    val prescriptionResponses = localPrescriptions.map { entity ->
                        convertEntityToResponse(entity)
                    }

                    // Solo actualizar si hay datos y no estamos cargando datos remotos
                    if (!_uiState.value.isLoading || _uiState.value.prescriptions.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                prescriptions = prescriptionResponses,
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }
    }

    private fun convertEntityToResponse(entity: PrescriptionEntity): PrescriptionResponse {
        return PrescriptionResponse(
            id = entity.id,
            patientId = entity.patientId,
            doctorName = entity.doctorName,
            date = entity.date,
            diagnosis = entity.diagnosis,
            status = entity.status,
            medications = emptyList(), // Los medicamentos se cargarán por separado cuando sea necesario
            notes = entity.notes,
            createdAt = entity.createdAt
        )
    }

    fun logout() {
        sessionManager.clearSession()
    }
}