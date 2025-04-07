package com.example.diagnow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.core.database.entity.TreatmentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    // --- ÚNICA VERSIÓN PARA OBTENER POR PRESCRIPTION ID (DEVUELVE FLOW) ---
    @Query("SELECT * FROM medications WHERE prescriptionId = :prescriptionId")
    fun getMedicationsByPrescriptionIdFlow(prescriptionId: String): Flow<List<MedicationEntity>> // <-- NOMBRE ESTÁNDAR

    // --- Obtener por ID (Suspend) ---
    @Query("SELECT * FROM medications WHERE id = :medicationId")
    suspend fun getMedicationById(medicationId: String): MedicationEntity?

    // --- Insertar / Actualizar ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: MedicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMedications(medications: List<MedicationEntity>)

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    // --- Borrar ---
    @Query("DELETE FROM medications WHERE prescriptionId = :prescriptionId")
    suspend fun deleteMedicationsByPrescriptionId(prescriptionId: String)

    @Query("DELETE FROM medications")
    suspend fun clearMedications()

    @Query("DELETE FROM medications WHERE id = :medicationId")
    suspend fun deleteMedicationById(medicationId: String): Int

    // --- Query para Alarmas / Boot (Sigue siendo suspend) ---
    @Query("SELECT * FROM medications WHERE treatmentStatus = :status")
    suspend fun getAllMedicationsByStatus(status: String = TreatmentStatus.ACTIVE): List<MedicationEntity>
}