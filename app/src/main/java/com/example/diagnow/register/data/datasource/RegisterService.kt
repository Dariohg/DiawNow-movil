package com.example.diagnow.register.data.datasource

import com.example.diagnow.register.data.model.RegisterRequest
import com.example.diagnow.register.data.model.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface RegisterService {
    @POST("/patients/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>
}