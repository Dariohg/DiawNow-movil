// --- START OF (MODIFIED) diagnow/core/network/RetrofitHelper.kt ---
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
        // --- ASEGÚRATE QUE ESTA ES TU BASE URL CORRECTA ---
        // Si tus endpoints empiezan con /api, inclúyelo aquí.
        private const val BASE_URL = "https://diagnow-api.onrender.com"
        private const val TIMEOUT = 30L
    }

    // Interceptor para agregar el token de autenticación (¡Este es CLAVE!)
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        // Obtiene el token guardado por SessionManager DESPUÉS del login
        val token = sessionManager.getToken()

        val newRequest = if (token != null && !originalRequest.url.encodedPath.contains("login") && !originalRequest.url.encodedPath.contains("register")) {
            // Añade el header solo si hay token y NO es para login/register
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest // No añade header para login/register o si no hay token
        }

        chain.proceed(newRequest)
    }

    // Interceptor para logging
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Cambia a NONE para producción
    }

    // Cliente OkHttp con configuración
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor) // Se aplica a TODAS las llamadas
        .addInterceptor(loggingInterceptor)
        .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
        .build()

    // Instancia de Retrofit
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Servicios de API
    val loginService: LoginService by lazy {
        retrofit.create(LoginService::class.java)
    }

    val registerService: RegisterService by lazy {
        retrofit.create(RegisterService::class.java)
    }

    val prescriptionService: PrescriptionService by lazy {
        retrofit.create(PrescriptionService::class.java)
    }

    // --- NUEVO SERVICIO ---
    val deviceTokenService: DeviceTokenService by lazy {
        retrofit.create(DeviceTokenService::class.java)
    }
    // --- FIN NUEVO SERVICIO ---
}