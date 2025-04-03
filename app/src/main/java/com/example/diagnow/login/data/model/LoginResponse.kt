package com.example.diagnow.login.data.model

import com.example.diagnow.core.model.User
import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("token")
    val token: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("user")
    val user: UserResponse? = null
)

data class UserResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("last_name")
    val lastName: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("age")
    val age: Int
)