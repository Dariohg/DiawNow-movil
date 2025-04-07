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

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleNextAlarm(medication: MedicationEntity) {
        val startDate =
            medication.treatmentStartDate ?: return
        if (medication.frequency <= 0) return

        val nowMillis = System.currentTimeMillis()
        val nextAlarmTimeMillis = calculateNextAlarmTime(
            Date(nowMillis),
            medication.frequency
        )
        val endTimeMillis = calculateEndTime(startDate, medication.days)

        Log.d(TAG, "Reprogramando SIGUIENTE alarma para ${medication.id} (${medication.name})")
        Log.d(TAG, "  -> Hora Siguiente Alarma: ${Date(nextAlarmTimeMillis)}")
        Log.d(TAG, "  -> Hora Fin Tratamiento: ${Date(endTimeMillis)}")

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e(
                    TAG,
                    "Permiso SCHEDULE_EXACT_ALARM no concedido. No se puede programar alarma exacta para ${medication.id}."
                )
                return
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeMillis,
                    pendingIntent
                )
            } else {
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
            createPendingIntentForCancellation(medicationId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }


    private fun calculateNextAlarmTime(baseTime: Date, frequencyHours: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.time = baseTime
        calendar.add(Calendar.HOUR_OF_DAY, frequencyHours)
        return calendar.timeInMillis
    }

    private fun calculateEndTime(startDate: Date, days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        calendar.add(Calendar.DAY_OF_YEAR, days)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }



    private fun createPendingIntent(medication: MedicationEntity): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            data = Uri.parse("diagnow://medication/alarm/${medication.id}")
            action = "com.example.diagnow.MEDICATION_ALARM"
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_ID, medication.id)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_NAME, medication.name)
            putExtra(MedicationAlarmReceiver.EXTRA_MEDICATION_DOSAGE, medication.dosage)
        }

        val requestCode = medication.id.hashCode()

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createPendingIntentForCancellation(medicationId: String): PendingIntent {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            data = Uri.parse("diagnow://medication/alarm/$medicationId")
            action = "com.example.diagnow.MEDICATION_ALARM"
        }
        val requestCode = medicationId.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?:
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            }.also {
                Log.w(
                    TAG,
                    "Se necesita permiso SCHEDULE_EXACT_ALARM. Guía al usuario a Configuración -> Apps -> Permisos especiales -> Alarmas y recordatorios."
                )
            }
        }
    }
}