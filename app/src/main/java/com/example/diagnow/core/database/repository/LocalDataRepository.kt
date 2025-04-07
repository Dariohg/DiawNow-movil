package com.example.diagnow.core.database.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.diagnow.core.database.DiagNowDatabase
import com.example.diagnow.core.database.dao.PrescriptionDao
import com.example.diagnow.core.database.dao.MedicationDao
import com.example.diagnow.core.database.entity.PrescriptionEntity
import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.core.database.entity.TreatmentStatus
import com.example.diagnow.home.data.model.MedicationDetailResponse
import com.example.diagnow.home.data.model.PrescriptionResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.NoSuchElementException

class LocalDataRepository(
    private val database: DiagNowDatabase,
    private val prescriptionDao: PrescriptionDao,
    private val medicationDao: MedicationDao
) {

    // --- Prescripciones ---
    fun getAllPrescriptions(): Flow<List<PrescriptionEntity>> { return prescriptionDao.getAllPrescriptions() }
    suspend fun getPrescriptionById(prescriptionId: String): PrescriptionEntity? { return prescriptionDao.getPrescriptionById(prescriptionId) }
    suspend fun savePrescription(prescription: PrescriptionResponse) {
        val prescriptionEntity = PrescriptionEntity( id = prescription.id, patientId = prescription.patientId, doctorName = prescription.doctorName, date = prescription.date, diagnosis = prescription.diagnosis, status = prescription.status, notes = prescription.notes, createdAt = prescription.createdAt )
        prescriptionDao.insertPrescription(prescriptionEntity)
    }
    suspend fun savePrescriptions(prescriptions: List<PrescriptionResponse>) {
        if (prescriptions.isEmpty()) return
        val entities = prescriptions.map { p -> PrescriptionEntity( id = p.id, patientId = p.patientId, doctorName = p.doctorName, date = p.date, diagnosis = p.diagnosis, status = p.status, notes = p.notes, createdAt = p.createdAt ) }
        prescriptionDao.insertAllPrescriptions(entities)
    }
    suspend fun deletePrescriptionWithMedications(prescriptionId: String) {
        Log.w("LocalDataRepository", "Deleting prescription and medications for ID: $prescriptionId")
        database.withTransaction {
            medicationDao.deleteMedicationsByPrescriptionId(prescriptionId)
            prescriptionDao.deletePrescription(prescriptionId)
        }
    }

    // --- Medicamentos ---

//    fun getLocalMedicationsFlow(prescriptionId: String): Flow<List<MedicationEntity>> {
//        return medicationDao.getMedicationsByPrescriptionIdFlow(prescriptionId) // Usa Flow DAO
//    }

    suspend fun getLocalMedicationsList(prescriptionId: String): List<MedicationEntity> {
        Log.d("LocalDataRepository", ">>> ENTERING getLocalMedicationsList for prescription: $prescriptionId")
        val result = try {
            delay(150)
            medicationDao.getMedicationsByPrescriptionIdList(prescriptionId) // Usa Suspend DAO
        } catch (e: Exception){
            Log.e("LocalDataRepository", ">>> ERROR in getLocalMedicationsList calling DAO", e)
            emptyList<MedicationEntity>()
        }
        Log.d("LocalDataRepository", "<<< EXITING getLocalMedicationsList for prescription: $prescriptionId - Count: ${result.size}")
        return result
    }

    suspend fun getMedicationById(medicationId: String): MedicationEntity? {
        return medicationDao.getMedicationById(medicationId)
    }

    /**
     * Sincroniza: Guarda solo nuevos remotos, ignora existentes, borra obsoletos locales.
     */
    suspend fun saveMedications(remoteMedications: List<MedicationDetailResponse>, prescriptionId: String) {
        Log.d("LocalDataRepository", "[IGNORE-SAFE] Syncing meds for prescription $prescriptionId. Remote count: ${remoteMedications.size}")
        try {
            // 1. Obtener locales ANTES de la transacción
            val initialLocalMedsMap = try {
                delay(150)
                getLocalMedicationsList(prescriptionId).associateBy { it.id }
            } catch (e: Exception) {
                Log.e("LocalDataRepository", "[IGNORE-SAFE] Error fetching initial local meds for $prescriptionId", e)
                emptyMap<String, MedicationEntity>()
            }
            val localMedIds = initialLocalMedsMap.keys
            Log.d("LocalDataRepository", "[IGNORE-SAFE] Fetched ${initialLocalMedsMap.size} initial local meds BEFORE transaction. MAP: $initialLocalMedsMap")

            // 2. IDs remotos
            val remoteMedMap = remoteMedications.associateBy { it.id }
            val remoteMedIds = remoteMedMap.keys

            // 3. Diferencias
            val newMedIds = remoteMedIds - localMedIds
            val obsoleteMedIds = localMedIds - remoteMedIds
            val existingIgnoredIds = remoteMedIds intersect localMedIds

            Log.d("LocalDataRepository", "[IGNORE-SAFE] Sync details: New=${newMedIds.size}, Existing/Ignored=${existingIgnoredIds.size}, Obsolete=${obsoleteMedIds.size}")

            // 4. Transacción si hay cambios
            if ((newMedIds.isNotEmpty() || obsoleteMedIds.isNotEmpty()) && localMedIds.isEmpty()) {
                database.withTransaction {
                    // Borrar Obsoletos
//                    if (obsoleteMedIds.isNotEmpty()) {
//                        obsoleteMedIds.forEach { medId -> medicationDao.deleteMedicationById(medId) }
//                        Log.w("LocalDataRepository", "[IGNORE-SAFE-TX] Deleted ${obsoleteMedIds.size} obsolete medications.")
//                    }
                    // Insertar Nuevos
                    if (newMedIds.isNotEmpty()) {
                        val newEntities = newMedIds.mapNotNull { remoteMedMap[it] }
                            .map { remoteMed ->
                                MedicationEntity(
                                    id = remoteMed.id, prescriptionId = prescriptionId, name = remoteMed.name, dosage = remoteMed.dosage,
                                    frequency = remoteMed.frequency, days = remoteMed.days, administrationRoute = remoteMed.administrationRoute,
                                    instructions = remoteMed.instructions, createdAt = remoteMed.createdAt,
                                    treatmentStatus = TreatmentStatus.NOT_STARTED, treatmentStartDate = null,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            }
                        if (newEntities.isNotEmpty()) { medicationDao.insertAllMedications(newEntities) }
                        Log.i("LocalDataRepository", "[IGNORE-SAFE-TX] Inserted ${newEntities.size} new medications.")
                    }
                    Log.d("LocalDataRepository", "[IGNORE-SAFE-TX] Transaction finished.")
                }
            } else {
                Log.d("LocalDataRepository", "[IGNORE-SAFE] No changes needed. Skipping transaction.")
            }
        } catch (e: Exception) {
            Log.e("LocalDataRepository", "[IGNORE-SAFE] Error during medication sync transaction for prescription $prescriptionId", e)
            throw e
        }
    }

    suspend fun updateMedicationTreatmentStatus( medicationId: String, newStatus: String, startDate: Date? = null ): Result<Unit> {
        Log.d("LocalDataRepository", "Updating treatment status for $medicationId to $newStatus")
        return try {
            val medication = medicationDao.getMedicationById(medicationId)
            if (medication != null) {
                val updatedMedication = medication.copy(
                    treatmentStatus = newStatus,
                    treatmentStartDate = if (newStatus == TreatmentStatus.ACTIVE && startDate != null) startDate
                    else if (newStatus != TreatmentStatus.ACTIVE) medication.treatmentStartDate
                    else medication.treatmentStartDate,
                    lastUpdated = System.currentTimeMillis()
                )
                medicationDao.updateMedication(updatedMedication)
                Log.i("LocalDataRepository", "Medication status updated successfully for $medicationId to $newStatus")
                Result.success(Unit)
            } else {
                Log.w("LocalDataRepository", "Medication $medicationId not found for status update.")
                Result.failure(Exception("Medication with id $medicationId not found"))
            }
        } catch (e: Exception) {
            Log.e("LocalDataRepository", "Error updating medication status for $medicationId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteMedicationsByPrescriptionId(prescriptionId: String) {
        medicationDao.deleteMedicationsByPrescriptionId(prescriptionId)
    }

    suspend fun getAllActiveMedications(): List<MedicationEntity> {
        return medicationDao.getAllMedicationsByStatus(TreatmentStatus.ACTIVE)
    }
}