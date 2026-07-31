package com.masterofpuppets.parkspotter.domain.model

import java.util.UUID

data class PlaceResult(
    val osmType: String,
    val osmId: Long,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val placeType: String,
    val tags: Map<String, String>,
    val distanceMeters: Int,
    val score: Float? = null,
    val isSanitized: Boolean = false,
    val sanitizationReason: String? = null,
    val contextMatches: Set<com.masterofpuppets.parkspotter.ui.search.SearchContext> = emptySet(),
)

data class SearchSession(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val originLat: Double,
    val originLon: Double,
    val radiusMeters: Int,
    val vehicleProfileId: String? = null,
    val status: SearchSessionStatus = SearchSessionStatus.ACTIVE,
    val resultIds: List<Pair<String, Long>> = emptyList()
)

enum class SearchSessionStatus {
    ACTIVE, CLOSED
}

data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val tier: UserTier = UserTier.FREE,
    val createdAt: Long = System.currentTimeMillis(),
    val vehicleProfiles: List<VehicleProfile> = emptyList()
)

enum class UserTier {
    FREE, PAID, PREMIUM
}

data class VehicleProfile(
    val id: String = UUID.randomUUID().toString(),
    val make: String? = null,
    val model: String? = null,
    val color: String? = null,
    val registration: String? = null,
    val category: VehicleCategory,
    val lengthMeters: Float? = null,
    val heightMeters: Float? = null,
    val discretionLevel: DiscretionLevel,
    val isBranded: Boolean = false,
    val isActive: Boolean = false
) {
    fun isValid(): Boolean {
        return !make.isNullOrBlank() || 
               !model.isNullOrBlank() || 
               !color.isNullOrBlank() || 
               !registration.isNullOrBlank()
    }
}

enum class VehicleCategory {
    CITY_CAR,
    SEDAN,
    STATION_WAGON,
    SUV,
    MINIVAN,
    VAN,
    PICKUP_TRUCK,
    CAMPERVAN,
    MOTORHOME,
    CARAVAN
}

enum class DiscretionLevel {
    LOW, MEDIUM, HIGH
}

data class UserPreferences(
    val defaultRadiusMeters: Int = 2000,
    val legalNoticeLastShownAt: Long = 0L,
    val resultsPageSize: Int = 10,
    val warnIfResultsAbove: Int = 150,
)

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val osmType: String,
    val osmId: Long,
    val actionType: HistoryActionType,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null
)

enum class HistoryActionType {
    VIEWED, NAVIGATED, VISIT_CONFIRMED, OVERNIGHT_CONFIRMED
}
