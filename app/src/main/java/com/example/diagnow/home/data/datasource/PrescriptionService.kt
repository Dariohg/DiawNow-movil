package com.example.diagnow.home.data.datasource

import com.example.diagnow.home.data.model.PrescriptionListResponse
import com.example.diagnow.home.data.model.PrescriptionResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface PrescriptionService {
    @GET("prescriptions")
    suspend fun getUserPrescriptions(
        @Header("Authorization") token: String
    ): Response<List<PrescriptionResponse>>

    @GET("prescriptions/{id}")
    suspend fun getPrescriptionById(
        @Header("Authorization") token: String,
        @Path("id") prescriptionId: String
    ): Response<PrescriptionResponse>

    @GET("prescriptions/patient/{patientId}")
    suspend fun getPatientPrescriptions(
        @Header("Authorization") token: String,
        @Path("patientId") patientId: String
    ): Response<PrescriptionListResponse>
}