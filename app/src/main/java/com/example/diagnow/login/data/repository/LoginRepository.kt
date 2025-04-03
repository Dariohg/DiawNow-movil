package com.example.diagnow.login.data.repository

import com.example.diagnow.core.model.User
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.login.data.model.LoginRequest

class LoginRepository(
    private val retrofitHelper: RetrofitHelper,
    private val sessionManager: SessionManager
) {
    suspend fun login(email: String, password: String, deviceToken: String? = null): Result<User> {
        return try {
            val request = LoginRequest(email, password, deviceToken)
            val response = retrofitHelper.loginService.login(request)

            if (response.isSuccessful) {
                response.body()?.let { loginResponse ->
                    // Guardar token y datos del usuario
                    sessionManager.setToken(loginResponse.token)
                    sessionManager.setUser(loginResponse.user)
                    Result.success(loginResponse.user)
                } ?: Result.failure(Exception("Respuesta vacía del servidor"))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Error desconocido"))
            }
        } catch (e: Exception) {
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