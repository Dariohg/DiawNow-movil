package com.example.diagnow.home.domain

import android.util.Log
import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.home.data.model.PrescriptionDetailResponse
import com.example.diagnow.home.data.repository.PrescriptionRepository
import kotlinx.coroutines.flow.Flow

class GetPrescriptionMedicationsUseCase(
    private val remoteRepository: PrescriptionRepository,
    private val localRepository: LocalDataRepository
) {
    suspend fun fetchAndSyncRemoteMedications(prescriptionId: String): Result<Unit> {
        Log.d("GetMedsUseCase", "[FLOW-ONLY] Attempting fetch and sync for prescription: $prescriptionId")
        val remoteResult = remoteRepository.getPrescriptionMedications(prescriptionId)
        return if (remoteResult.isSuccess) {
            try {
                localRepository.saveMedications(remoteResult.getOrNull()?.data?.medications ?: emptyList(), prescriptionId)
                Log.i("GetMedsUseCase", "[FLOW-ONLY] Sync completed successfully for prescription: $prescriptionId")
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        } else {
            Result.failure(remoteResult.exceptionOrNull() ?: Exception("Unknown remote error"))
        }
    }

    fun getLocalMedicationsFlow(prescriptionId: String): Flow<List<MedicationEntity>> {
        Log.d("GetMedsUseCase", "[FLOW-ONLY] Providing Flow of local medications for prescription: $prescriptionId")
        return localRepository.getLocalMedicationsFlow(prescriptionId)
    }
}