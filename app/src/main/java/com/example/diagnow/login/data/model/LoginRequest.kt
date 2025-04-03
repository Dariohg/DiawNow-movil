package com.example.diagnow.login.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("deviceToken")
    val deviceToken: String? = null
)