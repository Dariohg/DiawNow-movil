package com.example.diagnow.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

data class DeviceTokenRequest(val token: String, val deviceType: String = "android")

interface DeviceTokenService {
    @POST("device-tokens")
    suspend fun registerToken(@Body request: DeviceTokenRequest): Response<Unit>

    @DELETE("device-tokens/{token}")
    suspend fun deleteToken(@Path("token") token: String): Response<Unit>
}