package com.example.diagnow.core.fcm.domain

import com.example.diagnow.core.fcm.data.DeviceTokenRepository

class RegisterDeviceTokenUseCase(
    private val repository: DeviceTokenRepository
) {
    suspend operator fun invoke(token: String): Result<Unit> {
        if (token.isBlank()) {
            return Result.failure(Exception("No se puede registrar un token FCM vacío"))
        }
        // Simplemente delega al repositorio
        return repository.registerToken(token)
    }
}