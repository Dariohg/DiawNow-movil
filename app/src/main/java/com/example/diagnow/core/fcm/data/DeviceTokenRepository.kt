package com.example.diagnow.core.fcm.data

import android.util.Log
import com.example.diagnow.core.network.DeviceTokenRequest
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager

class DeviceTokenRepository(
    private val retrofitHelper: RetrofitHelper,
    private val sessionManager: SessionManager
) {
    private val TAG = "DeviceTokenRepository"

    suspend fun registerToken(token: String): Result<Unit> {
        if (!sessionManager.isLoggedIn()) {
            Log.w(TAG, "Cannot register FCM token, user is not logged in.")
            return Result.failure(Exception("Usuario no autenticado para registrar token"))
        }

        return try {
            val request = DeviceTokenRequest(token = token)
            val response = retrofitHelper.deviceTokenService.registerToken(request)

            if (response.isSuccessful) {
                Log.i(TAG, "Device token registered successfully on server.")
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Error registrando token FCM en el servidor"
                Log.e(TAG, "Failed to register FCM token: ${response.code()} - $errorMsg")
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network or other exception registering FCM token", e)
            Result.failure(e)
        }
    }

    suspend fun deleteToken(token: String): Result<Unit> {
        if (!sessionManager.isLoggedIn()) {
            Log.w(TAG, "Cannot delete FCM token, user is not logged in.")
            return Result.failure(Exception("Usuario no autenticado para eliminar token"))
        }
        return try {
            val response = retrofitHelper.deviceTokenService.deleteToken(token)
            if (response.isSuccessful) {
                Log.i(TAG, "Device token deleted successfully on server.")
                Result.success(Unit)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Error eliminando token FCM del servidor"
                Log.e(TAG, "Failed to delete FCM token: ${response.code()} - $errorMsg")
                Result.failure(Exception("Error ${response.code()}: $errorMsg"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network or other exception deleting FCM token", e)
            Result.failure(e)
        }
    }
}