package com.example.diagnow.register.data.model

import com.example.diagnow.core.model.User
import com.google.gson.annotations.SerializedName

data class RegisterResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("token")
    val token: String,

    @SerializedName("user")
    val user: User
)