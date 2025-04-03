package com.example.diagnow.home.domain

import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.home.data.model.MedicationDetailResponse
import com.example.diagnow.home.data.model.PrescriptionDetailData
import com.example.diagnow.home.data.model.PrescriptionDetailResponse
import com.example.diagnow.home.data.repository.PrescriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Date

class GetPrescriptionMedicationsUseCase(
    private val remoteRepository: PrescriptionRepository,
    private val localRepository: LocalDataRepository
) {
    // Obtener y guardar medicamentos remotos
    suspend fun fetchAndSaveRemoteMedications(prescriptionId: String): Result<PrescriptionDetailResponse> {
        val result = remoteRepository.getPrescriptionMedications(prescriptionId)

        if (result.isSuccess) {
            val response = result.getOrNull()
            if (response != null) {
                localRepository.saveMedications(response.data.medications, prescriptionId)
            }
        }

        return result
    }

    // Obtener medicamentos locales como Flow
    fun getLocalMedications(prescriptionId: String): Flow<List<MedicationEntity>> {
        return localRepository.getMedicationsByPrescriptionId(prescriptionId)
    }

    // Método principal que intenta datos remotos pero usa locales si fallan
    suspend operator fun invoke(prescriptionId: String): Result<PrescriptionDetailResponse> {
        val remoteResult = fetchAndSaveRemoteMedications(prescriptionId)

        // Si obtenemos datos remotos, los retornamos
        if (remoteResult.isSuccess) {
            return remoteResult
        }

        // Si no, intentamos usar datos locales y construir un objeto Response
        val localMedications = localRepository.getMedicationsByPrescriptionId(prescriptionId).first()

        if (localMedications.isNotEmpty()) {
            // Crear un objeto PrescriptionDetailResponse con datos locales
            val medicationResponses = localMedications.map { entity ->
                MedicationDetailResponse(
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

            val response = PrescriptionDetailResponse(
                status = "success",
                message = "Data loaded from local database",
                data = PrescriptionDetailData(
                    prescriptionCreatedAt = Date(), // Fecha actual como placeholder
                    medications = medicationResponses
                )
            )

            return Result.success(response)
        }

        // Si no hay datos ni locales ni remotos, retornamos el error original
        return remoteResult
    }
}