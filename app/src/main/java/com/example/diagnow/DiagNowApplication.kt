package com.example.diagnow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class DiagNowApplication : Application() {

    companion object {
        const val CHANNEL_ID = "diagnow_notifications"
    }

    override fun onCreate() {
        super.onCreate()

        // Crear canal de notificaciones para Android 8.0+
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