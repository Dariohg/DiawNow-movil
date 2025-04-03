package com.example.diagnow.home.data.repository

import android.util.Log
import com.example.diagnow.core.network.RetrofitHelper
import com.example.diagnow.core.session.SessionManager
import com.example.diagnow.home.data.model.PrescriptionDetailResponse
import com.example.diagnow.home.data.model.PrescriptionResponse

class PrescriptionRepository(
    private val retrofitHelper: RetrofitHelper,
    private val sessionManager: SessionManager
) {

    suspend fun getUserPrescriptions(): Result<List<PrescriptionResponse>> {
        return try {

            val token = sessionManager.getToken() ?: return Result.failure(Exception("No hay token de autenticación"))
            val authHeader = "Bearer $token"

            val user = sessionManager.getUser() ?: return Result.failure(Exception("No hay usuario en sesión"))

            Log.d("PrescriptionRepo", "Obteniendo recetas para el usuario ID: ${user.id}")

            val response = retrofitHelper.prescriptionService.getPatientPrescriptions(authHeader, user.id)

            Log.d("PrescriptionRepo", "Respuesta del servidor: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val prescriptions = responseBody.data.prescriptions
                    Log.d("PrescriptionRepo", "Recetas obtenidas: ${prescriptions.size}")
                    Result.success(prescriptions)
                } else {
                    Log.e("PrescriptionRepo", "Respuesta vacía del servidor")
                    Result.failure(Exception("Respuesta vacía del servidor"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Log.e("PrescriptionRepo", "Error del servidor: ${response.code()}, $errorBody")
                Result.failure(Exception("Error ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("PrescriptionRepo", "Excepción al obtener recetas", e)
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
    suspend fun getPrescriptionMedications(prescriptionId: String): Result<PrescriptionDetailResponse> {
        return try {
            val token = sessionManager.getToken() ?: return Result.failure(Exception("No hay token de autenticación"))
            val authHeader = "Bearer $token"

            Log.d("PrescriptionRepo", "Obteniendo medicamentos para la receta ID: $prescriptionId")

            val response = retrofitHelper.prescriptionService.getPrescriptionMedications(authHeader, prescriptionId)

            Log.d("PrescriptionRepo", "Respuesta del servidor: ${response.code()}")

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    Log.d("PrescriptionRepo", "Medicamentos obtenidos: ${responseBody.data.medications.size}")
                    Result.success(responseBody)
                } else {
                    Log.e("PrescriptionRepo", "Respuesta vacía del servidor")
                    Result.failure(Exception("Respuesta vacía del servidor"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Log.e("PrescriptionRepo", "Error del servidor: ${response.code()}, $errorBody")
                Result.failure(Exception("Error ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e("PrescriptionRepo", "Excepción al obtener medicamentos", e)
            Result.failure(e)
        }
    }
}