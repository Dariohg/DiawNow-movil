package com.example.diagnow.core.foreground.alarms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.diagnow.DiagNowApplication
import com.example.diagnow.MainActivity
import com.example.diagnow.R
import com.example.diagnow.core.database.entity.TreatmentStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class MedicationAlarmReceiver : BroadcastReceiver() {

    private val TAG = "MedicationAlarmReceiver"

    companion object {
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_MEDICATION_DOSAGE = "medication_dosage"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarma recibida para la medicación. Action: ${intent.action}")

        val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID)
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Medicación"
        val medicationDosage = intent.getStringExtra(EXTRA_MEDICATION_DOSAGE) ?: "Dosis indicada"

        if (medicationId == null) {
            Log.e(TAG, "Error: No se recibió medicationId en el Intent de la alarma.")
            return
        }

        Log.i(TAG, "Procesando alarma para Medication ID: $medicationId, Nombre: $medicationName")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as DiagNowApplication
                val medicationDao = application.database.medicationDao()
                val medication = medicationDao.getMedicationById(medicationId)

                if (medication == null) {
                    Log.w(TAG, "Medication ID $medicationId no encontrada en la BD. Cancelando futuras alarmas (si existieran).")
                    return@launch
                }

                val isTreatmentActive = medication.treatmentStatus == TreatmentStatus.ACTIVE
                val isWithinDuration = isTreatmentWithinDuration(medication.treatmentStartDate, medication.days)

                Log.d(TAG, "Verificación para $medicationId: Activo=$isTreatmentActive, DentroDuración=$isWithinDuration")

                if (isTreatmentActive && isWithinDuration) {
                    Log.i(TAG, "¡Tratamiento activo y dentro de duración para $medicationId! Mostrando notificación y reprogramando.")
                    sendMedicationNotification(context, medicationId, medicationName, medicationDosage)

                    val scheduler = MedicationAlarmScheduler(context)
                    scheduler.scheduleNextAlarm(medication)
                } else {
                    Log.i(TAG, "Tratamiento NO activo o fuera de duración para $medicationId. No se mostrará notificación ni se reprogramará.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando la alarma para $medicationId en background", e)
            } finally {
                pendingResult.finish()
                Log.d(TAG, "goAsync finalizado para $medicationId")
            }
        }
    }

    private fun isTreatmentWithinDuration(startDate: Date?, days: Int): Boolean {
        if (startDate == null || days <= 0) return false

        val startCalendar = Calendar.getInstance().apply { time = startDate }
        val endCalendar = Calendar.getInstance().apply {
            time = startDate
            add(Calendar.DAY_OF_YEAR, days)
        }
        val nowCalendar = Calendar.getInstance()

        return !nowCalendar.before(startCalendar) && nowCalendar.before(endCalendar)
    }

    private fun sendMedicationNotification(context: Context, medicationId: String, medicationName: String, dosage: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = medicationId.hashCode()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = DiagNowApplication.CHANNEL_ID
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 600)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_pill_24)
            .setContentTitle("¡Hora de tu medicación!")
            .setContentText("$medicationName - $dosage")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Recuerda tomar tu dosis de $medicationName ($dosage)."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(vibrationPattern)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(TAG, "Notificación mostrada para $medicationId con ID: $notificationId")

        vibrateDevice(context, vibrationPattern)
    }

    private fun vibrateDevice(context: Context, pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            Log.d(TAG, "Dispositivo vibrando con patrón.")
        } else {
            Log.w(TAG, "El dispositivo no soporta vibración.")
        }
    }
}