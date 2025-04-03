package com.example.diagnow.core.database.repository

import com.example.diagnow.core.database.dao.PrescriptionDao
import com.example.diagnow.core.database.dao.MedicationDao
import com.example.diagnow.core.database.entity.PrescriptionEntity
import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.home.data.model.MedicationDetailResponse
import com.example.diagnow.home.data.model.PrescriptionResponse
import kotlinx.coroutines.flow.Flow
import java.util.Date

class LocalDataRepository(
    private val prescriptionDao: PrescriptionDao,
    private val medicationDao: MedicationDao
) {
    // Prescripciones
    fun getAllPrescriptions(): Flow<List<PrescriptionEntity>> {
        return prescriptionDao.getAllPrescriptions()
    }

    suspend fun getPrescriptionById(prescriptionId: String): PrescriptionEntity? {
        return prescriptionDao.getPrescriptionById(prescriptionId)
    }

    suspend fun savePrescription(prescription: PrescriptionResponse) {
        val prescriptionEntity = PrescriptionEntity(
            id = prescription.id,
            patientId = prescription.patientId,
            doctorName = prescription.doctorName,
            date = prescription.date,
            diagnosis = prescription.diagnosis,
            status = prescription.status,
            notes = prescription.notes,
            createdAt = prescription.createdAt
        )
        prescriptionDao.insertPrescription(prescriptionEntity)
    }

    suspend fun savePrescriptions(prescriptions: List<PrescriptionResponse>) {
        val entities = prescriptions.map { prescription ->
            PrescriptionEntity(
                id = prescription.id,
                patientId = prescription.patientId,
                doctorName = prescription.doctorName,
                date = prescription.date,
                diagnosis = prescription.diagnosis,
                status = prescription.status,
                notes = prescription.notes,
                createdAt = prescription.createdAt
            )
        }
        prescriptionDao.insertAllPrescriptions(entities)
    }

    // Medicamentos
    fun getMedicationsByPrescriptionId(prescriptionId: String): Flow<List<MedicationEntity>> {
        return medicationDao.getMedicationsByPrescriptionId(prescriptionId)
    }

    suspend fun saveMedications(medications: List<MedicationDetailResponse>, prescriptionId: String) {
        val entities = medications.map { medication ->
            MedicationEntity(
                id = medication.id,
                prescriptionId = prescriptionId,
                name = medication.name,
                dosage = medication.dosage,
                frequency = medication.frequency,
                days = medication.days,
                administrationRoute = medication.administrationRoute,
                instructions = medication.instructions,
                createdAt = medication.createdAt
            )
        }
        medicationDao.insertAllMedications(entities)
    }

    suspend fun deletePrescriptionWithMedications(prescriptionId: String) {
        medicationDao.deleteMedicationsByPrescriptionId(prescriptionId)
        prescriptionDao.deletePrescription(prescriptionId)
    }
}