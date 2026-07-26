package com.masterofpuppets.parkspotter.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.masterofpuppets.parkspotter.domain.model.DiscretionLevel
import com.masterofpuppets.parkspotter.domain.model.VehicleCategory

@Entity(tableName = "vehicles")
data class VehicleProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "make")
    val make: String?,
    
    @ColumnInfo(name = "model")
    val model: String?,
    
    @ColumnInfo(name = "color")
    val color: String?,
    
    @ColumnInfo(name = "registration")
    val registration: String?,
    
    @ColumnInfo(name = "category")
    val category: VehicleCategory,
    
    @ColumnInfo(name = "length_meters")
    val lengthMeters: Float?,
    
    @ColumnInfo(name = "height_meters")
    val heightMeters: Float?,
    
    @ColumnInfo(name = "discretion_level")
    val discretionLevel: DiscretionLevel,
    
    @ColumnInfo(name = "is_branded")
    val isBranded: Boolean,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean
)
