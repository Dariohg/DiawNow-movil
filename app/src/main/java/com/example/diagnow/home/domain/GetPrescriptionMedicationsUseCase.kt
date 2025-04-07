package com.example.diagnow.home.domain

import android.util.Log
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.home.data.repository.PrescriptionRepository

class GetPrescriptionMedicationsUseCase(
    private val remoteRepository: PrescriptionRepository,
    private val localRepository: LocalDataRepository
) {
    /**
     * Intenta obtener los medicamentos desde la fuente remota (API) y los sincroniza
     * con la base de datos local usando la lógica "Ignorar Existentes" del LocalDataRepository.
     */
    suspend fun fetchAndSyncRemoteMedications(prescriptionId: String): Result<Unit> {
        Log.d("GetMedsUseCase", "[SUSPEND-IGNORE] Attempting fetch and sync for prescription: $prescriptionId")
        val remoteResult = remoteRepository.getPrescriptionMedications(prescriptionId)

        return if (remoteResult.isSuccess) {
            val response = remoteResult.getOrNull()
            try {
                localRepository.saveMedications(response?.data?.medications ?: emptyList(), prescriptionId)
                Log.i("GetMedsUseCase", "[SUSPEND-IGNORE] Sync completed successfully for prescription: $prescriptionId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("GetMedsUseCase", "[SUSPEND-IGNORE] Error during local save/sync for prescription $prescriptionId", e)
                Result.failure(e)
            }
        } else {
            Log.w("GetMedsUseCase", "[SUSPEND-IGNORE] Remote fetch failed for prescription $prescriptionId: ${remoteResult.exceptionOrNull()?.message}")
            Result.failure(remoteResult.exceptionOrNull() ?: Exception("Unknown error fetching remote medications"))
        }
    }
}