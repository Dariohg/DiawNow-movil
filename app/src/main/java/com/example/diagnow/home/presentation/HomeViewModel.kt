package com.example.diagnow.home.presentation

import android.app.Application // Importar Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel // Cambiar a AndroidViewModel
import androidx.lifecycle.ViewModel // Importar ViewModel base
import androidx.lifecycle.ViewModelProvider // Importar Factory
import androidx.lifecycle.viewModelScope
import com.example.diagnow.DiagNowApplication // Importar Application class
import com.example.diagnow.core.database.entity.PrescriptionEntity
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.model.PrescriptionResponse
import com.example.diagnow.home.domain.GetPrescriptionMedicationsUseCase
import com.example.diagnow.home.domain.GetPrescriptionsUseCase
import kotlinx.coroutines.Dispatchers // Importar Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch // Importar catch para manejo de errores en Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Importar withContext

data class HomeUiState(
    val prescriptions: List<PrescriptionResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    application: Application, // Añadir Application
    private val getPrescriptionsUseCase: GetPrescriptionsUseCase,
    private val localRepository: LocalDataRepository,
    private val sessionManager: SessionManager
) : AndroidViewModel(application) { // Heredar de AndroidViewModel

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Verificar sesión al inicio antes de cargar nada
        if (sessionManager.isSessionExpired()) {
            Log.w("HomeViewModel", "Session expired on ViewModel init. Clearing data.")
            // Si expira al inicio, limpiar datos y potencialmente navegar fuera (la UI lo manejará)
            logout() // Llama a la función de limpieza completa
            _uiState.update { it.copy(isLoading = false, error = "Sesión expirada.") } // Indicar estado
        } else {
            Log.d("HomeViewModel", "Session active on init. Loading prescriptions.")
            loadPrescriptions() // Cargar prescripciones si la sesión es válida
            observeLocalPrescriptions() // Observar cambios locales
        }
    }

    fun loadPrescriptions() {
        // Solo cargar si la sesión no ha expirado justo ahora
        if (sessionManager.isSessionExpired()) {
            Log.w("HomeViewModel", "Attempted to load prescriptions but session is expired.")
            _uiState.update { it.copy(isLoading = false, error = "Sesión expirada.") }
            // Considerar llamar a logout() aquí también si la UI no lo fuerza
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
                        // No necesitamos actualizar UI aquí, observeLocalPrescriptions lo hará
                        // Solo quitamos el estado de carga si la observación no lo hizo ya
                        if (_uiState.value.isLoading) {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    },
                    onFailure = { error ->
                        Log.e("HomeViewModel", "Error fetching remote prescriptions", error)
                        // Mantener isLoading=false y mostrar error si la carga local también falla
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
                .catch { e -> // Manejar errores del Flow de Room
                    Log.e("HomeViewModel", "Error observing local prescriptions", e)
                    _uiState.update { it.copy(isLoading = false, error = "Error al leer datos locales.") }
                }
                .collectLatest { localPrescriptions ->
                    Log.d("HomeViewModel", "Local prescriptions updated. Count: ${localPrescriptions.size}")
                    val prescriptionResponses = localPrescriptions.map { convertEntityToResponse(it) }
                    // Actualizar UI con datos locales, asegurando que isLoading sea false
                    _uiState.update {
                        it.copy(
                            prescriptions = prescriptionResponses,
                            isLoading = false // Marcar como no cargando al recibir datos locales
                            // No limpiar error aquí, podría haber un error de red previo
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
            medications = emptyList(), // Correcto, no cargar meds aquí
            notes = entity.notes,
            createdAt = entity.createdAt
        )
    }

    /**
     * Cierra la sesión del usuario, borrando los datos de SharedPreferences y la base de datos local.
     */
    fun logout() {
        viewModelScope.launch { // Usar el viewModelScope por defecto (Main) para iniciar
            try {
                Log.i("HomeViewModel", "Initiating logout process...")
                // 1. Borrar datos de SharedPreferences (rápido, puede ser en Main)
                sessionManager.clearSession()
                Log.d("HomeViewModel", "Session cleared from SharedPreferences.")

                // 2. Borrar datos de la Base de Datos Room (operación IO)
                withContext(Dispatchers.IO) { // Cambiar a contexto IO para BD
                    val database = (getApplication<DiagNowApplication>().database)
                    database.clearAllData()
                }
                Log.d("HomeViewModel", "Local database cleared.")
                // Actualizar UI para reflejar estado vacío (opcional, la navegación lo hará implícito)
                _uiState.update { HomeUiState() } // Resetear estado

            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error during logout data clearing", e)
                _uiState.update { it.copy(error = "Error al cerrar sesión.")}
            }
            // La navegación a Login la maneja la UI que llama a este método y luego a onLogout()
        }
    }
}

// --- Factory para HomeViewModel (sin cambios respecto a la anterior) ---
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