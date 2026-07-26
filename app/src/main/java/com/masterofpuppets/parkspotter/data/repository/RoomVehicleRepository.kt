package com.masterofpuppets.parkspotter.data.repository

import androidx.room.withTransaction
import com.masterofpuppets.parkspotter.data.local.ParkSpotterDatabase
import com.masterofpuppets.parkspotter.data.local.dao.VehicleDao
import com.masterofpuppets.parkspotter.data.local.entity.VehicleProfileEntity
import com.masterofpuppets.parkspotter.domain.model.VehicleProfile
import com.masterofpuppets.parkspotter.domain.repository.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomVehicleRepository(
    private val database: ParkSpotterDatabase,
    private val dao: VehicleDao
) : VehicleRepository {

    override suspend fun save(vehicle: VehicleProfile) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val entity = vehicle.toEntity()
                dao.insertVehicle(entity)
                if (entity.isActive) {
                    dao.deactivateOtherVehicles(entity.id)
                }
            }
        }
    }

    override suspend fun delete(vehicleId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteVehicle(vehicleId)
        }
    }

    override suspend fun getById(vehicleId: String): VehicleProfile? {
        return withContext(Dispatchers.IO) {
            dao.getVehicleById(vehicleId)?.toDomain()
        }
    }

    override suspend fun getAll(): List<VehicleProfile> {
        return withContext(Dispatchers.IO) {
            dao.getAllVehicles().map { it.toDomain() }
        }
    }

    private fun VehicleProfile.toEntity(): VehicleProfileEntity {
        return VehicleProfileEntity(
            id = this.id,
            make = this.make,
            model = this.model,
            color = this.color,
            registration = this.registration,
            category = this.category,
            lengthMeters = this.lengthMeters,
            heightMeters = this.heightMeters,
            discretionLevel = this.discretionLevel,
            isBranded = this.isBranded,
            isActive = this.isActive
        )
    }

    private fun VehicleProfileEntity.toDomain(): VehicleProfile {
        return VehicleProfile(
            id = this.id,
            make = this.make,
            model = this.model,
            color = this.color,
            registration = this.registration,
            category = this.category,
            lengthMeters = this.lengthMeters,
            heightMeters = this.heightMeters,
            discretionLevel = this.discretionLevel,
            isBranded = this.isBranded,
            isActive = this.isActive
        )
    }
}
