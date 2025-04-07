package com.example.diagnow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.diagnow.core.database.DiagNowDatabase
import androidx.lifecycle.ViewModelProvider
import com.example.diagnow.core.database.dao.MedicationDao
import com.example.diagnow.core.database.dao.PrescriptionDao
import com.example.diagnow.core.database.repository.LocalDataRepository


class DiagNowApplication : Application() {

    val database: DiagNowDatabase by lazy { DiagNowDatabase.getInstance(this) }
    private val prescriptionDao: PrescriptionDao by lazy { database.prescriptionDao() }
    private val medicationDao: MedicationDao by lazy { database.medicationDao() }
    val localDataRepository: LocalDataRepository by lazy {
        LocalDataRepository(database, prescriptionDao, medicationDao)
    }

    companion object {
        const val CHANNEL_ID = "diagnow_notifications"
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "DiagNow Notificaciones"
            val descriptionText = "Canal para notificaciones de DiagNow"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}