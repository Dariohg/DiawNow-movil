package com.example.diagnow.register.data.repository

import com.example.diagnow.core.model.User
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.register.data.model.RegisterRequest

class RegisterRepository(
    private val retrofitHelper: RetrofitHelper,
    private val sessionManager: SessionManager
) {
    suspend fun register(
        name: String,
        lastName: String,
        email: String,
        password: String,
        age: Int,
        height: Double? = null,
        weight: Double? = null,
        deviceToken: String? = null
    ): Result<String> {
        return try {
            val request = RegisterRequest(
                name = name,
                lastName = lastName,
                email = email,
                password = password,
                age = age,
                height = height,
                weight = weight
            )

            val response = retrofitHelper.registerService.register(request)

            if (response.isSuccessful) {
                response.body()?.let { registerResponse ->
                    // Guardar token y datos del usuario
                    Result.success(registerResponse.status)
                } ?: Result.failure(Exception("Respuesta vacía del servidor"))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Error desconocido"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}