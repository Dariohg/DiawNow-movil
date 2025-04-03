package com.example.diagnow.home.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.home.data.model.MedicationDetailResponse
import com.example.diagnow.home.domain.GetPrescriptionMedicationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrescriptionDetailUiState(
    val prescriptionId: String = "",
    val medications: List<MedicationDetailResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PrescriptionDetailViewModel(
    private val getPrescriptionMedicationsUseCase: GetPrescriptionMedicationsUseCase,
    private val localRepository: LocalDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrescriptionDetailUiState())
    val uiState: StateFlow<PrescriptionDetailUiState> = _uiState.asStateFlow()

    fun loadPrescriptionMedications(prescriptionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, prescriptionId = prescriptionId, error = null) }

            try {
                Log.d("PrescriptionDetailVM", "Iniciando carga de medicamentos para receta: $prescriptionId")

                // Intentar cargar medicamentos remotos primero
                getPrescriptionMedicationsUseCase.fetchAndSaveRemoteMedications(prescriptionId)
                    .fold(
                        onSuccess = { response ->
                            Log.d("PrescriptionDetailVM", "Medicamentos remotos cargados con éxito: ${response.data.medications.size}")
                            _uiState.update {
                                it.copy(
                                    medications = response.data.medications,
                                    isLoading = false
                                )
                            }
                        },
                        onFailure = { error ->
                            Log.e("PrescriptionDetailVM", "Error al cargar medicamentos remotos", error)
                            // No actualizamos el estado de error porque usaremos datos locales
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    )

                // Observar medicamentos locales
                observeLocalMedications(prescriptionId)

            } catch (e: Exception) {
                Log.e("PrescriptionDetailVM", "Excepción al cargar medicamentos", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error inesperado: ${e.message}"
                    )
                }
            }
        }
    }

    private fun observeLocalMedications(prescriptionId: String) {
        viewModelScope.launch {
            localRepository.getMedicationsByPrescriptionId(prescriptionId).collectLatest { entities ->
                if (entities.isNotEmpty()) {
                    Log.d("PrescriptionDetailVM", "Medicamentos locales actualizados: ${entities.size}")

                    val medicationResponses = entities.map { entity ->
                        convertEntityToResponse(entity)
                    }

                    // Solo actualizar si no tenemos datos o si no estamos cargando
                    if (!_uiState.value.isLoading || _uiState.value.medications.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                medications = medicationResponses,
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }
    }

    private fun convertEntityToResponse(entity: MedicationEntity): MedicationDetailResponse {
        return MedicationDetailResponse(
            id = entity.id,
            prescriptionId = entity.prescriptionId,
            name = entity.name,
            dosage = entity.dosage,
            frequency = entity.frequency,
            days = entity.days,
            administrationRoute = entity.administrationRoute,
            instructions = entity.instructions,
            createdAt = entity.createdAt
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}