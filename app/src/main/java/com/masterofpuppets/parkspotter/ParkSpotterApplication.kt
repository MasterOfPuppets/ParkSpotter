package com.masterofpuppets.parkspotter

import android.app.Application
import com.masterofpuppets.parkspotter.data.local.ParkSpotterDatabase
import com.masterofpuppets.parkspotter.data.repository.RoomVehicleRepository
import com.masterofpuppets.parkspotter.domain.repository.VehicleRepository
import com.masterofpuppets.parkspotter.domain.service.VehicleService
import com.masterofpuppets.parkspotter.domain.service.VehicleServiceImpl

class ParkSpotterApplication : Application() {
    
    // Lazy initialization of database
    val database: ParkSpotterDatabase by lazy {
        ParkSpotterDatabase.getDatabase(this)
    }

    // Lazy initialization of repository
    val vehicleRepository: VehicleRepository by lazy {
        RoomVehicleRepository(database, database.vehicleDao())
    }

    // Lazy initialization of service
    val vehicleService: VehicleService by lazy {
        VehicleServiceImpl(vehicleRepository)
    }

    override fun onCreate() {
        super.onCreate()
        // Other initialization if needed
    }
}
