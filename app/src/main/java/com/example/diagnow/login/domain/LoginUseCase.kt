package com.example.diagnow.login.domain

import com.example.diagnow.core.model.User
import com.example.diagnow.login.data.repository.LoginRepository

class LoginUseCase(private val loginRepository: LoginRepository) {

    suspend operator fun invoke(email: String, password: String, deviceToken: String? = null): Result<String> {
        // Validaciones básicas
        if (email.isBlank()) {
            return Result.failure(Exception("El correo electrónico es obligatorio"))
        }

        if (password.isBlank()) {
            return Result.failure(Exception("La contraseña es obligatoria"))
        }

        return loginRepository.login(email, password)
    }
}