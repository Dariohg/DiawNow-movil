package com.example.diagnow.core.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log // Importar Log
import com.example.diagnow.core.model.User
import com.google.gson.Gson
import java.util.concurrent.TimeUnit // Importar TimeUnit

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val TAG = "SessionManager" // Tag para logs

    companion object {
        private const val PREFS_NAME = "diagnow_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER = "user_data"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp" // Clave para timestamp
    }

    fun setToken(token: String) {
        val timestamp = System.currentTimeMillis() // Hora actual
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_LOGIN_TIMESTAMP, timestamp) // Guardar timestamp
            .apply()
        Log.d(TAG, "Token and login timestamp ($timestamp) saved.")
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun setUser(user: User) {
        val userJson = gson.toJson(user)
        prefs.edit().putString(KEY_USER, userJson).apply()
        Log.d(TAG, "User data saved for email: ${user.email}")
    }

    fun getUser(): User? {
        val userJson = prefs.getString(KEY_USER, null) ?: return null
        return try {
            gson.fromJson(userJson, User::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user JSON", e)
            null
        }
    }

    fun setDeviceToken(token: String) {
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    fun getDeviceToken(): String? {
        return prefs.getString(KEY_DEVICE_TOKEN, null)
    }

    /**
     * Obtiene el timestamp (en milisegundos) de cuando se guardó el token.
     * @return El timestamp o 0L si no se encuentra.
     */
    fun getLoginTimestamp(): Long {
        return prefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)
    }

    /**
     * Borra toda la información de sesión guardada (token, usuario, timestamp).
     */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER)
            // .remove(KEY_DEVICE_TOKEN) // DECIDIR: ¿Borrar el token FCM al cerrar sesión? Podría ser útil mantenerlo para re-registrar rápido en el próximo login. Por ahora NO lo borramos.
            .remove(KEY_LOGIN_TIMESTAMP) // Borrar el timestamp
            .apply()
        Log.i(TAG, "Session data cleared.")
    }

    /**
     * Verifica si hay un token de autenticación guardado.
     * NO verifica si el token es válido en el servidor ni si ha expirado localmente.
     * @return true si existe un token, false si no.
     */
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    /**
     * Verifica si la sesión ha expirado según la duración máxima definida en el cliente.
     * @param maxDurationHours Duración máxima de la sesión en horas. Por defecto 12.
     * @return true si la sesión ha expirado o no existe, false en caso contrario.
     */
    fun isSessionExpired(maxDurationHours: Int = 12): Boolean {
        val loginTime = getLoginTimestamp()
        if (!isLoggedIn() || loginTime == 0L) {
            // Si no está logueado (no hay token) O no hay timestamp, se considera expirada/inválida.
            Log.d(TAG, "isSessionExpired: No active session or timestamp found.")
            return true
        }
        val currentTime = System.currentTimeMillis()
        val durationMillis = TimeUnit.HOURS.toMillis(maxDurationHours.toLong())
        val elapsedTime = currentTime - loginTime

        val expired = elapsedTime > durationMillis
        Log.d(TAG, "isSessionExpired: Check - CurrentTime=$currentTime, LoginTime=$loginTime, Elapsed=${elapsedTime}ms, MaxDuration=${durationMillis}ms, Expired=$expired")
        return expired
    }
}