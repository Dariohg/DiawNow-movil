package com.example.diagnow.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE // Aunque no lo usemos ahora, lo dejamos por completitud
import retrofit2.http.POST
import retrofit2.http.Path

// Modelo para la solicitud POST
data class DeviceTokenRequest(val token: String, val deviceType: String = "android")

interface DeviceTokenService {
    /**
     * Registra el token del dispositivo en el backend.
     * La autenticación (Bearer token) debe ser añadida por un Interceptor.
     * Asume que BASE_URL termina en /api/
     */
    @POST("device-tokens")
    suspend fun registerToken(@Body request: DeviceTokenRequest): Response<Unit> // Backend no devuelve body, solo status 200/201

    /**
     * Elimina un token del backend (ej. al hacer logout).
     * La autenticación (Bearer token) debe ser añadida por un Interceptor.
     * Asume que BASE_URL termina en /api/
     */
    @DELETE("device-tokens/{token}")
    suspend fun deleteToken(@Path("token") token: String): Response<Unit>
}