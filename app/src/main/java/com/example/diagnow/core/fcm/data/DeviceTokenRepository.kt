package com.example.diagnow.core.fcm.data

import android.util.Log
import com.example.diagnow.core.network.DeviceTokenRequest
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager // Importante para saber si hay sesión

class DeviceTokenRepository(
    private val retrofitHelper: RetrofitHelper,
    private val sessionManager: SessionManager
) {
    private val TAG = "DeviceTokenRepository"

    /**
     * Intenta registrar el token FCM en el backend.
     * Requiere que el usuario esté logueado (el token JWT se añade vía Interceptor).
     * @param token El token FCM a registrar.
     * @return Result.success(Unit) si el registro fue exitoso (HTTP 2xx), Result.failure en caso contrario.
     */
    suspend fun registerToken(token: String): Result<Unit> {
        // Verificación PREVIA (opcional pero buena práctica): ¿Está el usuario logueado?
        if (!sessionManager.isLoggedIn()) {
            Log.w(TAG, "Cannot register FCM token, user is not logged in.")
            // Podrías guardar el token en prefs para intentarlo más tarde,
            // pero por ahora, fallamos la operación si no hay sesión activa.
            return Result.failure(Exception("Usuario no autenticado para registrar token"))
        }

        return try {
            val request = DeviceTokenRequest(token = token)
            // La llamada usa el OkHttpClient que tiene el authInterceptor.
            // Si sessionManager.getToken() devuelve un token JWT, se añadirá el header.
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

    /**
     * Intenta eliminar el token FCM del backend.
     * Requiere que el usuario esté logueado.
     * @param token El token FCM a eliminar.
     * @return Result.success(Unit) si fue exitoso (HTTP 2xx), Result.failure en caso contrario.
     */
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