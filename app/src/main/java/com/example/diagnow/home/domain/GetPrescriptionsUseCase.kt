package com.example.diagnow.home.domain

import com.example.diagnow.core.database.entity.PrescriptionEntity
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.home.data.model.PrescriptionResponse
import com.example.diagnow.home.data.repository.PrescriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GetPrescriptionsUseCase(
    private val remoteRepository: PrescriptionRepository,
    private val localRepository: LocalDataRepository
) {
    suspend fun fetchAndSaveRemotePrescriptions(): Result<List<PrescriptionResponse>> {
        val result = remoteRepository.getUserPrescriptions()

        if (result.isSuccess) {
            val prescriptions = result.getOrNull() ?: emptyList()
            localRepository.savePrescriptions(prescriptions)
        }

        return result
    }

    fun getLocalPrescriptions(): Flow<List<PrescriptionEntity>> {
        return localRepository.getAllPrescriptions()
    }

    suspend operator fun invoke(): Result<List<PrescriptionResponse>> {
        val remoteResult = fetchAndSaveRemotePrescriptions()

        if (remoteResult.isSuccess) {
            return remoteResult
        }

        val localPrescriptions = localRepository.getAllPrescriptions().first()

        if (localPrescriptions.isNotEmpty()) {
            val prescriptions = localPrescriptions.map { entity ->
                PrescriptionResponse(
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
            return Result.success(prescriptions)
        }

        return remoteResult
    }
}