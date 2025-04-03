package com.example.diagnow.home.data.model

import com.google.gson.annotations.SerializedName
import java.util.Date

data class PrescriptionDetailResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: PrescriptionDetailData
)

data class PrescriptionDetailData(
    @SerializedName("prescription_created_at")
    val prescriptionCreatedAt: Date?,

    @SerializedName("medications")
    val medications: List<MedicationDetailResponse>
)

data class MedicationDetailResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("prescriptionId")
    val prescriptionId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("dosage")
    val dosage: String,

    @SerializedName("frequency")
    val frequency: Int,

    @SerializedName("days")
    val days: Int,

    @SerializedName("administrationRoute")
    val administrationRoute: String? = null,

    @SerializedName("instructions")
    val instructions: String? = null,

    @SerializedName("createdAt")
    val createdAt: Date? = null
)