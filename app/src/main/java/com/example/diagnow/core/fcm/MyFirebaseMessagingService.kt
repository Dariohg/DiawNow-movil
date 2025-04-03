// --- CREA ESTE NUEVO ARCHIVO en diagnow/core/fcm/MyFirebaseMessagingService.kt ---
package com.example.diagnow.core.fcm // Asegúrate que el paquete sea este

import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.diagnow.DiagNowApplication // Importa tu clase Application
import com.example.diagnow.R // Necesitarás un icono en res/drawable
import com.example.diagnow.core.fcm.data.DeviceTokenRepository // Importa el Repo
import com.example.diagnow.core.fcm.domain.RegisterDeviceTokenUseCase // Importa el UseCase
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() { // <-- HEREDA de FirebaseMessagingService

    private val TAG = "MyFirebaseMsgService"

    /**
     * Se llama cuando Firebase genera un nuevo token FCM o actualiza uno existente.
     * Este es un punto CRÍTICO para enviar el token a tu backend.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM generado: ${token.take(10)}...")
        // Intentar enviar el token al servidor inmediatamente
        sendRegistrationToServer(token)
    }

    /**
     * Se llama cuando se recibe un mensaje mientras la app está en primer plano,
     * o cuando un mensaje de datos (o mixto) llega en segundo plano.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Mensaje FCM recibido desde: ${remoteMessage.from}")

        // Procesar payload de datos (si existe)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Payload de Datos: ${remoteMessage.data}")
            // Ejemplo: buscar tipo de notificación
            when (remoteMessage.data["type"]) {
                "NEW_PRESCRIPTION" -> {
                    val prescriptionId = remoteMessage.data["prescriptionId"]
                    Log.i(TAG, "Notificación de Nueva Receta recibida, ID: $prescriptionId")
                    // Aquí podrías decidir qué hacer con esta info (ej. guardar, notificar)
                }
                // Añadir otros tipos de mensajes de datos si los tienes
                else -> Log.d(TAG, "Mensaje de datos de tipo desconocido recibido.")
            }
        }

        // Procesar payload de notificación (si existe y la app está en primer plano)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Payload de Notificación: Title='${notification.title}', Body='${notification.body}'")
            // Mostrar la notificación al usuario si la app está en primer plano
            sendNotification(notification.title, notification.body)
        }
    }

    /**
     * Construye y muestra una notificación simple en la bandeja del sistema.
     * Deberías personalizar esto más (PendingIntent, etc.).
     */
    private fun sendNotification(title: String?, messageBody: String?) {
        // TODO: Crear un PendingIntent para abrir la app/pantalla correcta al hacer clic
        // val intent = Intent(this, MainActivity::class.java) // Por ejemplo
        // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)

        val channelId = DiagNowApplication.CHANNEL_ID // Usa el ID del canal creado en Application
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            // --- ¡USA TU PROPIO ICONO! ---
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Reemplaza con un icono adecuado (blanco y transparente idealmente)
            // -----------------------------
            .setContentTitle(title ?: getString(R.string.app_name)) // Usa R.string.app_name si el título es null
            .setContentText(messageBody)
            .setAutoCancel(true) // Cierra la notificación al tocarla
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // .setSound(defaultSoundUri) // Sonido por defecto
        // .setContentIntent(pendingIntent) // Asigna el PendingIntent aquí

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // ID único para poder mostrar múltiples notificaciones
        val notificationId = Random.nextInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(TAG, "Mostrando notificación ID: $notificationId")
    }

    /**
     * Intenta enviar el token FCM al backend.
     * IMPORTANTE: Esta función se ejecuta en un Service, fuera del ciclo de vida
     * normal de Activities/ViewModels. La gestión de dependencias aquí es más tricky
     * sin un framework como Hilt. Hacemos instanciación manual como fallback.
     */
    private fun sendRegistrationToServer(token: String?) {
        if (token == null) {
            Log.w(TAG, "Token nulo recibido en onNewToken, no se puede enviar.")
            return
        }

        // Instanciación manual (NO IDEAL, pero funciona sin DI)
        // Necesitamos el Context para SessionManager
        val context = applicationContext
        val sessionManager = SessionManager(context)

        // Solo intentar enviar si el usuario está logueado
        if (sessionManager.isLoggedIn()) {
            Log.d(TAG, "Usuario logueado, intentando enviar token $token al servidor...")
            val retrofitHelper = RetrofitHelper(sessionManager)
            val repository = DeviceTokenRepository(retrofitHelper, sessionManager)
            val useCase = RegisterDeviceTokenUseCase(repository)

            // Lanzar una coroutine para la operación de red
            CoroutineScope(Dispatchers.IO).launch {
                val result = useCase(token)
                result.fold(
                    onSuccess = { Log.i(TAG, "Token FCM enviado exitosamente al servidor desde onNewToken.") },
                    onFailure = { e -> Log.e(TAG, "Error enviando token FCM al servidor desde onNewToken.", e) }
                )
            }
        } else {
            // Usuario no logueado. ¿Qué hacer?
            // Opción 1: No hacer nada. El token se enviará después del login. (Implementado ahora)
            // Opción 2: Guardar el token en SharedPreferences para enviarlo específicamente después del login.
            Log.w(TAG, "Usuario no logueado. El token $token NO se envió. Se enviará tras el próximo login.")
            // sessionManager.setPendingDeviceToken(token) // Necesitarías añadir esta función a SessionManager
        }
    }
}
// --- END OF FILE diagnow/core/fcm/MyFirebaseMessagingService.kt ---