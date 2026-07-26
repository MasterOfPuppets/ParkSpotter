package com.masterofpuppets.parkspotter.domain.service

import com.masterofpuppets.parkspotter.domain.model.VehicleProfile
import com.masterofpuppets.parkspotter.domain.repository.VehicleRepository

class VehicleServiceImpl(
    private val repository: VehicleRepository
) : VehicleService {

    override suspend fun saveVehicle(vehicle: VehicleProfile): Result<Unit> {
        if (!vehicle.isValid()) {
            return Result.failure(IllegalArgumentException("Vehicle must have at least one identifying field (make, model, color, or registration)"))
        }

        // Room's DAO uses a @Transaction to automatically deactivate other vehicles
        // if this one is passed as active. So we just pass it to the repository.
        repository.save(vehicle)

        return Result.success(Unit)
    }

    override suspend fun deleteVehicle(vehicleId: String): Result<Unit> {
        // UI should show the warning before calling this.
        repository.delete(vehicleId)
        return Result.success(Unit)
    }

    override suspend fun setActiveVehicle(vehicleId: String): Result<Unit> {
        val vehicleToActivate = repository.getById(vehicleId)
            ?: return Result.failure(NoSuchElementException("Vehicle not found"))

        return saveVehicle(vehicleToActivate.copy(isActive = true))
    }

    override suspend fun getAllVehicles(): List<VehicleProfile> {
        return repository.getAll()
    }

    override suspend fun getActiveVehicle(): VehicleProfile? {
        return repository.getAll().find { it.isActive }
    }
}
