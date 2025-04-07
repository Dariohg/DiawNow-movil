package com.example.diagnow.core.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


suspend fun getFcmToken(): String? = suspendCancellableCoroutine { continuation ->
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener

        if (task.isSuccessful) {
            val token = task.result
            Log.d("GetFcmToken", "FCM Token obtained: ${token?.take(10)}...")
            continuation.resume(token)
        } else {
            Log.w("GetFcmToken", "Fetching FCM registration token failed", task.exception)
            continuation.resume(null)
        }
    }
}