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
    // Obtener prescripciones remotas y guardarlas localmente
    suspend fun fetchAndSaveRemotePrescriptions(): Result<List<PrescriptionResponse>> {
        val result = remoteRepository.getUserPrescriptions()

        if (result.isSuccess) {
            val prescriptions = result.getOrNull() ?: emptyList()
            localRepository.savePrescriptions(prescriptions)
        }

        return result
    }

    // Obtener prescripciones locales como Flow para observar cambios
    fun getLocalPrescriptions(): Flow<List<PrescriptionEntity>> {
        return localRepository.getAllPrescriptions()
    }

    // Método principal que intenta obtener datos remotos pero usa locales si fallan
    suspend operator fun invoke(): Result<List<PrescriptionResponse>> {
        val remoteResult = fetchAndSaveRemotePrescriptions()

        // Si obtenemos datos remotos, los retornamos
        if (remoteResult.isSuccess) {
            return remoteResult
        }

        // Si no, intentamos usar datos locales y construir objetos Response
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
                    medications = emptyList(), // No incluimos medicamentos aquí
                    notes = entity.notes,
                    createdAt = entity.createdAt
                )
            }
            return Result.success(prescriptions)
        }

        // Si no hay datos ni locales ni remotos, retornamos el error original
        return remoteResult
    }
}