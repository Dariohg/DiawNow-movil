package com.example.diagnow.core.fcm

import android.app.NotificationManager
import android.app.PendingIntent
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
import com.example.diagnow.core.database.repository.LocalDataRepository
import com.example.diagnow.core.fcm.data.DeviceTokenRepository
import com.example.diagnow.core.fcm.domain.RegisterDeviceTokenUseCase
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.repository.PrescriptionRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "MyFirebaseMsgService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM generado: ${token.take(10)}...")
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Mensaje FCM recibido desde: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Payload de Datos: ${remoteMessage.data}")

            when (remoteMessage.data["type"]) {
                "NEW_PRESCRIPTION" -> {
                    val prescriptionId = remoteMessage.data["prescriptionId"]
                    Log.i(TAG, "Notificación de Nueva Receta recibida, ID: $prescriptionId")

                    // Guardar la receta localmente
                    if (prescriptionId != null) {
                        savePrescriptionLocally(prescriptionId)
                    }
                }
                else -> Log.d(TAG, "Mensaje de datos de tipo desconocido recibido.")
            }
        }

        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Payload de Notificación: Title='${notification.title}', Body='${notification.body}'")
            sendNotification(notification.title, notification.body)
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        // Intent para abrir la app al hacer clic en la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = DiagNowApplication.CHANNEL_ID
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title ?: getString(R.string.app_name))
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500)) // Patrón de vibración: 500ms ON, 200ms OFF, 500ms ON

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = Random.nextInt()

        // Hacer vibrar el dispositivo
        vibrateDevice()

        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(TAG, "Mostrando notificación ID: $notificationId")
    }

    private fun vibrateDevice() {
        try {
            // Obtener el servicio de vibración
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Vibración con efecto predefinido para Android 8.0+
                    vibrator.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500), -1))
                } else {
                    // Método tradicional para versiones anteriores
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
                }
            } else {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al intentar vibrar: ${e.message}")
        }
    }

    private fun savePrescriptionLocally(prescriptionId: String) {
        val context = applicationContext
        val sessionManager = SessionManager(context)

        if (sessionManager.isLoggedIn()) {
            val database = (application as DiagNowApplication).database
            val prescriptionDao = database.prescriptionDao()
            val medicationDao = database.medicationDao()
            val localRepository = LocalDataRepository(prescriptionDao, medicationDao)

            val retrofitHelper = RetrofitHelper(sessionManager)
            val remoteRepository = PrescriptionRepository(retrofitHelper, sessionManager)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Obtener detalles de la receta
                    val prescriptionResult = remoteRepository.getPrescriptionById(prescriptionId)

                    if (prescriptionResult.isSuccess) {
                        prescriptionResult.getOrNull()?.let { prescription ->
                            // Guardar la receta localmente
                            localRepository.savePrescription(prescription)

                            // Obtener y guardar los medicamentos
                            val medicationsResult = remoteRepository.getPrescriptionMedications(prescriptionId)

                            if (medicationsResult.isSuccess) {
                                medicationsResult.getOrNull()?.let { detailResponse ->
                                    localRepository.saveMedications(
                                        detailResponse.data.medications,
                                        prescriptionId
                                    )
                                }
                                Log.i(TAG, "Medicamentos guardados localmente para receta ID: $prescriptionId")
                            }
                        }
                        Log.i(TAG, "Receta guardada localmente ID: $prescriptionId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al guardar la receta localmente: ${e.message}")
                }
            }
        }
    }

    private fun sendRegistrationToServer(token: String?) {
        if (token == null) {
            Log.w(TAG, "Token nulo recibido en onNewToken, no se puede enviar.")
            return
        }

        val context = applicationContext
        val sessionManager = SessionManager(context)

        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Usuario logueado, intentando enviar token $token al servidor...")
            val retrofitHelper = RetrofitHelper(sessionManager)
            val repository = DeviceTokenRepository(retrofitHelper, sessionManager)
            val useCase = RegisterDeviceTokenUseCase(repository)

            CoroutineScope(Dispatchers.IO).launch {
                val result = useCase(token)
                result.fold(
                    onSuccess = { Log.i(TAG, "Token FCM enviado exitosamente al servidor desde onNewToken.") },
                    onFailure = { e -> Log.e(TAG, "Error enviando token FCM al servidor desde onNewToken.", e) }
                )
            }
        } else {
            Log.w(TAG, "Usuario no logueado. El token $token NO se envió. Se enviará tras el próximo login.")
        }
    }
}