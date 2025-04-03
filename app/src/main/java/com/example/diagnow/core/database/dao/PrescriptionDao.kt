package com.example.diagnow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.diagnow.core.database.entity.PrescriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrescriptionDao {
    @Query("SELECT * FROM prescriptions ORDER BY lastUpdated DESC")
    fun getAllPrescriptions(): Flow<List<PrescriptionEntity>>

    @Query("SELECT * FROM prescriptions WHERE id = :prescriptionId")
    suspend fun getPrescriptionById(prescriptionId: String): PrescriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescription(prescription: PrescriptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPrescriptions(prescriptions: List<PrescriptionEntity>)

    @Update
    suspend fun updatePrescription(prescription: PrescriptionEntity)

    @Query("DELETE FROM prescriptions WHERE id = :prescriptionId")
    suspend fun deletePrescription(prescriptionId: String)
}