package com.example.diagnow.register.domain

import com.example.diagnow.core.model.User
import com.example.diagnow.register.data.repository.RegisterRepository

class RegisterUseCase(private val registerRepository: RegisterRepository) {

    suspend operator fun invoke(
        name: String,
        lastName: String,
        email: String,
        password: String,
        confirmPassword: String,
        age: Int,
        height: Double? = null,
        weight: Double? = null
    ): Result<String> {
        // Validaciones
        if (name.isBlank()) {
            return Result.failure(Exception("El nombre es obligatorio"))
        }

        if (lastName.isBlank()) {
            return Result.failure(Exception("El apellido es obligatorio"))
        }

        if (email.isBlank()) {
            return Result.failure(Exception("El correo electrónico es obligatorio"))
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return Result.failure(Exception("El correo electrónico no es válido"))
        }

        if (password.isBlank()) {
            return Result.failure(Exception("La contraseña es obligatoria"))
        }

        if (password.length < 6) {
            return Result.failure(Exception("La contraseña debe tener al menos 6 caracteres"))
        }

        if (password != confirmPassword) {
            return Result.failure(Exception("Las contraseñas no coinciden"))
        }

        if (age <= 0) {
            return Result.failure(Exception("La edad debe ser mayor a 0"))
        }

        return registerRepository.register(
            name = name,
            lastName = lastName,
            email = email,
            password = password,
            age = age,
            height = height,
            weight = weight
        )
    }
}