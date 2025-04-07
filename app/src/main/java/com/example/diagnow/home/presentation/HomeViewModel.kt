package com.example.diagnow.home.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diagnow.DiagNowApplication
import com.example.diagnow.core.database.entity.PrescriptionEntity
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.model.PrescriptionResponse
import com.example.diagnow.home.domain.GetPrescriptionMedicationsUseCase
import com.example.diagnow.home.domain.GetPrescriptionsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val prescriptions: List<PrescriptionResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    application: Application,
    private val getPrescriptionsUseCase: GetPrescriptionsUseCase,
    private val localRepository: LocalDataRepository,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        if (sessionManager.isSessionExpired()) {
            Log.w("HomeViewModel", "Session expired on ViewModel init. Clearing data.")
            logout()
            _uiState.update { it.copy(isLoading = false, error = "Sesión expirada.") }
        } else {
            Log.d("HomeViewModel", "Session active on init. Loading prescriptions.")
            loadPrescriptions()
            observeLocalPrescriptions()
        }
    }

    fun loadPrescriptions() {
        if (sessionManager.isSessionExpired()) {
            Log.w("HomeViewModel", "Attempted to load prescriptions but session is expired.")
            _uiState.update { it.copy(isLoading = false, error = "Sesión expirada.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                Log.d("HomeViewModel", "Fetching remote prescriptions...")
                val result = getPrescriptionsUseCase.fetchAndSaveRemotePrescriptions()
                result.fold(
                    onSuccess = { prescriptions ->
                        Log.d("HomeViewModel", "Remote prescriptions fetched successfully: ${prescriptions.size}")
                        if (_uiState.value.isLoading) {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    },
                    onFailure = { error ->
                        Log.e("HomeViewModel", "Error fetching remote prescriptions", error)
                        _uiState.update { it.copy(isLoading = false, error = "No se pudo actualizar: ${error.message}") }
                    }
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Exception during prescription loading", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Error inesperado: ${e.message}")
                }
            }
        }
    }

    private fun observeLocalPrescriptions() {
        viewModelScope.launch {
            Log.d("HomeViewModel", "Starting to observe local prescriptions.")
            localRepository.getAllPrescriptions()
                .catch { e ->
                    Log.e("HomeViewModel", "Error observing local prescriptions", e)
                    _uiState.update { it.copy(isLoading = false, error = "Error al leer datos locales.") }
                }
                .collectLatest { localPrescriptions ->
                    Log.d("HomeViewModel", "Local prescriptions updated. Count: ${localPrescriptions.size}")
                    val prescriptionResponses = localPrescriptions.map { convertEntityToResponse(it) }
                    _uiState.update {
                        it.copy(
                            prescriptions = prescriptionResponses,
                            isLoading = false
                        )
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
            medications = emptyList(),
            notes = entity.notes,
            createdAt = entity.createdAt
        )
    }

    fun logout() {
        viewModelScope.launch {
            try {
                Log.i("HomeViewModel", "Initiating logout process...")
                sessionManager.clearSession()
                Log.d("HomeViewModel", "Session cleared from SharedPreferences.")

                withContext(Dispatchers.IO) {
                    val database = (getApplication<DiagNowApplication>().database)
                    database.clearAllData()
                }
                Log.d("HomeViewModel", "Local database cleared.")
                _uiState.update { HomeUiState() }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error during logout data clearing", e)
                _uiState.update { it.copy(error = "Error al cerrar sesión.")}
            }
        }
    }
}

class HomeViewModelFactory(
    private val application: Application,
    private val getPrescriptionsUseCase: GetPrescriptionsUseCase,
    private val localRepository: LocalDataRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(application, getPrescriptionsUseCase, localRepository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}