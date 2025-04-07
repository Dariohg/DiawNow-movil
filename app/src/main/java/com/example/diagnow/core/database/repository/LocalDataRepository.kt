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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Date
import java.util.NoSuchElementException

class LocalDataRepository(
    private val database: DiagNowDatabase,
    private val prescriptionDao: PrescriptionDao,
    private val medicationDao: MedicationDao
) {


    fun getAllPrescriptions(): Flow<List<PrescriptionEntity>> {
        return prescriptionDao.getAllPrescriptions()
    }

    suspend fun getPrescriptionById(prescriptionId: String): PrescriptionEntity? {
        return prescriptionDao.getPrescriptionById(prescriptionId)
    }

    suspend fun savePrescription(prescription: PrescriptionResponse) {
        val existing = prescriptionDao.getPrescriptionById(prescription.id)
        val entity = PrescriptionEntity(
            id = prescription.id,
            patientId = prescription.patientId,
            doctorName = prescription.doctorName,
            date = prescription.date,
            diagnosis = prescription.diagnosis,
            status = prescription.status,
            notes = prescription.notes,
            createdAt = prescription.createdAt,
            isSynchronized = existing?.isSynchronized ?: true,
            lastUpdated = System.currentTimeMillis()
        )
        prescriptionDao.insertPrescription(entity)
    }

    suspend fun savePrescriptions(prescriptions: List<PrescriptionResponse>) {
        Log.d("LocalDataRepository", "Upserting ${prescriptions.size} prescriptions individually.")
        if (prescriptions.isEmpty()) return
        prescriptions.forEach { savePrescription(it) }
        Log.i("LocalDataRepository", "Finished upserting prescriptions.")
    }

    suspend fun deletePrescriptionWithMedications(prescriptionId: String) {
        Log.w("LocalDataRepository", "Deleting prescription and medications for ID: $prescriptionId")
        database.withTransaction {
            medicationDao.deleteMedicationsByPrescriptionId(prescriptionId)
            prescriptionDao.deletePrescription(prescriptionId)
        }
    }


    fun getLocalMedicationsFlow(prescriptionId: String): Flow<List<MedicationEntity>> {
        return medicationDao.getMedicationsByPrescriptionIdFlow(prescriptionId)
    }


    suspend fun getMedicationById(medicationId: String): MedicationEntity? {
        return medicationDao.getMedicationById(medicationId)
    }

    suspend fun saveMedications(remoteMedications: List<MedicationDetailResponse>, prescriptionId: String) {
        Log.d("LocalDataRepository", "[IGNORE-FLOW] Syncing meds for prescription $prescriptionId. Remote count: ${remoteMedications.size}")

        try {
            database.withTransaction {
                Log.d("LocalDataRepository", "[IGNORE-FLOW-TX] Transaction started for $prescriptionId.")

                val localMedsMap: Map<String, MedicationEntity> = try {
                    medicationDao.getMedicationsByPrescriptionIdFlow(prescriptionId).first()
                        .associateBy { it.id }
                } catch (e: NoSuchElementException) {
                    Log.d("LocalDataRepository", "[IGNORE-FLOW-TX] No existing local meds (Flow empty) for $prescriptionId")
                    emptyMap()
                } catch (e: Exception) {
                    Log.e("LocalDataRepository", "[IGNORE-FLOW-TX] Error getting local meds in transaction", e)
                    emptyMap()
                }
                val localMedIds = localMedsMap.keys
                Log.d("LocalDataRepository", "[IGNORE-FLOW-TX] Fetched ${localMedsMap.size} local meds IN transaction. MAP: $localMedsMap")

                val remoteMedMap = remoteMedications.associateBy { it.id }
                val remoteMedIds = remoteMedMap.keys

                val newMedIds = remoteMedIds - localMedIds
                val obsoleteMedIds = localMedIds - remoteMedIds
                val existingIgnoredIds = remoteMedIds intersect localMedIds

                Log.d("LocalDataRepository", "[IGNORE-FLOW-TX] Sync details: New=${newMedIds.size}, Existing/Ignored=${existingIgnoredIds.size}, Obsolete=${obsoleteMedIds.size}")

                if (obsoleteMedIds.isNotEmpty()) {
                    Log.w("LocalDataRepository", "[IGNORE-FLOW-TX] Deleting ${obsoleteMedIds.size} obsolete medications.")
                    obsoleteMedIds.forEach { medId ->
                        val rowsDeleted = medicationDao.deleteMedicationById(medId)
                        if (rowsDeleted <= 0) Log.w("LocalDataRepository", "[IGNORE-FLOW-TX] Failed to delete obsolete med: $medId")
                    }
                }

                if (newMedIds.isNotEmpty()) {
                    Log.i("LocalDataRepository", "[IGNORE-FLOW-TX] Inserting ${newMedIds.size} new medications.")
                    val newEntities = newMedIds.mapNotNull { remoteMedMap[it] }
                        .map { remoteMed ->
                            MedicationEntity(
                                id = remoteMed.id,
                                prescriptionId = prescriptionId,
                                name = remoteMed.name,
                                dosage = remoteMed.dosage,
                                frequency = remoteMed.frequency,
                                days = remoteMed.days,
                                administrationRoute = remoteMed.administrationRoute,
                                instructions = remoteMed.instructions,
                                createdAt = remoteMed.createdAt,
                                treatmentStatus = TreatmentStatus.NOT_STARTED,
                                treatmentStartDate = null,
                                lastUpdated = System.currentTimeMillis()
                            )
                        }
                    if (newEntities.isNotEmpty()) {
                        medicationDao.insertAllMedications(newEntities)
                    }
                }

                if(existingIgnoredIds.isNotEmpty()){
                    Log.d("LocalDataRepository", "[IGNORE-FLOW-TX] Ignored update for existing med IDs: $existingIgnoredIds")
                }

                Log.d("LocalDataRepository", "[IGNORE-FLOW-TX] Transaction finished.")
            }
        } catch (e: Exception) {
            Log.e("LocalDataRepository", "[IGNORE-FLOW] Error during medication sync transaction for prescription $prescriptionId", e)
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