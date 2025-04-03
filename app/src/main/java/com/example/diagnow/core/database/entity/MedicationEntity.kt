package com.example.diagnow.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.diagnow.core.database.converters.DateConverter
import java.util.Date

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = PrescriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["prescriptionId"],
            onDelete = ForeignKey.CASCADE
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
    val lastUpdated: Long = System.currentTimeMillis()
)