package com.masterofpuppets.parkspotter.domain.service

import com.masterofpuppets.parkspotter.domain.model.VehicleProfile

/**
 * Service to handle business logic for vehicles, especially managing the active vehicle.
 */
interface VehicleService {
    /**
     * Creates or updates a vehicle profile.
     * Enforces Rule A (must be valid) and Rule B.1 (if isActive=true, others become false).
     */
    suspend fun saveVehicle(vehicle: VehicleProfile): Result<Unit>

    /**
     * Deletes a vehicle profile.
     * Warning message for deleting an active vehicle should be handled by the UI before calling this.
     */
    suspend fun deleteVehicle(vehicleId: String): Result<Unit>

    /**
     * Sets a specific vehicle as the active one.
     */
    suspend fun setActiveVehicle(vehicleId: String): Result<Unit>

    /**
     * Retrieves all vehicles.
     */
    suspend fun getAllVehicles(): List<VehicleProfile>

    /**
     * Retrieves the currently active vehicle, if any.
     */
    suspend fun getActiveVehicle(): VehicleProfile?
}
