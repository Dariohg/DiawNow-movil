package com.example.diagnow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.diagnow.core.database.converters.DateConverter
import java.util.Date

@Entity(tableName = "prescriptions")
@TypeConverters(DateConverter::class)
data class PrescriptionEntity(
    @PrimaryKey
    val id: String,
    val patientId: String,
    val doctorName: String?,
    val date: Date?,
    val diagnosis: String,
    val status: String?,
    val notes: String?,
    val createdAt: String?,
    val isSynchronized: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)