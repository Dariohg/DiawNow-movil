package com.example.diagnow.core.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Obtiene el token FCM actual de forma asíncrona usando coroutines.
 *
 * @return El token FCM como String, o null si hubo un error al obtenerlo.
 */
suspend fun getFcmToken(): String? = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener // Coroutine ya cancelada

        if (task.isSuccessful) {
            val token = task.result
            Log.d("GetFcmToken", "FCM Token obtained: ${token?.take(10)}...")
            continuation.resume(token) // Devuelve el token
        } else {
            Log.w("GetFcmToken", "Fetching FCM registration token failed", task.exception)
            // Decidimos devolver null en caso de error para no bloquear el flujo principal
            continuation.resume(null)
        }
    }
}