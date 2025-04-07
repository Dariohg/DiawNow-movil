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
import com.example.diagnow.MainActivity // Para abrir la app al tocar la notif
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
        // Podríamos pasar frequency/days/startDate también si fuera necesario
        // recalcular aquí, pero por simplicidad, lo haremos en el scheduler.
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

        // Acceder a la base de datos de forma asíncrona
        // Usar goAsync para mantener el Receiver vivo mientras trabajamos en segundo plano
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as DiagNowApplication
                val medicationDao = application.database.medicationDao()
                val medication = medicationDao.getMedicationById(medicationId)

                if (medication == null) {
                    Log.w(TAG, "Medication ID $medicationId no encontrada en la BD. Cancelando futuras alarmas (si existieran).")
                    // Podríamos intentar cancelar explícitamente, pero si no existe, no debería haber alarma activa.
                    return@launch // Salir de la corutina
                }

                // --- Verificar si el tratamiento sigue activo y dentro del plazo ---
                val isTreatmentActive = medication.treatmentStatus == TreatmentStatus.ACTIVE
                val isWithinDuration = isTreatmentWithinDuration(medication.treatmentStartDate, medication.days)

                Log.d(TAG, "Verificación para $medicationId: Activo=$isTreatmentActive, DentroDuración=$isWithinDuration")

                if (isTreatmentActive && isWithinDuration) {
                    Log.i(TAG, "¡Tratamiento activo y dentro de duración para $medicationId! Mostrando notificación y reprogramando.")
                    // 1. Mostrar Notificación
                    sendMedicationNotification(context, medicationId, medicationName, medicationDosage)

                    // 2. Reprogramar la *siguiente* alarma
                    val scheduler = MedicationAlarmScheduler(context)
                    // Pasamos la entidad completa para que el scheduler tenga toda la info
                    scheduler.scheduleNextAlarm(medication)
                } else {
                    Log.i(TAG, "Tratamiento NO activo o fuera de duración para $medicationId. No se mostrará notificación ni se reprogramará.")
                    // No hacemos nada, la cadena de alarmas se detiene aquí.
                    // Si el estado cambió a COMPLETED, la cancelación ya debería haberse hecho.
                    // Si se borró, no se encontrará. Si pasaron los días, isWithinDuration será false.
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando la alarma para $medicationId en background", e)
            } finally {
                // IMPORTANTE: Finalizar goAsync cuando el trabajo asíncrono termina
                pendingResult.finish()
                Log.d(TAG, "goAsync finalizado para $medicationId")
            }
        }
    }

    private fun isTreatmentWithinDuration(startDate: Date?, days: Int): Boolean {
        if (startDate == null || days <= 0) return false // No se puede calcular sin fecha de inicio o duración

        val startCalendar = Calendar.getInstance().apply { time = startDate }
        val endCalendar = Calendar.getInstance().apply {
            time = startDate
            add(Calendar.DAY_OF_YEAR, days)
            // Considerar llevar al final del día si es necesario ser más preciso
            // set(Calendar.HOUR_OF_DAY, 23)
            // set(Calendar.MINUTE, 59)
            // set(Calendar.SECOND, 59)
        }
        val nowCalendar = Calendar.getInstance()

        // El tratamiento es válido si la hora actual está DESPUÉS o IGUAL al inicio
        // Y ANTES del final (el día 'days+1' a las 00:00 ya no cuenta)
        return !nowCalendar.before(startCalendar) && nowCalendar.before(endCalendar)
    }

    private fun sendMedicationNotification(context: Context, medicationId: String, medicationName: String, dosage: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = medicationId.hashCode() // Usar hashCode del ID como ID de notificación (o convertir a Int si es seguro)

        // Intent para abrir MainActivity (o una pantalla específica si se desea)
        val intent = Intent(context, MainActivity::class.java).apply {
            // Podrías añadir extras para navegar a la pantalla de detalle si es necesario
            // putExtra("NAVIGATE_TO", "prescription_detail")
            // putExtra("PRESCRIPTION_ID", ???) // Necesitaríamos pasar el prescriptionId también
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // O FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId, // Usar un request code único por notificación
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = DiagNowApplication.CHANNEL_ID // Reutilizar canal existente
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) // Usar sonido de alarma
        val vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 600) // Patrón más insistente

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_pill_24) // TODO: Añadir un icono de píldora a drawable
            .setContentTitle("¡Hora de tu medicación!")
            .setContentText("$medicationName - $dosage")
            .setStyle(NotificationCompat.BigTextStyle() // Para texto más largo si es necesario
                .bigText("Recuerda tomar tu dosis de $medicationName ($dosage)."))
            .setPriority(NotificationCompat.PRIORITY_MAX) // Máxima prioridad para alarmas
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Categoría de alarma
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Mostrar en pantalla de bloqueo
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(vibrationPattern)
            .setContentIntent(pendingIntent) // Acción al tocar
        // Podríamos añadir acciones como "Posponer" o "Tomada" si se desea
        // .addAction(R.drawable.ic_snooze, "Posponer 5 min", snoozePendingIntent)
        // .addAction(R.drawable.ic_check, "Ya la tomé", takenPendingIntent)

        // Mostrar notificación
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(TAG, "Notificación mostrada para $medicationId con ID: $notificationId")

        // Vibrar (redundante si la notificación ya lo hace, pero asegura la vibración)
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
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) // -1 = no repetir
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