package com.example.diagnow.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.diagnow.core.database.converters.DateConverter
import java.util.Date

object TreatmentStatus {
    const val NOT_STARTED = "NOT_STARTED"
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
}

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("prescriptionId")]
)
@TypeConverters(DateConverter::class)
data class MedicationEntity(
    @PrimaryKey
    val id: String,
    val prescriptionId: String,
    val name: String,
    val dosage: String,
    val frequency: Int,
    val days: Int,
    val administrationRoute: String?,
    val instructions: String?,
    val createdAt: Date?,
    val lastUpdated: Long = System.currentTimeMillis(),
    val treatmentStatus: String = TreatmentStatus.NOT_STARTED,
    val treatmentStartDate: Date? = null
)