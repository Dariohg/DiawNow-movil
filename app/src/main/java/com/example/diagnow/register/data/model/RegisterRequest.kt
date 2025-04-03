package com.example.diagnow.register.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("lastName")
    val lastName: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("age")
    val age: Int,

    @SerializedName("height")
    val height: Double? = null,

    @SerializedName("weight")
    val weight: Double? = null,

    @SerializedName("deviceToken")
    val deviceToken: String? = null
)