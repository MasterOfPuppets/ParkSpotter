package com.masterofpuppets.parkspotter.domain.repository

import com.masterofpuppets.parkspotter.domain.model.VehicleProfile

interface VehicleRepository {
    suspend fun save(vehicle: VehicleProfile)
    suspend fun delete(vehicleId: String)
    suspend fun getById(vehicleId: String): VehicleProfile?
    suspend fun getAll(): List<VehicleProfile>
}
