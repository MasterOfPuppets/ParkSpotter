package com.masterofpuppets.parkspotter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.masterofpuppets.parkspotter.data.local.dao.VehicleDao
import com.masterofpuppets.parkspotter.data.local.entity.VehicleProfileEntity
import com.masterofpuppets.parkspotter.domain.model.DiscretionLevel
import com.masterofpuppets.parkspotter.domain.model.VehicleCategory

class Converters {
    @TypeConverter
    fun fromVehicleCategory(value: VehicleCategory): String = value.name

    @TypeConverter
    fun toVehicleCategory(value: String): VehicleCategory = try {
        VehicleCategory.valueOf(value)
    } catch (e: IllegalArgumentException) {
        VehicleCategory.CITY_CAR // Fallback safe value
    }

    @TypeConverter
    fun fromDiscretionLevel(value: DiscretionLevel): String = value.name

    @TypeConverter
    fun toDiscretionLevel(value: String): DiscretionLevel = try {
        DiscretionLevel.valueOf(value)
    } catch (e: IllegalArgumentException) {
        DiscretionLevel.MEDIUM // Fallback safe value
    }
}

@Database(
    entities = [VehicleProfileEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ParkSpotterDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    companion object {
        @Volatile
        private var INSTANCE: ParkSpotterDatabase? = null

        fun getDatabase(context: Context): ParkSpotterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ParkSpotterDatabase::class.java,
                    "parkspotter_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
