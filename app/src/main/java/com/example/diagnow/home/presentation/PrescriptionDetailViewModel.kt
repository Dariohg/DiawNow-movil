package com.example.diagnow.home.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diagnow.core.foreground.alarms.MedicationAlarmScheduler
import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.core.database.entity.TreatmentStatus
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.home.data.model.MedicationDetailResponse
import com.example.diagnow.home.domain.GetPrescriptionMedicationsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.lang.IllegalArgumentException
import java.lang.SecurityException

data class PrescriptionDetailUiState(
    val prescriptionId: String = "",
    val medications: List<MedicationDetailResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PrescriptionDetailViewModel(
    application: Application,
    private val getPrescriptionMedicationsUseCase: GetPrescriptionMedicationsUseCase,
    private val localRepository: LocalDataRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PrescriptionDetailUiState())
    val uiState: StateFlow<PrescriptionDetailUiState> = _uiState.asStateFlow()

    private val alarmScheduler = MedicationAlarmScheduler(getApplication())
    private var currentPrescriptionId: String? = null
    private var medicationObserverJob: Job? = null

    fun loadPrescriptionMedications(prescriptionId: String) {
        if (prescriptionId == currentPrescriptionId) {
            Log.d("PrescriptionDetailVM", "[FLOW] Observation already active for $prescriptionId.")
            return
        }
        Log.i("PrescriptionDetailVM", "[FLOW] Loading/Observing medications for prescription: $prescriptionId")
        currentPrescriptionId = prescriptionId
        medicationObserverJob?.cancel()

        medicationObserverJob = observeLocalMedications(prescriptionId)
        triggerRemoteFetch(prescriptionId)
    }

    private fun observeLocalMedications(prescriptionId: String): Job {
        return viewModelScope.launch {
            Log.d("PrescriptionDetailVM", "[FLOW] Starting local medication observer for $prescriptionId")
            _uiState.update { it.copy(isLoading = true, error = null) }

            getPrescriptionMedicationsUseCase.getLocalMedicationsFlow(prescriptionId)
                .catch { e ->
                    Log.e("PrescriptionDetailVM", "[FLOW] Error collecting local medications Flow", e)
                    _uiState.update { it.copy(isLoading = false, error = "Error interno al leer datos: ${e.message}") }
                }
                .collectLatest { entities ->
                    Log.d("PrescriptionDetailVM", "[FLOW] Local Flow emitted ${entities.size} medications.")
                    val medicationResponses = entities.map { convertEntityToResponse(it) }
                    _uiState.update { currentState ->
                        currentState.copy(
                            medications = medicationResponses,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun triggerRemoteFetch(prescriptionId: String) {
        viewModelScope.launch {
            Log.d("PrescriptionDetailVM", "[FLOW] Triggering remote fetch for $prescriptionId")
            val syncResult = getPrescriptionMedicationsUseCase.fetchAndSyncRemoteMedications(prescriptionId)
            if (syncResult.isFailure) {
                val errorMsg = "No se pudo actualizar: ${syncResult.exceptionOrNull()?.message}"
                Log.e("PrescriptionDetailVM", "[FLOW] Remote fetch/sync failed", syncResult.exceptionOrNull())
                _uiState.update { it.copy(error = errorMsg ) }
            } else {
                Log.i("PrescriptionDetailVM", "[FLOW] Remote fetch/sync successful for $prescriptionId")
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
            createdAt = entity.createdAt,
            treatmentStatus = entity.treatmentStatus,
            treatmentStartDate = entity.treatmentStartDate
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }


    fun startTreatment(medicationId: String) {
        viewModelScope.launch {
            Log.d("PrescriptionDetailVM", "Attempting to start treatment for medication: $medicationId")
            val updateResult = localRepository.updateMedicationTreatmentStatus(medicationId, TreatmentStatus.ACTIVE, Date())

            if (updateResult.isSuccess) {
                Log.i("PrescriptionDetailVM", "Treatment start saved for $medicationId. Scheduling alarm.")
                val medication = localRepository.getMedicationById(medicationId)
                if (medication != null) {
                    try {
                        alarmScheduler.scheduleFirstAlarm(medication)
                    } catch (se: SecurityException) {
                        Log.e("PrescriptionDetailVM", "Permission error scheduling alarm for $medicationId", se)
                        _uiState.update { it.copy(error = "Se necesita permiso para programar alarmas.") }
                    } catch (e: Exception) {
                        Log.e("PrescriptionDetailVM", "Error scheduling alarm for $medicationId", e)
                        _uiState.update { it.copy(error = "Error al programar recordatorio: ${e.message}") }
                    }
                } else {
                    Log.e("PrescriptionDetailVM", "Medication $medicationId not found after update for alarm scheduling.")
                    _uiState.update { it.copy(error = "Error al obtener datos para alarma.") }
                }
            } else {
                val errorMsg = "Error al guardar inicio tratamiento: ${updateResult.exceptionOrNull()?.message}"
                _uiState.update { it.copy(error = errorMsg) }
                Log.e("PrescriptionDetailVM", "Failed to save treatment start", updateResult.exceptionOrNull())
            }
        }
    }

    fun endTreatment(medicationId: String) {
        viewModelScope.launch {
            Log.d("PrescriptionDetailVM", "Cancelling alarm for $medicationId before updating status.")
            alarmScheduler.cancelAlarm(medicationId)

            Log.d("PrescriptionDetailVM", "Attempting to end treatment status for medication: $medicationId")
            val updateResult = localRepository.updateMedicationTreatmentStatus(medicationId, TreatmentStatus.COMPLETED, null)

            if (updateResult.isSuccess) {
                Log.i("PrescriptionDetailVM", "Treatment end status updated successfully for $medicationId")
            } else {
                val errorMsg = "Error al finalizar tratamiento: ${updateResult.exceptionOrNull()?.message}"
                _uiState.update { it.copy(error = errorMsg) }
                Log.e("PrescriptionDetailVM", "Failed to update treatment end status", updateResult.exceptionOrNull())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        medicationObserverJob?.cancel()
        Log.d("PrescriptionDetailVM", "[FLOW] ViewModel cleared, medication observer cancelled.")
    }
}

class PrescriptionDetailViewModelFactory(
    private val application: Application,
    private val getPrescriptionMedicationsUseCase: GetPrescriptionMedicationsUseCase,
    private val localRepository: LocalDataRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PrescriptionDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PrescriptionDetailViewModel(application, getPrescriptionMedicationsUseCase, localRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}