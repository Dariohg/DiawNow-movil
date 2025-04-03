package com.example.diagnow.login.data.repository

import android.util.Log
import com.example.diagnow.core.model.User
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.login.data.model.LoginRequest

class LoginRepository(
    private val retrofitHelper: RetrofitHelper,
    private val sessionManager: SessionManager
) {
    private val TAG = "LoginRepository"

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val request = LoginRequest(email, password)
            val response = retrofitHelper.loginService.login(request)

            if (response.isSuccessful) {
                response.body()?.let { loginResponse ->
                    // Guardar token
                    sessionManager.setToken(loginResponse.token)

                    // Extraer ID y datos del usuario del token JWT
                    try {
                        val tokenParts = loginResponse.token.split(".")
                        if (tokenParts.size >= 2) {
                            val payload = tokenParts[1]
                            // Asegurar que la longitud del payload sea múltiplo de 4 para decodificación Base64
                            val paddedPayload = payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '=')
                            val decodedBytes = android.util.Base64.decode(paddedPayload, android.util.Base64.URL_SAFE)
                            val decodedPayload = String(decodedBytes)

                            // Usar JSONObject para parsear el payload
                            val jsonPayload = org.json.JSONObject(decodedPayload)
                            val userId = jsonPayload.optString("id")

                            if (userId.isNotEmpty()) {
                                // Crear un objeto User con el ID del token
                                val user = User(
                                    id = userId,
                                    name = "Usuario", // Podríamos obtener más datos del token o de una llamada API adicional
                                    lastName = "Paciente",
                                    email = email,
                                    age = 0
                                )

                                // Guardar el usuario en la sesión
                                sessionManager.setUser(user)
                                Log.d(TAG, "Usuario guardado en sesión con ID: $userId y email: $email")
                            } else {
                                Log.e(TAG, "No se pudo extraer el ID del usuario del token")
                            }
                        } else {
                            Log.e(TAG, "Formato de token inválido: ${loginResponse.token.take(10)}...")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al decodificar el token: ${e.message}")

                        // Plan B: Si no podemos extraer del token, crear un usuario con datos mínimos
                        val userId = "user_${System.currentTimeMillis()}" // ID temporal
                        val fallbackUser = User(
                            id = userId,
                            name = "Usuario",
                            lastName = "Temporal",
                            email = email,
                            age = 0
                        )
                        sessionManager.setUser(fallbackUser)
                        Log.d(TAG, "Creado usuario fallback con ID: $userId")
                    }

                    Result.success(loginResponse.status)
                } ?: Result.failure(Exception("Respuesta vacía del servidor"))
            } else {
                Log.e(TAG, "Error en login: ${response.code()} - ${response.errorBody()?.string()}")
                Result.failure(Exception(response.errorBody()?.string() ?: "Error desconocido"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en login: ${e.message}")
            Result.failure(e)
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }

    fun isLoggedIn(): Boolean {
        return sessionManager.isLoggedIn()
    }

    fun getCurrentUser(): User? {
        return sessionManager.getUser()
    }
}