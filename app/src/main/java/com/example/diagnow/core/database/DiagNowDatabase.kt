package com.example.diagnow.core.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.diagnow.core.database.converters.DateConverter
import com.example.diagnow.core.database.dao.MedicationDao
import com.example.diagnow.core.database.dao.PrescriptionDao
import com.example.diagnow.core.database.entity.MedicationEntity
import com.example.diagnow.core.database.entity.PrescriptionEntity

@Database(
    entities = [PrescriptionEntity::class, MedicationEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class DiagNowDatabase : RoomDatabase() {

    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun medicationDao(): MedicationDao

    companion object {
        private const val DATABASE_NAME = "diagnow_db"

        @Volatile
        private var INSTANCE: DiagNowDatabase? = null

        fun getInstance(context: Context): DiagNowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DiagNowDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun clearAllData() {
        Log.w("DiagNowDatabase", "Clearing all data from database...")
        prescriptionDao().clearPrescriptions()
        medicationDao().clearMedications()
        Log.i("DiagNowDatabase", "Database cleared.")
    }
}