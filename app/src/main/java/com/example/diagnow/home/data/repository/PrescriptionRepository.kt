package com.example.diagnow.home.data.repository

import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.model.PrescriptionResponse

class PrescriptionRepository(
    private val retrofitHelper: RetrofitHelper,
    private val sessionManager: SessionManager
) {
    suspend fun getUserPrescriptions(): Result<List<PrescriptionResponse>> {
        return try {
            val token = sessionManager.getToken() ?: return Result.failure(Exception("No hay sesión activa"))
            val authHeader = "Bearer $token"

            val response = retrofitHelper.prescriptionService.getUserPrescriptions(authHeader)

            if (response.isSuccessful) {
                response.body()?.let { prescriptions ->
                    Result.success(prescriptions)
                } ?: Result.failure(Exception("Respuesta vacía del servidor"))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Error desconocido"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPrescriptionById(prescriptionId: String): Result<PrescriptionResponse> {
        return try {
            val token = sessionManager.getToken() ?: return Result.failure(Exception("No hay sesión activa"))
            val authHeader = "Bearer $token"

            val response = retrofitHelper.prescriptionService.getPrescriptionById(authHeader, prescriptionId)

            if (response.isSuccessful) {
                response.body()?.let { prescription ->
                    Result.success(prescription)
                } ?: Result.failure(Exception("Respuesta vacía del servidor"))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Error desconocido"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}