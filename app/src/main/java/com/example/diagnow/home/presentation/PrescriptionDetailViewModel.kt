package com.example.diagnow.home.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diagnow.core.foreground.alarms.MedicationAlarmScheduler // Corregido import
import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.core.database.entity.TreatmentStatus
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.home.data.model.MedicationDetailResponse
import com.example.diagnow.home.domain.GetPrescriptionMedicationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class PrescriptionDetailUiState(
    val prescriptionId: String = "",
    val medications: List<MedicationDetailResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PrescriptionDetailViewModel(
    application: Application,
    private val getPrescriptionMedicationsUseCase: GetPrescriptionMedicationsUseCase, // Necesaria para fetch inicial
    private val localRepository: LocalDataRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PrescriptionDetailUiState())
    val uiState: StateFlow<PrescriptionDetailUiState> = _uiState.asStateFlow()

    private val alarmScheduler = MedicationAlarmScheduler(getApplication())

    /**
     * Carga medicamentos: Local primero, si vacío, intenta remoto y guarda localmente (ignorando existentes).
     */
    fun loadPrescriptionMedications(prescriptionId: String) {
        // Evitar recargas innecesarias si ya se está mostrando la misma prescripción
        if (prescriptionId == _uiState.value.prescriptionId && _uiState.value.medications.isNotEmpty() && !_uiState.value.isLoading) {
            Log.d("PrescriptionDetailVM", "Data for prescription $prescriptionId already displayed. Skipping reload.")
            return
        }

        Log.i("PrescriptionDetailVM", "Loading medications for prescription: $prescriptionId (Local First approach)")
        _uiState.update { it.copy(isLoading = true, prescriptionId = prescriptionId, error = null) }

        viewModelScope.launch {
            var finalError: String? = null
            try {
                // 1. Cargar Local Primero
                var localMeds = localRepository.getLocalMedicationsList(prescriptionId)
                Log.d("PrescriptionDetailVM", "Initial local fetch found ${localMeds.size} medications.")

                // 2. Si Local está vacío, intentar Remoto
                if (localMeds.isEmpty()) {
                    Log.i("PrescriptionDetailVM", "Local data empty for $prescriptionId. Attempting remote fetch and save...")
                    val syncResult = getPrescriptionMedicationsUseCase.fetchAndSyncRemoteMedications(prescriptionId)

                    if (syncResult.isSuccess) {
                        Log.d("PrescriptionDetailVM", "Remote fetch/save successful. Reloading local data.")
                        // Volver a cargar local DESPUÉS de guardar/sincronizar
                        localMeds = localRepository.getLocalMedicationsList(prescriptionId)
                        Log.d("PrescriptionDetailVM", "Reloaded local data, found ${localMeds.size} medications.")
                    } else {
                        finalError = "Error al cargar medicamentos: ${syncResult.exceptionOrNull()?.message}"
                        Log.e("PrescriptionDetailVM", "Remote fetch failed and local was empty.", syncResult.exceptionOrNull())
                        // No salir, actualizar UI con lista vacía y error
                    }
                }
                // Si local NO estaba vacío, no hacemos fetch remoto aquí.

                // 3. Mapear y actualizar UI
                val medicationResponses = localMeds.map { convertEntityToResponse(it) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        medications = medicationResponses,
                        error = finalError // Mostrar error solo si fetch remoto falló y local estaba vacío
                    )
                }

            } catch (e: Exception) {
                Log.e("PrescriptionDetailVM", "Exception during medication loading", e)
                _uiState.update { it.copy(isLoading = false, error = "Error inesperado: ${e.message}") }
            }
        }
    }

    private fun convertEntityToResponse(entity: MedicationEntity): MedicationDetailResponse {
        return MedicationDetailResponse(
            id = entity.id, prescriptionId = entity.prescriptionId, name = entity.name,
            dosage = entity.dosage, frequency = entity.frequency, days = entity.days,
            administrationRoute = entity.administrationRoute, instructions = entity.instructions,
            createdAt = entity.createdAt, treatmentStatus = entity.treatmentStatus,
            treatmentStartDate = entity.treatmentStartDate
        )
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }

    fun startTreatment(medicationId: String) {
        viewModelScope.launch {
            val updateResult = localRepository.updateMedicationTreatmentStatus(medicationId, TreatmentStatus.ACTIVE, Date())
            if (updateResult.isSuccess) {
                val medication = localRepository.getMedicationById(medicationId)
                if (medication != null) {
                    try { alarmScheduler.scheduleFirstAlarm(medication) }
                    catch (se: SecurityException) { _uiState.update { it.copy(error = "Permiso de alarma necesario.") } }
                    catch (e: Exception) { _uiState.update { it.copy(error = "Error al programar recordatorio.") } }
                } else { _uiState.update { it.copy(error = "Error al obtener datos para alarma.") } }
                loadPrescriptionMedications(_uiState.value.prescriptionId) // Recargar UI
            } else {
                val errorMsg = "Error al guardar inicio tratamiento: ${updateResult.exceptionOrNull()?.message}"
                _uiState.update { it.copy(error = errorMsg) }
            }
        }
    }

    fun endTreatment(medicationId: String) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(medicationId)
            val updateResult = localRepository.updateMedicationTreatmentStatus(medicationId, TreatmentStatus.COMPLETED, null)
            if (updateResult.isSuccess) {
                loadPrescriptionMedications(_uiState.value.prescriptionId) // Recargar UI
            } else {
                val errorMsg = "Error al finalizar tratamiento: ${updateResult.exceptionOrNull()?.message}"
                _uiState.update { it.copy(error = errorMsg) }
            }
        }
    }

    override fun onCleared() { super.onCleared(); Log.d("PrescriptionDetailVM", "ViewModel cleared.") }
}

// --- Factory ---
class PrescriptionDetailViewModelFactory(
    private val application: Application,
    private val getPrescriptionMedicationsUseCase: GetPrescriptionMedicationsUseCase, // Necesaria de nuevo
    private val localRepository: LocalDataRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PrescriptionDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PrescriptionDetailViewModel(application, getPrescriptionMedicationsUseCase, localRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}