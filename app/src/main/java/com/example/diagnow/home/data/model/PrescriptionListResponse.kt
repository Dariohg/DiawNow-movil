package com.example.diagnow.home.data.model

import com.example.diagnow.home.data.model.PrescriptionResponse
import com.google.gson.annotations.SerializedName

data class PrescriptionListResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: PrescriptionData
)

data class PrescriptionData(
    @SerializedName("prescriptions")
    val prescriptions: List<PrescriptionResponse>
)