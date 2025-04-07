package com.example.diagnow.core.network

import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.datasource.PrescriptionService
import com.example.diagnow.login.data.datasource.LoginService
import com.example.diagnow.register.data.datasource.RegisterService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitHelper(private val sessionManager: SessionManager) {
    companion object {
        private const val BASE_URL = "http://54.172.229.231:8080"
        private const val TIMEOUT = 30L
    }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = sessionManager.getToken()

        val newRequest = if (token != null && !originalRequest.url.encodedPath.contains("login") && !originalRequest.url.encodedPath.contains("register")) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        chain.proceed(newRequest)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val loginService: LoginService by lazy {
        retrofit.create(LoginService::class.java)
    }

    val registerService: RegisterService by lazy {
        retrofit.create(RegisterService::class.java)
    }

    val prescriptionService: PrescriptionService by lazy {
        retrofit.create(PrescriptionService::class.java)
    }

    val deviceTokenService: DeviceTokenService by lazy {
        retrofit.create(DeviceTokenService::class.java)
    }
}