package com.example.diagnow.core.foreground.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.diagnow.DiagNowApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Dispositivo reiniciado. Reprogramando alarmas de medicación activas...")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val application = context.applicationContext as DiagNowApplication
                    val localRepository = application.localDataRepository
                    val alarmScheduler = MedicationAlarmScheduler(context)

                    val activeMedications = localRepository.getAllActiveMedications()

                    Log.d(TAG, "Se encontraron ${activeMedications.size} medicaciones activas para reprogramar.")

                    if (activeMedications.isNotEmpty()) {
                        activeMedications.forEach { medication ->
                            Log.d(TAG, "Reprogramando alarma para ${medication.id} (${medication.name})")
                            alarmScheduler.scheduleNextAlarm(medication)
                        }
                        Log.i(TAG, "Reprogramación de alarmas completada.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al reprogramar alarmas después del reinicio", e)
                } finally {
                    pendingResult.finish()
                    Log.d(TAG, "goAsync finalizado para BootCompletedReceiver.")
                }
            }
        } else {
            Log.w(TAG, "Recibido intent con acción inesperada: ${intent.action}")
        }
    }
}