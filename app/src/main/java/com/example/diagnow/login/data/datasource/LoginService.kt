package com.example.diagnow.login.data.datasource

import com.example.diagnow.login.data.model.LoginRequest
import com.example.diagnow.login.data.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LoginService {
    @POST("/patients/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}