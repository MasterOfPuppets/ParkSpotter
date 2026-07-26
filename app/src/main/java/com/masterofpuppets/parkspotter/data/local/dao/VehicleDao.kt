package com.masterofpuppets.parkspotter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.masterofpuppets.parkspotter.data.local.entity.VehicleProfileEntity

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles")
    fun getAllVehicles(): List<VehicleProfileEntity>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    fun getVehicleById(vehicleId: String): VehicleProfileEntity?

    @Query("SELECT * FROM vehicles WHERE is_active = 1 LIMIT 1")
    fun getActiveVehicle(): VehicleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertVehicle(vehicle: VehicleProfileEntity)

    @Update
    fun updateVehicle(vehicle: VehicleProfileEntity)

    @Query("DELETE FROM vehicles WHERE id = :vehicleId")
    fun deleteVehicle(vehicleId: String)

    @Query("UPDATE vehicles SET is_active = 0 WHERE id != :excludeId")
    fun deactivateOtherVehicles(excludeId: String)
}
