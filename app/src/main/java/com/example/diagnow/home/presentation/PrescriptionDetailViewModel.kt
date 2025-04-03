package com.example.diagnow.home.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diagnow.home.data.model.MedicationDetailResponse
import com.example.diagnow.home.domain.GetPrescriptionMedicationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrescriptionDetailUiState(
    val prescriptionId: String = "",
    val medications: List<MedicationDetailResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PrescriptionDetailViewModel(
    private val getPrescriptionMedicationsUseCase: GetPrescriptionMedicationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrescriptionDetailUiState())
    val uiState: StateFlow<PrescriptionDetailUiState> = _uiState.asStateFlow()

    fun loadPrescriptionMedications(prescriptionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, prescriptionId = prescriptionId, error = null) }

            try {
                Log.d("PrescriptionDetailVM", "Iniciando carga de medicamentos para receta: $prescriptionId")

                getPrescriptionMedicationsUseCase(prescriptionId)
                    .fold(
                        onSuccess = { response ->
                            Log.d("PrescriptionDetailVM", "Medicamentos cargados con éxito: ${response.data.medications.size}")
                            _uiState.update {
                                it.copy(
                                    medications = response.data.medications,
                                    isLoading = false
                                )
                            }
                        },
                        onFailure = { error ->
                            Log.e("PrescriptionDetailVM", "Error al cargar medicamentos", error)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = error.message ?: "Error desconocido"
                                )
                            }
                        }
                    )
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}