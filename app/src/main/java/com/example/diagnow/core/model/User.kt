package com.example.diagnow.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val id: String,
    val name: String,
    val lastName: String,
    val email: String,
    val age: Int,
    val height: Double? = null,
    val weight: Double? = null,
    val deviceToken: String? = null,
    val profilePicture: String? = null
) : Parcelable