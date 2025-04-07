package com.example.diagnow.core.foreground.alarms

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.diagnow.core.database.entity.MedicationEntity
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class MedicationAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val TAG = "MedicationAlarmScheduler"

    // Programa la PRIMERA alarma cuando se inicia el tratamiento
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleFirstAlarm(medication: MedicationEntity) {
        val startDate = medication.treatmentStartDate ?: run {
            Log.e(TAG, "No se puede programar alarma sin fecha de inicio para ${medication.id}")
            return
        }
        if (medication.frequency <= 0) {
            Log.e(
                TAG,
                "No se puede programar alarma con frecuencia inválida (${medication.frequency}) para ${medication.id}"
            )
            return
        }

        val firstAlarmTimeMillis = calculateNextAlarmTime(startDate, medication.frequency)
        val endTimeMillis = calculateEndTime(startDate, medication.days)

        Log.d(TAG, "Programando PRIMERA alarma para ${medication.id} (${medication.name})")
        Log.d(TAG, "  -> Hora Primera Alarma: ${Date(firstAlarmTimeMillis)}")
        Log.d(TAG, "  -> Hora Fin Tratamiento: ${Date(endTimeMillis)}")

        // Solo programar si la primera alarma es ANTES de la fecha de fin
        if (firstAlarmTimeMillis < endTimeMillis) {
            scheduleExactAlarm(medication, firstAlarmTimeMillis, endTimeMillis)
        } else {
            Log.w(
                TAG,
                "La primera alarma calculada (${Date(firstAlarmTimeMillis)}) es después del fin del tratamiento (${
                    Date(endTimeMillis)
                }). No se programará."
            )
        }
    }

    // Programa la SIGUIENTE alarma (llamado desde el Receiver)
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleNextAlarm(medication: MedicationEntity) {
        val startDate =
            medication.treatmentStartDate ?: return // Ya no debería pasar si llegó al receiver
        if (medication.frequency <= 0) return

        // Calcula la siguiente alarma basándose en la HORA ACTUAL
        val nowMillis = System.currentTimeMillis()
        val nextAlarmTimeMillis = calculateNextAlarmTime(
            Date(nowMillis),
            medication.frequency
        ) // Calcula desde AHORA + frecuencia
        val endTimeMillis = calculateEndTime(startDate, medication.days)

        Log.d(TAG, "Reprogramando SIGUIENTE alarma para ${medication.id} (${medication.name})")
        Log.d(TAG, "  -> Hora Siguiente Alarma: ${Date(nextAlarmTimeMillis)}")
        Log.d(TAG, "  -> Hora Fin Tratamiento: ${Date(endTimeMillis)}")

        // Solo reprogramar si la siguiente alarma es ANTES de la fecha de fin
        if (nextAlarmTimeMillis < endTimeMillis) {
            scheduleExactAlarm(medication, nextAlarmTimeMillis, endTimeMillis)
        } else {
            Log.w(
                TAG,
                "La siguiente alarma calculada (${Date(nextAlarmTimeMillis)}) es después del fin del tratamiento (${
                    Date(endTimeMillis)
                }). No se reprogramará."
            )
        }
    }

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    private fun scheduleExactAlarm(
        medication: MedicationEntity,
        alarmTimeMillis: Long,
        endTimeMillis: Long
    ) {
        val pendingIntent = createPendingIntent(medication)

        // Verificar permiso para alarmas exactas (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(
                    TAG,
                    "Permiso SCHEDULE_EXACT_ALARM no concedido. No se puede programar alarma exacta para ${medication.id}."
                )
                // Aquí podrías notificar al usuario o guiarlo a la configuración.
                // Por ahora, solo logueamos y no programamos.
                // requestExactAlarmPermission(context) // Podrías llamar a una función para pedir permiso
                return
            }
        }

        try {
            // Usar setExactAndAllowWhileIdle para precisión incluso en Doze mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, // Usa reloj en tiempo real y despierta el dispositivo
                    alarmTimeMillis,
                    pendingIntent
                )
            } else {
                // Versiones anteriores
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeMillis,
                    pendingIntent
                )
            }
            Log.i(
                TAG,
                "Alarma exacta programada para ${medication.id} a las ${Date(alarmTimeMillis)}"
            )
        } catch (se: SecurityException) {
            Log.e(
                TAG,
                "SecurityException al programar alarma exacta para ${medication.id}. ¿Falta permiso USE_EXACT_ALARM en Android 14+?",
                se
            )
            // Esto puede pasar en Android 14 si USE_EXACT_ALARM no está declarado o concedido
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al programar alarma exacta para ${medication.id}", e)
        }
    }


    fun cancelAlarm(medicationId: String) {
        if (medicationId.isBlank()) {
            Log.w(TAG, "Intento de cancelar alarma con ID vacío.")
            return
        }
        Log.i(TAG, "Cancelando alarma para Medication ID: $medicationId")
        val pendingIntent =
            createPendingIntentForCancellation(medicationId) // Usa la misma forma de crear el PI
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel() // También cancela el PendingIntent en sí
    }

    // --- Funciones de Cálculo ---

    // Calcula la hora de la PRÓXIMA alarma sumando la frecuencia a la 'baseTime'
    private fun calculateNextAlarmTime(baseTime: Date, frequencyHours: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.time = baseTime
        calendar.add(Calendar.HOUR_OF_DAY, frequencyHours)
        // Opcional: redondear a la hora o minuto si se desea
        // calendar.set(Calendar.MINUTE, 0)
        // calendar.set(Calendar.SECOND, 0)
        // calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // Calcula la hora de finalización sumando los días a la 'startDate'
    private fun calculateEndTime(startDate: Date, days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        calendar.add(Calendar.DAY_OF_YEAR, days)
        // Llevar al inicio del día siguiente para la comparación (< endTimeMillis)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }


    // --- Creación de PendingIntent ---

    // Crea el PendingIntent que se disparará cuando suene la alarma
    private fun createPendingIntent(medication: MedicationEntity): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            // Usar URI de datos para identificar unívocamente la alarma por medicamento
            data = Uri.parse("diagnow://medication/alarm/${medication.id}")
            action = "com.example.diagnow.MEDICATION_ALARM" // Acción genérica
            // Pasar datos necesarios al Receiver
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID, medication.id)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, medication.name)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_DOSAGE, medication.dosage)
        }

        // Request code único basado en el ID del medicamento (convertido a Int de forma segura)
        val requestCode = medication.id.hashCode()

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            // FLAG_UPDATE_CURRENT: si ya existe un PI con mismo request code/intent filter, actualiza sus extras
            // FLAG_IMMUTABLE: Requerido para seguridad en Android 12+
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Crea un PendingIntent IDÉNTICO al que se usó para programar, para poder cancelarlo
    private fun createPendingIntentForCancellation(medicationId: String): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            data = Uri.parse("diagnow://medication/alarm/$medicationId")
            action = "com.example.diagnow.MEDICATION_ALARM"
            // No necesitamos extras para cancelar, solo identificar el PI
        }
        val requestCode = medicationId.hashCode()
        // Usar FLAG_NO_CREATE para obtener el PI existente sin crearlo si no existe
        // y FLAG_IMMUTABLE.
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?:
        // Fallback por si NO_CREATE da problemas (aunque debería funcionar)
        // Si NO_CREATE falla, creamos uno igual al original para que cancel() lo encuentre.
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE // Solo immutable si no necesitamos crearlo
        )
    }

    // Opcional: Función para guiar al usuario a la configuración de alarmas exactas
    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            }.also {
                // Asegúrate de llamar a startActivity desde un Context de Activity si es posible,
                // o muestra una notificación guiando al usuario.
                // Llamar desde un context general puede no funcionar bien.
                // context.startActivity(it) <-- CUIDADO al llamar desde aquí
                Log.w(
                    TAG,
                    "Se necesita permiso SCHEDULE_EXACT_ALARM. Guía al usuario a Configuración -> Apps -> Permisos especiales -> Alarmas y recordatorios."
                )
                // Mostrar un Toast o notificación sería mejor aquí
            }
        }
    }
}