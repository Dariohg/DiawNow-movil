package com.example.diagnow.home.domain

import com.example.diagnow.home.data.model.PrescriptionResponse
import com.example.diagnow.home.data.repository.PrescriptionRepository

class GetPrescriptionsUseCase(private val prescriptionRepository: PrescriptionRepository) {

    suspend operator fun invoke(): Result<List<PrescriptionResponse>> {
        return prescriptionRepository.getUserPrescriptions()
    }
}