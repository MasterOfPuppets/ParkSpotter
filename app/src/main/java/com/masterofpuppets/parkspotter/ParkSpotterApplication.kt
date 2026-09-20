package com.masterofpuppets.parkspotter

import android.app.Application
import com.masterofpuppets.parkspotter.data.local.ParkSpotterDatabase
import com.masterofpuppets.parkspotter.data.repository.RoomVehicleRepository
import com.masterofpuppets.parkspotter.domain.repository.VehicleRepository
import com.masterofpuppets.parkspotter.domain.service.SearchService
import com.masterofpuppets.parkspotter.domain.service.SearchServiceImpl
import com.masterofpuppets.parkspotter.domain.service.VehicleService
import com.masterofpuppets.parkspotter.domain.service.VehicleServiceImpl
import com.masterofpuppets.parkspotter.domain.service.zone.ZoneClassificationService
import com.masterofpuppets.parkspotter.domain.service.zone.ZoneClassificationServiceImpl

class ParkSpotterApplication : Application() {
    
    // Lazy initialization of database
    val database: ParkSpotterDatabase by lazy {
        ParkSpotterDatabase.getDatabase(this)
    }

    // Lazy initialization of repository
    val vehicleRepository: VehicleRepository by lazy {
        RoomVehicleRepository(database, database.vehicleDao())
    }

    // Lazy initialization of vehicle service
    val vehicleService: VehicleService by lazy {
        VehicleServiceImpl(vehicleRepository)
    }

    // Lazy initialization of search service
    val searchService: SearchService by lazy {
        SearchServiceImpl()
    }

    val zoneClassificationService: ZoneClassificationService by lazy {
        ZoneClassificationServiceImpl()
    }

    override fun onCreate() {
        super.onCreate()
        // Other initialization if needed
    }
}
