package com.example.diagnow.home.data.model

import com.google.gson.annotations.SerializedName
import java.util.Date

data class PrescriptionResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("userId")
    val userId: String,

    @SerializedName("doctorName")
    val doctorName: String,

    @SerializedName("date")
    val date: Date,

    @SerializedName("diagnosis")
    val diagnosis: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("medications")
    val medications: List<MedicationResponse>,

    @SerializedName("notes")
    val notes: String? = null
)

data class MedicationResponse(
    @SerializedName("id")
    val id: String,

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
    val instructions: String? = null
)