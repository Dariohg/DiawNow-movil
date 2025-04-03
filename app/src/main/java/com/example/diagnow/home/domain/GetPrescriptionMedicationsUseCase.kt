package com.example.diagnow.home.domain

import com.example.diagnow.home.data.model.PrescriptionDetailResponse
import com.example.diagnow.home.data.repository.PrescriptionRepository

class GetPrescriptionMedicationsUseCase(
    private val prescriptionRepository: PrescriptionRepository
) {
    suspend operator fun invoke(prescriptionId: String): Result<PrescriptionDetailResponse> {
        return prescriptionRepository.getPrescriptionMedications(prescriptionId)
    }
}